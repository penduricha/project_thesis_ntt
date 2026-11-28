package com.example.be_web_admin.service.redis.impl;

import com.example.be_web_admin.config.TotpProperties;
import com.example.be_web_admin.enums.AuthEvent;
import com.example.be_web_admin.model.jpa.Admin;
import com.example.be_web_admin.model.jpa.TotpCredential;
import com.example.be_web_admin.repository.jpa.AdminRepository;
import com.example.be_web_admin.repository.jpa.TotpCredentialRepository;
import com.example.be_web_admin.security.private_key.CryptoService;
import com.example.be_web_admin.security.totp.TotpVerifier;
import com.example.be_web_admin.service.jpa.impl.AuthAuditService;
import com.example.be_web_admin.service.redis.impl.EnrollmentServiceImpl.AdminNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Giai đoạn 3 của cài 2FA: app mobile nhập đúng khóa, gửi mã 6 số đầu tiên để xác nhận. */
@Slf4j
@Service
public class EnrollmentConfirmServiceImpl {

    private static final int CLOCK_SKEW_WINDOW = 1; // +-1 chu kỳ
    private static final String ATTEMPTS_KEY_PREFIX = "enroll_confirm_attempts:";
    // Increment and TTL must be one Redis operation. Repair keys left without TTL by older code.
    private static final DefaultRedisScript<Long> REGISTER_ATTEMPT = new DefaultRedisScript<>(
            "local n=redis.call('INCR',KEYS[1]); " +
            "if redis.call('TTL',KEYS[1]) == -1 then redis.call('EXPIRE',KEYS[1],ARGV[1]) end; return n",
            Long.class);

    public record PendingParams(Long credentialId, String algorithm, int digits, int periodSeconds, long expiresInSeconds) {
    }

    public static class NoPendingEnrollmentException extends RuntimeException {
    }

    public static class ConfirmExpiredException extends RuntimeException {
    }

    private final TotpCredentialRepository totpCredentialRepository;
    private final AdminRepository adminRepository;
    private final AuthAuditService auditService;
    private final CryptoService cryptoService;
    private final TotpProperties totpProperties;
    private final StringRedisTemplate redis;
    private final TransactionTemplate transactionTemplate;
    private final TwoFaEventServiceImpl eventService;

    public EnrollmentConfirmServiceImpl(TotpCredentialRepository totpCredentialRepository,
                                        AdminRepository adminRepository,
                                        AuthAuditService auditService,
                                        CryptoService cryptoService,
                                        TotpProperties totpProperties,
                                        StringRedisTemplate redis,
                                        TransactionTemplate transactionTemplate,
                                        TwoFaEventServiceImpl eventService) {
        this.totpCredentialRepository = totpCredentialRepository;
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
        return new PendingParams(c.getCredentialId(), c.getAlgorithm(), c.getDigits(), c.getPeriodSeconds(), secondsLeft(c));
    }

    /**
     * @return true nếu mã đúng và đã bật 2FA; false nếu mã sai.
     * @throws TooManyAttemptsException nhập sai quá số lần: phần cài dở đã bị xóa, admin phải cài lại.
     */
    public boolean confirm(Long adminId, String code, String ip, String userAgent) {
        return confirm(adminId, null, code, ip, userAgent);
    }

