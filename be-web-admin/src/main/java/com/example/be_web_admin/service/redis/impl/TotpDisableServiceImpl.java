package com.example.be_web_admin.service.redis.impl;

import com.example.be_web_admin.enums.AuthEvent;
import com.example.be_web_admin.model.jpa.Admin;
import com.example.be_web_admin.model.jpa.TotpCredential;
import com.example.be_web_admin.repository.jpa.AdminRepository;
import com.example.be_web_admin.repository.jpa.TotpCredentialRepository;
import com.example.be_web_admin.security.AuthenticationRateLimiter;
import com.example.be_web_admin.security.private_key.CryptoService;
import com.example.be_web_admin.security.totp.TotpLoginVerifier;
import com.example.be_web_admin.service.jpa.impl.AuthAuditService;
import com.example.be_web_admin.service.redis.impl.EnrollmentServiceImpl.AdminNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;

/**
 * Tắt 2FA từ app mobile. Bắt buộc mã TOTP HIỆN TẠI (người dùng còn giữ khóa thì mới tắt được).
 *
 * Thứ tự (giống các luồng TOTP khác: transaction MariaDB trước, việc phụ sau khi đã commit):
 *   1. Có credential đã xác nhận không? Không -> TotpNotEnabledException (404).
 *   2. Giành lượt thử nguyên tử trong Redis; hết lượt thì trả DisableLockedException (429).
 *   3. Giải mã secret và dò mã trong T-1, T, T+1 (có chống dùng lại); sai thì audit.
 *   4. MỘT transaction MariaDB: xóa credential + Admin.totp_enabled = false.
 *   5. Sau commit: audit TOTP_DISABLED và phát SSE NOT_ENABLED; Redis keys expire via TTL.
 */
@Service
public class TotpDisableServiceImpl {

    private static final int MAX_FAILS = 5;
    private static final Duration FAIL_WINDOW = Duration.ofMinutes(5);

    public static class TotpNotEnabledException extends RuntimeException {
    }

    public static class InvalidTotpCodeException extends RuntimeException {
        private final boolean replayed;

        public InvalidTotpCodeException(boolean replayed) {
            this.replayed = replayed;
        }

        /** true: mã đúng nhưng đã được dùng ở chu kỳ này (vd vừa đăng nhập web). */
        public boolean isReplayed() {
            return replayed;
        }
    }

    public static class DisableLockedException extends RuntimeException {
        private final long retryAfterSeconds;

