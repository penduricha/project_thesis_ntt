package com.example.be_web_admin.service.redis.impl;

import com.example.be_web_admin.model.jpa.Admin;
import com.example.be_web_admin.model.redis.EnrollSession;
import com.example.be_web_admin.security.EnrollmentGenerationStore;
import com.example.be_web_admin.security.EnrollmentFenceStore;
import com.example.be_web_admin.repository.jpa.AdminRepository;
import com.example.be_web_admin.security.private_key.CryptoService;
import com.example.be_web_admin.service.redis.EnrollSessionService;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class EnrollSessionServiceImpl implements EnrollSessionService {

    public static final int DEFAULT_PERIOD_SECONDS = 30;

    // Khớp danh sách chọn trong modal Vue (15 / 30 / 45 / 60 giây).
    private static final Set<Integer> ALLOWED_PERIOD_SECONDS = Set.of(15, 30, 45, 60);

    /** secretKey là khóa GỐC, chỉ hiển thị đúng một lần. */
    public record StartedEnrollment(String secretKey, int periodSeconds, int expiresInSeconds, String generation) {
    }

    private final EnrollmentGenerationStore generationStore;
    private final EnrollmentFenceStore fenceStore;
    private final AdminRepository adminRepository;
    private final CryptoService cryptoService;

    public EnrollSessionServiceImpl(EnrollmentGenerationStore generationStore,
                                    EnrollmentFenceStore fenceStore,
                                    AdminRepository adminRepository,
                                    CryptoService cryptoService) {
        this.generationStore = generationStore;
        this.fenceStore = fenceStore;
        this.adminRepository = adminRepository;
        this.cryptoService = cryptoService;
    }

    public boolean isAllowedPeriod(int periodSeconds) {
        return isPeriodAllowed(periodSeconds);
    }

    /** Bản static để EnrollmentServiceImpl dùng chung, không phải inject thêm service. */
    public static boolean isPeriodAllowed(int periodSeconds) {
        return ALLOWED_PERIOD_SECONDS.contains(periodSeconds);
    }

    /**
     * Sinh khóa Base32 mã hóa trong Redis, TTL 5 phút; không ghi đè phiên khác.
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

        // Do not create or expose a secret that cannot later be activated because the
        // required fence migration is missing. Activation still probes again; this
        // check cannot protect against a migration being removed immediately afterwards.
        fenceStore.requireAvailable();
        String secretKey = cryptoService.generateSecretKey();
        // AAD gắn với adminId + keyVersion: bản mã chép sang admin khác sẽ không giải mã được.
        CryptoService.Sealed sealed = cryptoService.encrypt(secretKey, adminId);
        String generation = generationStore.start(adminId, sealed.toBase64(), sealed.keyVersion(), period, EnrollSession.TTL_SECONDS);
        return new StartedEnrollment(secretKey, period, EnrollSession.TTL_SECONDS, generation);
    }

    public boolean cancelEnrollment(Long adminId, String generation) {
        return generationStore.cancel(adminId, generation);
    }
}
