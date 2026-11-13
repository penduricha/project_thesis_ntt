package com.example.be_web_admin.service.totp;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;

/**
 * Kiểm tra mã TOTP lúc ĐĂNG NHẬP.
 *
 * Khác với bước xác nhận khi cài đặt, ở đây:
 *  - dò thêm các chu kỳ lân cận để bù độ trễ mạng / lệch đồng hồ,
 *  - không bao giờ ném exception khi mã sai: luôn trả về Outcome,
 *  - mỗi chu kỳ chỉ được dùng một lần (chống replay).
 *
 * Mô phỏng ví dụ trong đề bài: server đang ở chu kỳ N (mã 394583),
 * nhưng người dùng gõ mã của chu kỳ N-1 (345344) vì POST tới chậm
 * -> khớp ở offset -1 và vẫn được chấp nhận.
 */
@Service
public class TotpLoginVerifier {

    public enum Result { OK, INVALID, REPLAYED }

    /**
     * @param matchedOffset  -1 = mã chu kỳ trước (trễ), 0 = đúng chu kỳ, +1 = mã chu kỳ sau (app chạy nhanh).
     *                       Chỉ có nghĩa khi result = OK hoặc REPLAYED. Dùng để ghi log đo độ trễ cho luận văn.
     */
    public record Outcome(Result result, int matchedOffset, long matchedStep) {
        static Outcome invalid() { return new Outcome(Result.INVALID, 0, -1); }
    }

    /** Số chu kỳ chấp nhận phía TRƯỚC (bù độ trễ khi POST). */
    private static final int PAST_STEPS = 1;
    /** Số chu kỳ chấp nhận phía SAU (bù app/điện thoại chạy nhanh hơn server). */
    private static final int FUTURE_STEPS = 1;
    private static final int DIGITS = 6;
    private static final int MODULO = 1_000_000;

    private final StringRedisTemplate redis;

    public TotpLoginVerifier(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * @param secret        secret đã GIẢI MÃ AES (dạng byte, cùng cách decode Base32 với Flutter TotpGenerator)
     * @param periodSeconds chu kỳ lưu trong credential (15/30/45/60)
     * @param code          6 số người dùng nhập
     */
    public Outcome verify(long adminId, byte[] secret, int periodSeconds, String code, Instant now) {
        if (code == null || !code.matches("\\d{" + DIGITS + "}")) {
            return Outcome.invalid();
        }

        long currentStep = now.getEpochSecond() / periodSeconds;

        // Duyệt HẾT các offset, không return sớm, để thời gian xử lý không lộ offset nào khớp.
        long matchedStep = -1;
        int matchedOffset = 0;
        for (int offset = -PAST_STEPS; offset <= FUTURE_STEPS; offset++) {
            long step = currentStep + offset;
            String expected = generate(secret, step);
            if (constantTimeEquals(expected, code) && matchedStep < 0) {
                matchedStep = step;
                matchedOffset = offset;
            }
        }

        if (matchedStep < 0) {
            return Outcome.invalid();
        }

        // Chống replay: mỗi (admin, chu kỳ) chỉ được dùng 1 lần. setIfAbsent là thao tác nguyên tử trên Redis.
        String key = "totp:used:" + adminId + ":" + matchedStep;
        Duration ttl = Duration.ofSeconds((long) (PAST_STEPS + FUTURE_STEPS + 1) * periodSeconds + 5);
        Boolean firstUse = redis.opsForValue().setIfAbsent(key, "1", ttl);
        if (!Boolean.TRUE.equals(firstUse)) {
            return new Outcome(Result.REPLAYED, matchedOffset, matchedStep);
        }
        return new Outcome(Result.OK, matchedOffset, matchedStep);
    }

    /**
     * RFC 6238 + RFC 4226 (dynamic truncation) với HMAC-SHA256.
     * Nếu bạn đã có hàm sinh mã trong TotpVerifier (dùng cho bước xác nhận), hãy gọi lại hàm đó
     * thay vì giữ bản này, để chắc chắn khớp 100% với TotpGenerator bên Flutter.
     */
    String generate(byte[] secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());

            int o = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[o] & 0x7F) << 24)
                    | ((hash[o + 1] & 0xFF) << 16)
                    | ((hash[o + 2] & 0xFF) << 8)
                    | (hash[o + 3] & 0xFF);
            return String.format("%0" + DIGITS + "d", binary % MODULO);
        } catch (Exception e) {
            // Lỗi cấu hình (thiếu thuật toán...), không phải lỗi do người dùng nhập sai.
            throw new IllegalStateException("Không sinh được mã TOTP", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
