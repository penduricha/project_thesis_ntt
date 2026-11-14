package com.example.be_web_admin.service.redis.impl;

import com.example.be_web_admin.enums.AuthEvent;
import com.example.be_web_admin.model.jpa.Admin;
import com.example.be_web_admin.model.jpa.TotpCredential;
import com.example.be_web_admin.repository.jpa.AdminRepository;
import com.example.be_web_admin.repository.jpa.TotpCredentialRepository;
import com.example.be_web_admin.repository.redis.EnrollSessionRepository;
import com.example.be_web_admin.security.private_key.CryptoService;
import com.example.be_web_admin.security.totp.TotpLoginVerifier;
import com.example.be_web_admin.service.jpa.impl.AuthAuditService;
import com.example.be_web_admin.service.redis.impl.EnrollmentServiceImpl.AdminNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;

/**
 * Tắt 2FA từ app mobile. Bắt buộc mã TOTP HIỆN TẠI (người dùng còn giữ khóa thì mới tắt được).
 *
 * Thứ tự (giống các luồng TOTP khác: transaction MariaDB trước, việc phụ sau khi đã commit):
 *   1. Có credential đã xác nhận không? Không -> TotpNotEnabledException (404).
 *   2. Đang bị khóa vì nhập sai? (Redis totp:disable_fail:{adminId}) -> DisableLockedException (429).
 *   3. Giải mã secret (AES-256-GCM) và dò mã trong T-1, T, T+1 (TotpLoginVerifier, có chống dùng lại mã).
 *      Sai -> tăng bộ đếm, audit TOTP_DISABLE_FAILED; sai lần thứ 5 -> khóa 5 phút.
 *   4. MỘT transaction MariaDB: xóa credential + Admin.totp_enabled = false.
 *   5. Sau commit: dọn Redis (totp:*, enroll_*), audit TOTP_DISABLED, phát SSE NOT_ENABLED.
 */
@Slf4j
@Service
public class TotpDisableServiceImpl {

    private static final int MAX_FAILS = 5;
    private static final Duration FAIL_WINDOW = Duration.ofMinutes(5);

    // Nằm dưới tiền tố "totp:" để việc dọn dẹp totp:* gom được cùng một chỗ.
    private static final String DISABLE_FAIL_KEY = "totp:disable_fail:";
    private static final String LOGIN_FAIL_KEY = "totp:fail:";
    private static final String USED_KEY_PREFIX = "totp:used:"; // totp:used:{adminId}:{step}

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
    private final StringRedisTemplate redis;
    private final TransactionTemplate transactionTemplate;
    private final TwoFaEventServiceImpl eventService;
    private final EnrollSessionRepository enrollSessionRepository;
    private final EnrollmentConfirmServiceImpl confirmService;

    public TotpDisableServiceImpl(TotpCredentialRepository totpCredentialRepository,
                                  AdminRepository adminRepository,
                                  CryptoService cryptoService,
                                  TotpLoginVerifier totpLoginVerifier,
                                  AuthAuditService auditService,
                                  StringRedisTemplate redis,
                                  TransactionTemplate transactionTemplate,
                                  TwoFaEventServiceImpl eventService,
                                  EnrollSessionRepository enrollSessionRepository,
                                  EnrollmentConfirmServiceImpl confirmService) {
        this.totpCredentialRepository = totpCredentialRepository;
        this.adminRepository = adminRepository;
        this.cryptoService = cryptoService;
        this.totpLoginVerifier = totpLoginVerifier;
        this.auditService = auditService;
        this.redis = redis;
        this.transactionTemplate = transactionTemplate;
        this.eventService = eventService;
        this.enrollSessionRepository = enrollSessionRepository;
        this.confirmService = confirmService;
    }

