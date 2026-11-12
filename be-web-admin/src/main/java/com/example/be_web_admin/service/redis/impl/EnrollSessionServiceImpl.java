package com.example.be_web_admin.service.redis.impl;

import com.example.be_web_admin.model.jpa.Admin;
import com.example.be_web_admin.model.redis.EnrollSession;
import com.example.be_web_admin.repository.jpa.AdminRepository;
import com.example.be_web_admin.repository.redis.EnrollSessionRepository;
import com.example.be_web_admin.security.private_key.EncryptionUtil;
import com.example.be_web_admin.service.redis.EnrollSessionService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;

@Service
public class EnrollSessionServiceImpl implements EnrollSessionService {

    public static final int DEFAULT_PERIOD_SECONDS = 30;

    // Khớp danh sách chọn trong modal Vue (15 / 30 / 45 / 60 giây).
    private static final Set<Integer> ALLOWED_PERIOD_SECONDS = Set.of(15, 30, 45, 60);

    /** secretKey là khóa GỐC, chỉ hiển thị đúng một lần. */
    public record StartedEnrollment(String secretKey, int periodSeconds, int expiresInSeconds) {
    }

    private final EnrollSessionRepository enrollSessionRepository;
    private final AdminRepository adminRepository;

    public EnrollSessionServiceImpl(EnrollSessionRepository enrollSessionRepository,
                                    AdminRepository adminRepository) {
        this.enrollSessionRepository = enrollSessionRepository;
        this.adminRepository = adminRepository;
    }

    public boolean isAllowedPeriod(int periodSeconds) {
        return ALLOWED_PERIOD_SECONDS.contains(periodSeconds);
    }

    /**
     * Sinh khóa Base32 (32 ký tự) cho admin, lưu Redis dạng mã hóa, sống 5 phút. Gọi lại thì khóa cũ bị ghi đè.
     *
     * @throws TotpAlreadyEnabledException admin đã bật 2FA
     */
    public StartedEnrollment startEnrollment(Long adminId, Integer periodSeconds) {
        int period = periodSeconds == null ? DEFAULT_PERIOD_SECONDS : periodSeconds;
        if (!isAllowedPeriod(period)) {
            throw new IllegalArgumentException("Unsupported period: " + period);
        }

        Admin admin = adminRepository.findAdminByAdminId(adminId);
        if (admin != null && Boolean.TRUE.equals(admin.getTotpEnabled())) {
            throw new TotpAlreadyEnabledException();
        }

        String secretKey = EncryptionUtil.generateSecretKey();
        enrollSessionRepository.save(new EnrollSession(
                adminId, EncryptionUtil.encrypt(secretKey), period, 0, Instant.now()));
        return new StartedEnrollment(secretKey, period, EnrollSession.TTL_SECONDS);
    }

    /** Hủy phiên chờ ở Redis (đóng modal / rời trang). Gọi nhiều lần vẫn an toàn. */
    public boolean cancelEnrollment(Long adminId) {
        if (!enrollSessionRepository.existsById(adminId)) {
            return false;
        }
        enrollSessionRepository.deleteById(adminId);
        return true;
    }
}