        public DisableLockedException(long retryAfterSeconds) {
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    private final TotpCredentialRepository totpCredentialRepository;
    private final AdminRepository adminRepository;
    private final CryptoService cryptoService;
    private final TotpLoginVerifier totpLoginVerifier;
    private final AuthAuditService auditService;
    private final AuthenticationRateLimiter limiter;
    private final TransactionTemplate transactionTemplate;
    private final TwoFaEventServiceImpl eventService;

    public TotpDisableServiceImpl(TotpCredentialRepository totpCredentialRepository,
                                  AdminRepository adminRepository,
                                  CryptoService cryptoService,
                                  TotpLoginVerifier totpLoginVerifier,
                                  AuthAuditService auditService,
                                  AuthenticationRateLimiter limiter,
                                  TransactionTemplate transactionTemplate,
                                  TwoFaEventServiceImpl eventService) {
        this.totpCredentialRepository = totpCredentialRepository;
        this.adminRepository = adminRepository;
        this.cryptoService = cryptoService;
        this.totpLoginVerifier = totpLoginVerifier;
        this.auditService = auditService;
        this.limiter = limiter;
        this.transactionTemplate = transactionTemplate;
        this.eventService = eventService;
    }

    /**
     * @throws TotpNotEnabledException   admin chưa bật 2FA (không có credential đã xác nhận)
     * @throws DisableLockedException    nhập sai quá {@value #MAX_FAILS} lần, phải chờ hết thời gian khóa
     * @throws InvalidTotpCodeException  mã sai, hết hạn hoặc đã dùng
     * @throws AdminNotFoundException    admin không còn trong DB
     */
    public void disable(Long adminId, String code, String ip, String userAgent) {
        disable(adminId, null, code, ip, userAgent);
    }

    public void disable(Long adminId, Long expectedCredentialId, String code, String ip, String userAgent) {
        // (1) Chỉ credential ĐÃ xác nhận mới là 2FA đang bật (credential chờ không tính).
        TotpCredential cred = totpCredentialRepository
                .findFirstByAdmin_AdminIdAndConfirmedTrueOrderByConfirmedAtDesc(adminId)
                .orElseThrow(TotpNotEnabledException::new);
        if (expectedCredentialId != null && !expectedCredentialId.equals(cred.getCredentialId())) {
            throw new TotpNotEnabledException();
        }

        // Reserve before checking; concurrent requests cannot all bypass the attempt cap.
        long wait = limiter.reserve("totp-disable", adminId, MAX_FAILS, FAIL_WINDOW);
        if (wait > 0) throw new DisableLockedException(wait);

        // (3) Giải mã secret (cùng cách AdminLoginServiceImpl) rồi dò mã trong cửa sổ lân cận.
        String secretBase32;
        try {
            // adminId lấy từ tham số, không dùng cred.getAdmin() (proxy LAZY)
            secretBase32 = cryptoService.decrypt(cred.getKdfSalt(), cred.getIv(), cred.getSecretCipher(),
                    cred.getKeyVersion(), adminId);
        } catch (CryptoService.CryptoTamperedException e) {
            auditService.log(adminId, AuthEvent.TOTP_DISABLE_FAILED, false, ip, userAgent,
                    "credential ciphertext authentication failed (possible tampering)");
            throw e;
        }

        TotpLoginVerifier.Outcome outcome = totpLoginVerifier.verify(
                adminId, secretBase32, cred.getPeriodSeconds(), cred.getDigits(),
                cred.getAlgorithm(), code, Instant.now());

        if (outcome.result() != TotpLoginVerifier.Result.OK) {
            boolean replayed = outcome.result() == TotpLoginVerifier.Result.REPLAYED;
            auditService.log(adminId, AuthEvent.TOTP_DISABLE_FAILED, false, ip, userAgent,
                    replayed ? "replayed code" : "wrong code");
            throw new InvalidTotpCodeException(replayed);
        }

        // (4) Một transaction: xóa credential + tắt cờ trên admin. Hoặc cả hai, hoặc không gì cả.
        Boolean removed = transactionTemplate.execute(status -> {
            Admin admin = adminRepository.findForTotpUpdate(adminId);
            if (admin == null) {
                throw new AdminNotFoundException(adminId);
            }
            // Do not let a valid code for an old credential disable its replacement.
            TotpCredential current = totpCredentialRepository
                    .findFirstByAdmin_AdminIdAndConfirmedTrueOrderByConfirmedAtDesc(adminId)
                    .orElseThrow(TotpNotEnabledException::new);
            if (!current.getCredentialId().equals(cred.getCredentialId())) {
                throw new TotpNotEnabledException();
            }
            // Inconsistent multiple active credentials must never become password-only login.
            int deleted = totpCredentialRepository.deleteConfirmedGeneration(adminId, cred.getCredentialId());
            if (deleted != 1 || totpCredentialRepository
                    .findFirstByAdmin_AdminIdAndConfirmedTrueOrderByConfirmedAtDesc(adminId).isPresent()) {
                throw new IllegalStateException("Inconsistent confirmed TOTP credentials");
            }
            admin.setTotpEnabled(false);
            admin.setUpdatedAt(Instant.now());
            adminRepository.save(admin);
            return deleted > 0;
        });
        if (!Boolean.TRUE.equals(removed)) {
            // Request song song khác đã tắt xong giữa bước (1) và (4)
            throw new TotpNotEnabledException();
        }

        // Do not delete admin-wide Redis keys here: a newer enrollment or login may have
        // started after the DB commit. Old counters/session state expire via their TTLs.
        // offset: -1 / 0 / +1 giống TOTP_LOGIN_OK, để thống kê độ trễ cho luận văn
        auditService.log(adminId, AuthEvent.TOTP_DISABLED, true, ip, userAgent,
                "offset=" + outcome.matchedOffset());
        // Mọi tab web chuyển sang "Chưa kích hoạt 2FA" mà không cần tải lại trang
        eventService.publish(adminId, TwoFaEventServiceImpl.NOT_ENABLED);
    }

}
