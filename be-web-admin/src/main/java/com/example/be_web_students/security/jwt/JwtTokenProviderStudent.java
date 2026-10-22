package com.example.be_web_students.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProviderStudent {

    // Khóa bí mật (Tối thiểu 32 ký tự / 256-bit cho HS256)
    // Lưu ý: Trong thực tế nên đưa chuỗi này vào file application.properties/yml
    private static final String JWT_SECRET = "x9K#mP$7vL2!nQ5@zW8*yR1&jF4^bC3(vT0)mN6+qX8=";

    // Thời gian hết hạn Access Token: 5 phút = 300,000 ms
    private static final long ACCESS_TOKEN_EXPIRATION = 5 * 60 * 1000L;

    // Thời gian hết hạn Refresh Token: 30 ngày = 2,592,000,000 ms
    private static final long REFRESH_TOKEN_EXPIRATION = 30L * 24 * 60 * 60 * 1000L;

//    public JwtTokenProvider() {
//
//    }

    // Tạo SecretKey từ chuỗi bí mật
    private SecretKey getSigningKey() {
        byte[] keyBytes = JWT_SECRET.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 1. Tạo Access Token (Thời hạn 5 phút)
     */
    public String generateAccessToken(Long studentId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + ACCESS_TOKEN_EXPIRATION);

        return Jwts.builder()
                .subject(String.valueOf(studentId))
                .issuedAt(now)
                .expiration(expiryDate)
                .claim("token_type", "ACCESS")
                // Đánh dấu loại token để tránh dùng nhầm RT thay AT
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 2. Tạo Refresh Token (Thời hạn 30 ngày)
     */
    public String generateRefreshToken(Long studentId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + REFRESH_TOKEN_EXPIRATION);

        return Jwts.builder()
                .subject(String.valueOf(studentId))
                .issuedAt(now)
                .expiration(expiryDate)
                .claim("token_type", "REFRESH")
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 3. Giải mã và Lấy studentId từ Token
     */
    public Long getStudentIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return Long.parseLong(claims.getSubject());
    }

    /**
     * 4. Verify/Xác thực Token (Không cần gọi Database)
     * Trả về true nếu Token hợp lệ và chưa hết hạn.
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            // Token đã hết hạn
            System.err.println("JWT Token đã hết hạn: " + e.getMessage());
        } catch (JwtException | IllegalArgumentException e) {
            // Token bị chỉnh sửa, sai chữ ký hoặc không đúng định dạng
            System.err.println("JWT Token không hợp lệ: " + e.getMessage());
        }
        return false;
    }
}