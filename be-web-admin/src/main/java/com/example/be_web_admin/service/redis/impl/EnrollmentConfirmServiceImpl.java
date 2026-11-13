package com.example.be_web_admin.service.redis.impl;

import com.example.be_web_admin.config.TotpProperties;
import com.example.be_web_admin.enums.AuthEvent;
import com.example.be_web_admin.model.jpa.Admin;
import com.example.be_web_admin.model.jpa.TotpCredential;
import com.example.be_web_admin.repository.jpa.AdminRepository;
import com.example.be_web_admin.repository.jpa.BackupCodeRepository;
import com.example.be_web_admin.repository.jpa.TotpCredentialRepository;
import com.example.be_web_admin.security.private_key.CryptoService;
import com.example.be_web_admin.security.totp.TotpVerifier;
import com.example.be_web_admin.service.jpa.impl.AuthAuditService;
import com.example.be_web_admin.service.redis.impl.EnrollmentServiceImpl.AdminNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;

/** Giai đoạn 3 của cài 2FA: app mobile nhập đúng khóa, gửi mã 6 số đầu tiên để xác nhận. */
@Slf4j
@Service
public class EnrollmentConfirmServiceImpl {

    private static final int CLOCK_SKEW_WINDOW = 1; // +-1 chu kỳ
    private static final String ATTEMPTS_KEY_PREFIX = "enroll_confirm_attempts:";

    public record PendingParams(String algorithm, int digits, int periodSeconds, long expiresInSeconds) {
    }

    public static class NoPendingEnrollmentException extends RuntimeException {
    }

    public static class ConfirmExpiredException extends RuntimeException {
    }

    private final TotpCredentialRepository totpCredentialRepository;
    private final BackupCodeRepository backupCodeRepository;
    private final AdminRepository adminRepository;
    private final AuthAuditService auditService;
    private final CryptoService cryptoService;
    private final TotpProperties totpProperties;
    private final StringRedisTemplate redis;
    private final TransactionTemplate transactionTemplate;
    private final TwoFaEventServiceImpl eventService;

    public EnrollmentConfirmServiceImpl(TotpCredentialRepository totpCredentialRepository,
                                        BackupCodeRepository backupCodeRepository,
                                        AdminRepository adminRepository,
                                        AuthAuditService auditService,
                                        CryptoService cryptoService,
                                        TotpProperties totpProperties,
                                        StringRedisTemplate redis,
                                        TransactionTemplate transactionTemplate,
                                        TwoFaEventServiceImpl eventService) {
        this.totpCredentialRepository = totpCredentialRepository;
        this.backupCodeRepository = backupCodeRepository;
        this.adminRepository = adminRepository;
        this.auditService = auditService;
        this.cryptoService = cryptoService;
        this.totpProperties = totpProperties;
        this.redis = redis;
        this.transactionTemplate = transactionTemplate;
        this.eventService = eventService;
    }

    /** Khớp đồng hồ "Còn 10:00" trên trang web (cấu hình app.totp.confirm-window-minutes). */
    public Duration getConfirmWindow() {
        return Duration.ofMinutes(totpProperties.getConfirmWindowMinutes());
    }

    /** App cần biết chu kỳ/số chữ số trước khi sinh mã (khóa Base32 không chứa các thông số này). Không trả khóa. */
    public PendingParams getPendingParams(Long adminId) {
        TotpCredential c = findPending(adminId);
        return new PendingParams(c.getAlgorithm(), c.getDigits(), c.getPeriodSeconds(), secondsLeft(c));
    }

