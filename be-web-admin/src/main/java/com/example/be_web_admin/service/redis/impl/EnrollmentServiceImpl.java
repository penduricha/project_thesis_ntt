package com.example.be_web_admin.service.redis.impl;

import com.example.be_web_admin.enums.AuthEvent;
import com.example.be_web_admin.model.jpa.Admin;
import com.example.be_web_admin.model.jpa.TotpCredential;
import com.example.be_web_admin.model.redis.EnrollSession;
import com.example.be_web_admin.repository.jpa.AdminRepository;
import com.example.be_web_admin.repository.jpa.TotpCredentialRepository;
import com.example.be_web_admin.repository.redis.EnrollSessionRepository;
import com.example.be_web_admin.security.private_key.CryptoService;
import com.example.be_web_admin.service.jpa.impl.AuthAuditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Giai đoạn 2 của cài 2FA (web bấm "Kích hoạt"): đọc khóa chờ ở Redis, ghi MariaDB trong 1 transaction,
 * commit xong mới xóa key Redis và ghi audit. Admin chỉ thực sự bật 2FA sau khi app mobile xác nhận
 * (xem EnrollmentConfirmServiceImpl).
 */
@Slf4j
@Service
public class EnrollmentServiceImpl {

    /** cột secret_cipher là varbinary(64). */
    private static final int MAX_CIPHER_LENGTH = 64;

    private final EnrollSessionRepository enrollSessionRepository;
    private final AdminRepository adminRepository;
    private final TotpCredentialRepository totpCredentialRepository;
    private final AuthAuditService auditService;
    private final EnrollmentConfirmServiceImpl confirmService;
    private final TransactionTemplate transactionTemplate;
    private final TwoFaEventServiceImpl eventService;

    public EnrollmentServiceImpl(EnrollSessionRepository enrollSessionRepository,
                                 AdminRepository adminRepository,
                                 TotpCredentialRepository totpCredentialRepository,
                                 AuthAuditService auditService,
                                 EnrollmentConfirmServiceImpl confirmService,
                                 TransactionTemplate transactionTemplate,
                                 TwoFaEventServiceImpl eventService) {
        this.enrollSessionRepository = enrollSessionRepository;
        this.adminRepository = adminRepository;
        this.totpCredentialRepository = totpCredentialRepository;
        this.auditService = auditService;
        this.confirmService = confirmService;
        this.transactionTemplate = transactionTemplate;
        this.eventService = eventService;
    }

    public record ActivationResult(Long adminId, Long credentialId, String algorithm, int digits,
                                   int periodSeconds, long expiresInSeconds) {
    }

    /** Trạng thái 2FA cho trang thông tin admin. */
    public record TwoFaStatus(boolean totpEnabled, boolean pending, long expiresInSeconds, Integer periodSeconds) {
    }

    public static class EnrollmentExpiredException extends RuntimeException {
        public EnrollmentExpiredException() {
            super("Enrollment session expired or not found.");
        }
    }

    public static class AdminNotFoundException extends RuntimeException {
        public AdminNotFoundException(Long adminId) {
            super("Admin not found: " + adminId);
        }
    }

    // ------------------------------------------------------------------ kích hoạt

    /**
     * @param periodOverride chu kỳ admin chọn trong modal lúc bấm "Kích hoạt" (15/30/45/60);
     *                       null thì dùng chu kỳ đã lưu cùng khóa ở Redis. Phải khớp chu kỳ app Authenticator dùng.
     */
    public ActivationResult activate(Long adminId, Integer periodOverride, String ip, String userAgent) {
        if (periodOverride != null && !EnrollSessionServiceImpl.isPeriodAllowed(periodOverride)) {
            throw new IllegalArgumentException("Unsupported period: " + periodOverride);
        }
        EnrollSession pending = enrollSessionRepository.findById(adminId)
                .orElseThrow(EnrollmentExpiredException::new);
        // Phiên tạo trước khi nâng cấp mã hóa (chưa có keyVersion): bắt cài lại.
        if (pending.getKeyVersion() == null) {
            throw new EnrollmentExpiredException();
        }

        CryptoService.Sealed sealed =
                CryptoService.Sealed.fromBase64(pending.getSecretCipher(), pending.getKeyVersion());
        if (sealed.cipher().length > MAX_CIPHER_LENGTH) {
            throw new IllegalStateException("Unexpected secret cipher length: " + sealed.cipher().length);
        }

        int period = periodOverride != null
                ? periodOverride
                : (pending.getPeriodSeconds() == null
                ? EnrollSessionServiceImpl.DEFAULT_PERIOD_SECONDS
                : pending.getPeriodSeconds());

        ActivationResult result = transactionTemplate.execute(status ->
                persist(adminId, sealed, period));

        // Sau khi commit: việc phụ, lỗi cũng không ảnh hưởng kết quả.
        confirmService.resetAttempts(adminId);
        try {
            enrollSessionRepository.deleteById(adminId);
        } catch (Exception e) {
            log.warn("Can't delete enroll session of admin {}: {}", adminId, e.getMessage());
        }
        auditService.log(adminId, AuthEvent.TOTP_ENROLL_ACTIVATED, true, ip, userAgent,
                "period=" + period + "s");
        // Tab web khác của cùng admin cũng chuyển sang "Đang chờ xác nhận"
        eventService.publish(adminId, TwoFaEventServiceImpl.PENDING);
        return result;
    }