    public boolean confirm(Long adminId, Long expectedCredentialId, String code, String ip, String userAgent) {
        TotpCredential c = findPending(adminId);
        if (expectedCredentialId != null && !expectedCredentialId.equals(c.getCredentialId())) {
            throw new NoPendingEnrollmentException();
        }

        // Đếm TRƯỚC khi kiểm tra mã: nhiều request song song vẫn không vượt được giới hạn.
        if (registerAttempt(adminId, c.getCredentialId()) > totpProperties.getConfirmMaxAttempts()) {
            abortEnrollment(adminId, c.getCredentialId());
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
            Admin admin = adminRepository.findForTotpUpdate(adminId);
            if (admin == null) {
                throw new AdminNotFoundException(adminId);
            }
            // The pending generation may have been canceled/replaced while the code was checked.
            TotpCredential fresh = totpCredentialRepository
                    .findFirstByAdmin_AdminIdAndConfirmedFalseOrderByCreatedAtDesc(adminId)
                    .filter(pending -> pending.getCredentialId().equals(c.getCredentialId()))
                    .orElseThrow(NoPendingEnrollmentException::new);
            if (secondsLeft(fresh) <= 0) throw new ConfirmExpiredException();
            if (!Boolean.FALSE.equals(admin.getTotpEnabled()) || totpCredentialRepository
                    .findFirstByAdmin_AdminIdAndConfirmedTrueOrderByConfirmedAtDesc(adminId).isPresent()) {
                throw new NoPendingEnrollmentException();
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

    /** Reconcile only after proving possession of the secret for this exact active credential. */
    public boolean verifyConfirmedGeneration(Long adminId, Long credentialId, String code) {
        if (credentialId == null || credentialId <= 0 || code == null || !code.matches("[0-9]{6}")) return false;
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            Admin admin = adminRepository.findForTotpUpdate(adminId);
            if (admin == null || !Boolean.TRUE.equals(admin.getTotpEnabled())) return false;
            TotpCredential c = totpCredentialRepository.findFirstByAdmin_AdminIdAndConfirmedTrueOrderByConfirmedAtDesc(adminId)
                    .filter(found -> credentialId.equals(found.getCredentialId())).orElse(null);
            if (c == null) return false;
            if (registerAttemptKey("enroll_receipt_attempts:" + adminId + ":" + credentialId)
                    > totpProperties.getConfirmMaxAttempts()) throw new TooManyAttemptsException();
            String secret = cryptoService.decrypt(c.getKdfSalt(), c.getIv(), c.getSecretCipher(), c.getKeyVersion(), adminId);
            return TotpVerifier.verify(secret, code, c.getPeriodSeconds(), c.getDigits(),
                    c.getAlgorithm(), CLOCK_SKEW_WINDOW, Instant.now().getEpochSecond());
        }));
    }

    /** Xóa bộ đếm cũ theo admin. Lượt thử mới theo generation tự hết hạn. */
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
            abortEnrollment(adminId, c.getCredentialId()); // only the expired generation
            throw new ConfirmExpiredException();
        }
        return c;
    }

    private long secondsLeft(TotpCredential c) {
        return Duration.between(Instant.now(), c.getCreatedAt().plus(getConfirmWindow())).getSeconds();
    }

    /** INCR nguyên tử. Không đếm được thì từ chối luôn (fail closed) thay vì cho thử không giới hạn. */
    private long registerAttempt(Long adminId, Long credentialId) {
        if (credentialId == null) throw new IllegalStateException("Pending credential has no id");
        return registerAttemptKey(ATTEMPTS_KEY_PREFIX + adminId + ":" + credentialId);
    }

    private long registerAttemptKey(String key) {
        long seconds = getConfirmWindow().getSeconds();
        if (seconds <= 0) throw new IllegalStateException("Invalid confirm window");
        Long n = redis.execute(REGISTER_ATTEMPT, List.of(key), String.valueOf(seconds));
        if (n == null) {
            throw new IllegalStateException("Can't count confirm attempts");
        }
        return n;
    }

    /** Delete only the expired/locked generation, never a replacement created concurrently. */
    private void abortEnrollment(Long adminId, Long credentialId) {
        if (credentialId == null) throw new IllegalStateException("Pending credential has no id");
        Boolean removed = transactionTemplate.execute(status -> {
            Admin admin = adminRepository.findForTotpUpdate(adminId);
            if (admin == null) throw new AdminNotFoundException(adminId);
            return totpCredentialRepository.deletePendingGeneration(adminId, credentialId) > 0;
        });
        // Per-generation counters expire on their own. Do not clear a newer generation's counter.
        if (Boolean.TRUE.equals(removed)) {
            eventService.publish(adminId, TwoFaEventServiceImpl.NOT_ENABLED);
        }
    }
}