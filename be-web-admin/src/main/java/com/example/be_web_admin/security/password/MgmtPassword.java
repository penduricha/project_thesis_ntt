package com.example.be_web_admin.security.password;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * ĐIỂM DUY NHẤT biết thuật toán băm mật khẩu của hệ thống.
 * Mọi nơi băm hoặc kiểm tra mật khẩu (đăng nhập, đổi mật khẩu, data_init...) chỉ gọi class này.
 * Muốn đổi thuật toán: sửa createEncoder(), các nơi gọi không phải sửa.
 *
 * BCrypt cost (strength) = 12, nghĩa là 2^12 vòng lặp, KHÔNG phải độ dài mật khẩu.
 */
@Component
public class MgmtPassword {

    public static final int DEFAULT_STRENGTH = 12;

    private final PasswordEncoder encoder = createEncoder();

    // Hash giả để admin không tồn tại cũng tốn thời gian băm như admin có thật
    private final String dummyHash = encoder.encode("dummy-password-for-timing-only");

    public MgmtPassword() {
    }

    private static PasswordEncoder createEncoder() {
        return new BCryptPasswordEncoder(DEFAULT_STRENGTH);
    }

    /** Băm bằng thuật toán hiện hành. */
    public String generateHash(String rawText) {
        return encoder.encode(rawText);
    }

    /** Giữ chữ ký cũ để code đang gọi generateHash(raw, strength) (ví dụ data_init) vẫn chạy. */
    public String generateHash(String rawText, int strength) {
        return new BCryptPasswordEncoder(strength).encode(rawText);
    }

    /**
     * So khớp mật khẩu với hash trong DB. Cost nằm sẵn trong chuỗi BCrypt nên hash cũ (cost khác 12) vẫn kiểm được.
     * @param hash null nếu admin không tồn tại: vẫn chạy 1 lần băm rồi trả false.
     */
    public boolean matches(String rawText, String hash) {
        if (rawText == null) {
            return false;
        }
        if (hash == null || hash.isBlank()) {
            encoder.matches(rawText, dummyHash);
            return false;
        }
        return encoder.matches(rawText, hash);
    }

    /**
     * true nếu hash đang lưu yếu hơn chuẩn hiện hành (ví dụ cost thấp hơn 12).
     * Gọi sau khi đăng nhập thành công để băm lại và lưu, giúp DB chuyển dần sang chuẩn mới.
     */
    public boolean needsRehash(String hash) {
        return hash != null && !hash.isBlank() && encoder.upgradeEncoding(hash);
    }
}