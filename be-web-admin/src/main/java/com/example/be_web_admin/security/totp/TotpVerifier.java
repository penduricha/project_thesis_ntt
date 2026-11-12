package com.example.be_web_admin.security.totp;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/** TOTP theo RFC 6238 (HMAC-SHA256 mặc định). Khóa là chuỗi Base32 RFC 4648, không padding. */
public final class TotpVerifier {

    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private TotpVerifier() {
    }

    public static byte[] base32Decode(String text) {
        String s = text.trim().replace("=", "").toUpperCase();
        ByteBuffer out = ByteBuffer.allocate(s.length() * 5 / 8);
        int buffer = 0, bits = 0;
        for (char c : s.toCharArray()) {
            int v = BASE32.indexOf(c);
            if (v < 0) throw new IllegalArgumentException("Invalid Base32 character: " + c);
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) ((buffer >> (bits - 8)) & 0xFF));
                bits -= 8;
            }
        }
        return out.array();
    }

    /** Sinh mã cho một bộ đếm (counter = floor(epochSeconds / period)). */
    public static String generate(byte[] key, long counter, int digits, String algorithm) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(key, algorithm));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int mod = 1;
            for (int i = 0; i < digits; i++) mod *= 10;
            return String.format("%0" + digits + "d", binary % mod);
        } catch (Exception e) {
            throw new IllegalStateException("Can't compute TOTP: " + e.getMessage(), e);
        }
    }

    /** Chấp nhận mã của chu kỳ hiện tại và +-window chu kỳ lân cận (lệch đồng hồ). */
    public static boolean verify(String secretBase32, String code, int periodSeconds, int digits,
                                 String algorithm, int window, long nowEpochSeconds) {
        if (code == null || !code.matches("\\d{" + digits + "}")) return false;
        byte[] key = base32Decode(secretBase32);
        long counter = nowEpochSeconds / periodSeconds;
        boolean ok = false;
        for (long i = -window; i <= window; i++) {
            String expected = generate(key, counter + i, digits, algorithm);
            // So sánh thời gian hằng số; không thoát sớm để không lộ vị trí khớp.
            ok |= MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                    code.getBytes(StandardCharsets.UTF_8));
        }
        return ok;
    }
}
