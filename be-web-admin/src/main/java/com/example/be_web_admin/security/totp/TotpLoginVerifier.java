package com.example.be_web_admin.security.totp;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;

/**
 * Kiểm tra mã TOTP lúc ĐĂNG NHẬP. Tái dùng TotpVerifier (base32Decode + generate) để chắc chắn
 * cùng thuật toán với bước xác nhận và với TotpGenerator bên Flutter.
 *
 * Khác TotpVerifier.verify() ở 3 điểm:
 *  - cửa sổ lệch (trễ phía trước / sớm phía sau) cấu hình riêng,
 *  - biết chính xác chu kỳ nào khớp (offset) để ghi log đo độ trễ và chống dùng lại,
 *  - không ném exception khi mã sai: luôn trả Outcome.
 *
 * Ví dụ: server đang ở chu kỳ N (mã 394583), người dùng gõ mã chu kỳ N-1 (345344) vì POST tới chậm
 * -> khớp ở offset -1 và vẫn được chấp nhận.
 */
@Component
public class TotpLoginVerifier {

    public enum Result { OK, INVALID, REPLAYED }

    /** matchedOffset: -1 = mã chu kỳ trước (trễ), 0 = đúng chu kỳ, +1 = mã chu kỳ sau (app chạy nhanh). */
    public record Outcome(Result result, int matchedOffset, long matchedStep) {
        static Outcome invalid() {
            return new Outcome(Result.INVALID, 0, -1);
        }
    }

    private static final int PAST_STEPS = 1;
    private static final int FUTURE_STEPS = 1;

    private final StringRedisTemplate redis;

    public TotpLoginVerifier(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * @param secretBase32 secret Base32 đã giải mã AES (đúng như TotpVerifier.verify đang nhận)
     * @param algorithm    ví dụ "HmacSHA256", cùng giá trị đang dùng ở bước xác nhận
     */
    public Outcome verify(long adminId, String secretBase32, int periodSeconds, int digits,
                          String algorithm, String code, Instant now) {
        if (code == null || !code.matches("\\d{" + digits + "}")) {
            return Outcome.invalid();
        }

        byte[] key = TotpVerifier.base32Decode(secretBase32);
        long currentStep = now.getEpochSecond() / periodSeconds;

        // Duyệt hết các offset, không thoát sớm, để thời gian xử lý không lộ offset nào khớp.
        long matchedStep = -1;
        int matchedOffset = 0;
        for (int offset = -PAST_STEPS; offset <= FUTURE_STEPS; offset++) {
            long step = currentStep + offset;
            String expected = TotpVerifier.generate(key, step, digits, algorithm);
            boolean equal = MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8), code.getBytes(StandardCharsets.UTF_8));
            if (equal && matchedStep < 0) {
                matchedStep = step;
                matchedOffset = offset;
            }
        }
        if (matchedStep < 0) {
            return Outcome.invalid();
        }

        // Chống replay: mỗi (admin, chu kỳ) chỉ dùng 1 lần. setIfAbsent là thao tác nguyên tử trên Redis.
        String redisKey = "totp:used:" + adminId + ":" + matchedStep;
        Duration ttl = Duration.ofSeconds((long) (PAST_STEPS + FUTURE_STEPS + 1) * periodSeconds + 5);
        Boolean firstUse = redis.opsForValue().setIfAbsent(redisKey, "1", ttl);
        return new Outcome(Boolean.TRUE.equals(firstUse) ? Result.OK : Result.REPLAYED, matchedOffset, matchedStep);
    }
}