    /**
     * @return true nếu mã đúng và đã bật 2FA; false nếu mã sai.
     * @throws TooManyAttemptsException nhập sai quá số lần: phần cài dở đã bị xóa, admin phải cài lại.
     */
    public boolean confirm(Long adminId, String code, String ip, String userAgent) {
        TotpCredential c = findPending(adminId);

        // Đếm TRƯỚC khi kiểm tra mã: nhiều request song song vẫn không vượt được giới hạn.
        if (registerAttempt(adminId) > totpProperties.getConfirmMaxAttempts()) {
            abortEnrollment(adminId);
            auditService.log(adminId, AuthEvent.TOTP_ENROLL_CONFIRM_FAILED, false, ip, userAgent,
                    "too many attempts, pending enrollment removed");
            throw new TooManyAttemptsException();
        }

        String secret;
        try {
            // adminId lấy từ tham số (không dùng c.getAdmin(): đó là proxy LAZY).
            secret = cryptoService.decrypt(c.getKdfSalt(), c.getIv(), c.getSecretCipher(),
                    c.getKeyVersion(), adminId);
        } catch (CryptoService.CryptoTamperedException e) {
            auditService.log(adminId, AuthEvent.TOTP_ENROLL_CONFIRM_FAILED, false, ip, userAgent,
                    "credential ciphertext authentication failed (possible tampering)");
            throw e;
        }

        boolean ok = TotpVerifier.verify(secret, code, c.getPeriodSeconds(), c.getDigits(),
                c.getAlgorithm(), CLOCK_SKEW_WINDOW, Instant.now().getEpochSecond());
        if (!ok) {
            auditService.log(adminId, AuthEvent.TOTP_ENROLL_CONFIRM_FAILED, false, ip, userAgent, null);
            return false;
        }

        Boolean changed = transactionTemplate.execute(status -> {
            TotpCredential fresh = totpCredentialRepository.findById(c.getCredentialId())
                    .orElseThrow(NoPendingEnrollmentException::new);
            Admin admin = adminRepository.findAdminByAdminId(adminId);
            if (admin == null) {
                throw new AdminNotFoundException(adminId);
            }
            if (Boolean.TRUE.equals(fresh.getConfirmed())) {
                return false; // gọi lần hai: đã xác nhận rồi, không ghi đè confirmedAt
            }
            Instant now = Instant.now();
            fresh.setConfirmed(true);
            fresh.setConfirmedAt(now);
            totpCredentialRepository.save(fresh);

            admin.setTotpEnabled(true);
            admin.setUpdatedAt(now);
            adminRepository.save(admin);
            return true;
        });

        resetAttempts(adminId);
        if (Boolean.TRUE.equals(changed)) {
            auditService.log(adminId, AuthEvent.TOTP_ENROLL_CONFIRMED, true, ip, userAgent, null);
            // Đã commit: báo cho web (mọi tab) chuyển sang "Đã kích hoạt" mà không cần tải lại trang
            eventService.publish(adminId, TwoFaEventServiceImpl.ENABLED);
        }
        return true;
    }

    /** Xóa bộ đếm nhập sai (gọi khi xác nhận thành công, cài lại hoặc hủy). Lỗi Redis chỉ ghi cảnh báo. */
    public void resetAttempts(Long adminId) {
        try {
            redis.delete(ATTEMPTS_KEY_PREFIX + adminId);
        } catch (Exception e) {
            log.warn("Can't reset confirm attempts of admin {}: {}", adminId, e.getMessage());
        }
    }

    // ------------------------------------------------------------------ hỗ trợ

    private TotpCredential findPending(Long adminId) {
        TotpCredential c = totpCredentialRepository
                .findFirstByAdmin_AdminIdAndConfirmedFalseOrderByCreatedAtDesc(adminId)
                .orElseThrow(NoPendingEnrollmentException::new);
        if (secondsLeft(c) <= 0) {
            abortEnrollment(adminId); // quá hạn: bỏ cả credential lẫn mã dự phòng, admin phải cài lại
            throw new ConfirmExpiredException();
        }
        return c;
    }

    private long secondsLeft(TotpCredential c) {
        return Duration.between(Instant.now(), c.getCreatedAt().plus(getConfirmWindow())).getSeconds();
    }

    /** INCR nguyên tử. Không đếm được thì từ chối luôn (fail closed) thay vì cho thử không giới hạn. */
    private long registerAttempt(Long adminId) {
        String key = ATTEMPTS_KEY_PREFIX + adminId;
        Long n = redis.opsForValue().increment(key);
        if (n == null) {
            throw new IllegalStateException("Can't count confirm attempts");
        }
        if (n == 1L) {
            redis.expire(key, getConfirmWindow());
        }
        return n;
    }

    /** Xóa phần cài dở: credential chưa xác nhận + mã dự phòng (chỉ khi 2FA chưa bật) + bộ đếm. */
    private void abortEnrollment(Long adminId) {
        transactionTemplate.executeWithoutResult(status -> {
            totpCredentialRepository.deletePendingByAdminId(adminId);
            Admin admin = adminRepository.findAdminByAdminId(adminId);
            if (admin != null && !Boolean.TRUE.equals(admin.getTotpEnabled())) {
                backupCodeRepository.purgeByAdminId(adminId);
            }
        });
        resetAttempts(adminId);
        // Quá hạn hoặc nhập sai quá số lần: web về "Chưa kích hoạt"
        eventService.publish(adminId, TwoFaEventServiceImpl.NOT_ENABLED);
    }
}