    private ActivationResult persist(Long adminId, CryptoService.Sealed sealed, int period) {
        Admin admin = adminRepository.findAdminByAdminId(adminId);
        if (admin == null) {
            throw new AdminNotFoundException(adminId);
        }
        // Đã bật 2FA thì không được cài đè: sẽ để lại 2 credential. Muốn cài lại phải tắt 2FA trước.
        if (Boolean.TRUE.equals(admin.getTotpEnabled())) {
            throw new TotpAlreadyEnabledException();
        }

        totpCredentialRepository.deletePendingByAdminId(adminId);

        TotpCredential credential = new TotpCredential();
        credential.setAdmin(admin);
        credential.setKdfSalt(sealed.salt());
        credential.setIv(sealed.iv());
        credential.setSecretCipher(sealed.cipher());
        credential.setKeyVersion(sealed.keyVersion()); // đúng phiên bản đã mã hóa, không gán cứng 1
        credential.setAlgorithm("HmacSHA256");
        credential.setDigits((byte) 6);
        credential.setPeriodSeconds((short) period);
        credential.setConfirmed(false);
        credential.setConfirmedAt(null);
        credential = totpCredentialRepository.save(credential);

        // totp_enabled CHƯA bật: chỉ bật khi mobile gửi đúng mã đầu tiên.
        admin.setUpdatedAt(Instant.now());
        adminRepository.save(admin);

        return new ActivationResult(adminId, credential.getCredentialId(), credential.getAlgorithm(),
                credential.getDigits(), period, confirmService.getConfirmWindow().getSeconds());
    }

    // ------------------------------------------------------------------ hủy phần chờ xác nhận

    /** "Hủy và cài lại": xóa credential chưa xác nhận của lần cài dở. Không chạm 2FA đã bật. */
    public void cancelPending(Long adminId, String ip, String userAgent) {
        transactionTemplate.executeWithoutResult(status -> {
            Admin admin = adminRepository.findAdminByAdminId(adminId);
            if (admin == null) {
                throw new AdminNotFoundException(adminId);
            }
            totpCredentialRepository.deletePendingByAdminId(adminId);
        });

        confirmService.resetAttempts(adminId);
        enrollSessionRepository.deleteById(adminId); // nếu còn khóa chờ ở Redis thì bỏ luôn
        auditService.log(adminId, AuthEvent.TOTP_ENROLL_CANCELLED, true, ip, userAgent, null);
        eventService.publish(adminId, TwoFaEventServiceImpl.NOT_ENABLED);
    }

    // ------------------------------------------------------------------ trạng thái

    public TwoFaStatus getStatus(Long adminId) {
        Admin admin = adminRepository.findAdminByAdminId(adminId);
        if (admin == null) {
            throw new AdminNotFoundException(adminId);
        }
        boolean enabled = Boolean.TRUE.equals(admin.getTotpEnabled());

        Optional<TotpCredential> pending = totpCredentialRepository
                .findFirstByAdmin_AdminIdAndConfirmedFalseOrderByCreatedAtDesc(adminId);
        if (enabled || pending.isEmpty()) {
            return new TwoFaStatus(enabled, false, 0, null);
        }
        TotpCredential c = pending.get();
        long left = Duration.between(Instant.now(),
                c.getCreatedAt().plus(confirmService.getConfirmWindow())).getSeconds();
        if (left <= 0) {
            return new TwoFaStatus(false, false, 0, null); // đã quá hạn: coi như chưa có
        }
        return new TwoFaStatus(false, true, left, (int) c.getPeriodSeconds());
    }

    // ------------------------------------------------------------------ hỗ trợ
}