    /**
     * @throws TotpNotEnabledException   admin chưa bật 2FA (không có credential đã xác nhận)
     * @throws DisableLockedException    nhập sai quá {@value #MAX_FAILS} lần, phải chờ hết thời gian khóa
     * @throws InvalidTotpCodeException  mã sai, hết hạn hoặc đã dùng
     * @throws AdminNotFoundException    admin không còn trong DB
     */
    public void disable(Long adminId, String code, String ip, String userAgent) {
        // (1) Chỉ credential ĐÃ xác nhận mới là 2FA đang bật (credential chờ không tính).
        TotpCredential cred = totpCredentialRepository
                .findFirstByAdmin_AdminIdAndConfirmedTrueOrderByConfirmedAtDesc(adminId)
                .orElseThrow(TotpNotEnabledException::new);

        // (2) Khóa tạm khi nhập sai nhiều lần (bộ đếm riêng, không dùng chung totp:fail của đăng nhập web).
        String failKey = DISABLE_FAIL_KEY + adminId;
        String failStr = redis.opsForValue().get(failKey);
        int fails = (failStr == null) ? 0 : Integer.parseInt(failStr);
        if (fails >= MAX_FAILS) {
            throw new DisableLockedException(secondsLeft(failKey));
        }

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
            Long count = redis.opsForValue().increment(failKey);
            if (count != null && count == 1L) {
                redis.expire(failKey, FAIL_WINDOW);
            }
            // Không ghi mã người dùng nhập vào log
            auditService.log(adminId, AuthEvent.TOTP_DISABLE_FAILED, false, ip, userAgent,
                    replayed ? "replayed code" : "wrong code");
            // Lần sai thứ MAX_FAILS: khóa đủ 5 phút kể từ lần sai cuối (giống đăng nhập web)
            if (count != null && count >= MAX_FAILS) {
                redis.expire(failKey, FAIL_WINDOW);
                throw new DisableLockedException(FAIL_WINDOW.toSeconds());
            }
            throw new InvalidTotpCodeException(replayed);
        }

        // (4) Một transaction: xóa credential + tắt cờ trên admin. Hoặc cả hai, hoặc không gì cả.
        Boolean removed = transactionTemplate.execute(status -> {
            Admin admin = adminRepository.findAdminByAdminId(adminId);
            if (admin == null) {
                throw new AdminNotFoundException(adminId);
            }
            int deleted = totpCredentialRepository.deleteAllByAdminId(adminId);
            admin.setTotpEnabled(false);
            admin.setUpdatedAt(Instant.now());
            adminRepository.save(admin);
            return deleted > 0;
        });
        if (!Boolean.TRUE.equals(removed)) {
            // Request song song khác đã tắt xong giữa bước (1) và (4)
            throw new TotpNotEnabledException();
        }

        // (5) Đã commit. Từ đây lỗi phụ chỉ ghi cảnh báo, không làm hỏng việc đã tắt xong.
        clearRedisState(adminId);
        // offset: -1 / 0 / +1 giống TOTP_LOGIN_OK, để thống kê độ trễ cho luận văn
        auditService.log(adminId, AuthEvent.TOTP_DISABLED, true, ip, userAgent,
                "offset=" + outcome.matchedOffset());
        // Mọi tab web chuyển sang "Chưa kích hoạt 2FA" mà không cần tải lại trang
        eventService.publish(adminId, TwoFaEventServiceImpl.NOT_ENABLED);
    }

    // ------------------------------------------------------------------ hỗ trợ

    private long secondsLeft(String key) {
        Long ttl = redis.getExpire(key);
        return (ttl == null || ttl < 1) ? FAIL_WINDOW.toSeconds() : ttl;
    }

    /**
     * Dọn mọi khóa Redis còn dính tới 2FA của admin này:
     *   totp:fail:{id}, totp:disable_fail:{id}, totp:used:{id}:*  (đăng nhập và tắt 2FA)
     *   enroll_confirm_attempts:{id}, enroll_session:{id}          (cài đặt dở)
     * Mỗi mục độc lập, lỗi mục nào chỉ ghi cảnh báo.
     */
    private void clearRedisState(Long adminId) {
        try {
            redis.delete(java.util.List.of(LOGIN_FAIL_KEY + adminId, DISABLE_FAIL_KEY + adminId));
        } catch (Exception e) {
            log.warn("Can't clear totp fail counters of admin {}: {}", adminId, e.getMessage());
        }
        try {
            deleteByPattern(USED_KEY_PREFIX + adminId + ":*");
        } catch (Exception e) {
            log.warn("Can't clear totp:used keys of admin {}: {}", adminId, e.getMessage());
        }
        confirmService.resetAttempts(adminId); // tự bắt lỗi Redis bên trong
        try {
            enrollSessionRepository.deleteById(adminId);
        } catch (Exception e) {
            log.warn("Can't clear enroll_session of admin {}: {}", adminId, e.getMessage());
        }
    }

    /** SCAN theo từng đợt rồi DEL (không dùng KEYS vì KEYS chặn Redis khi nhiều key). */
    private void deleteByPattern(String pattern) {
        redis.execute((RedisCallback<Void>) connection -> {
            ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
            try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                while (cursor.hasNext()) {
                    connection.keyCommands().del(cursor.next());
                }
            }
            return null;
        });
    }
}
