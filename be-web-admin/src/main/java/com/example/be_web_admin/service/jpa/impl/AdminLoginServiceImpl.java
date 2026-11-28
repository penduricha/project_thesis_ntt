package com.example.be_web_admin.service.jpa.impl;

import com.example.be_web_admin.dto.AdminLoginDTO;
import com.example.be_web_admin.dto.LoginOutcome;
import com.example.be_web_admin.enums.AuthEvent;
import com.example.be_web_admin.enums.LoginStatus;
import com.example.be_web_admin.model.jpa.Admin;
import com.example.be_web_admin.model.jpa.TotpCredential;
import com.example.be_web_admin.repository.jpa.AdminRepository;
import com.example.be_web_admin.repository.jpa.TotpCredentialRepository;
import com.example.be_web_admin.security.AuthSessionStore;
import com.example.be_web_admin.security.AuthenticationRateLimiter;
import com.example.be_web_admin.security.password.MgmtPassword;
import com.example.be_web_admin.security.private_key.CryptoService;
import com.example.be_web_admin.security.totp.TotpLoginVerifier;
import com.example.be_web_admin.service.redis.AdminTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Đăng nhập một request duy nhất gồm adminId, password và totpCode (tùy chọn):
 *  - Chưa bật 2FA: adminId + password là vào, totpCode để trống.
 *  - Đã bật 2FA: bắt buộc totpCode; thiếu -> TOTP_REQUIRED, sai/hết hạn/đã dùng -> TOTP_INVALID.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminLoginServiceImpl {

    private static final int MAX_TOTP_FAILS = 5;
    private static final Duration TOTP_FAIL_WINDOW = Duration.ofMinutes(5);

    // Fixed-window reservations count both successful and failed logins. This avoids allowing
    // in-flight attempts to escape the cap when another request succeeds and resets a counter.
    private static final int MAX_PASSWORD_FAILS = 10;
    private static final Duration PASSWORD_FAIL_WINDOW = Duration.ofMinutes(15);

    private final AdminRepository adminRepository;
    private final TotpCredentialRepository totpCredentialRepository;
    private final MgmtPassword mgmtPassword;
    private final TotpLoginVerifier totpLoginVerifier;
    private final CryptoService cryptoService;
    private final AuthAuditService auditService;
    private final AuthenticationRateLimiter limiter;
    private final AuthSessionStore sessions;

    // Inject interface (không inject AdminTokenServiceImpl): đã tạo JWT và lưu access/refresh token vào Redis
    private final AdminTokenService adminTokenService;

    public LoginOutcome login(AdminLoginDTO req, String ip, String userAgent) {
        return login(req, ip, userAgent, AuthSessionStore.Client.WEB, null, null);
    }

    public LoginOutcome loginMobile(AdminLoginDTO req, String ip, String userAgent, String device, String platform) {
        return login(req, ip, userAgent, AuthSessionStore.Client.MOBILE, device, platform);
    }

    private LoginOutcome login(AdminLoginDTO req, String ip, String userAgent,
                               AuthSessionStore.Client client, String device, String platform) {
        if (req == null || req.getAdminId() == null || req.getPassword() == null) {
            return LoginOutcome.of(LoginStatus.BAD_CREDENTIALS);
        }
        Long adminId = req.getAdminId();

        // Reserve atomically before BCrypt. Counts all attempts, including successful ones;
        // no concurrent success can erase reservations from other in-flight attempts.
        // Use the socket peer, not an untrusted X-Forwarded-For header. Behind a proxy this is
        // a shared limit, so keep it conservative and configure trusted forwarding upstream.
        String peer = ip == null ? "unknown" : ip;
        long ipWait = limiter.reserve("ip", peer, 300, Duration.ofMinutes(1));
        if (ipWait > 0) return LoginOutcome.locked(ipWait);
        long passwordWait = limiter.reserve("password", adminId, MAX_PASSWORD_FAILS, PASSWORD_FAIL_WINDOW);
        if (passwordWait > 0) return LoginOutcome.locked(passwordWait);

        // (a) Mật khẩu BCrypt. Cột password trong DB ĐÃ là hash BCrypt, so khớp thẳng với mật khẩu người dùng nhập
        //     (không được băm lại hash đó). Admin không tồn tại: matches() vẫn chạy 1 lần BCrypt giả
        //     để thời gian phản hồi không lộ tài khoản có tồn tại hay không.
        Admin admin = adminRepository.findAdminByAdminId(adminId);
        String storedHash = (admin == null) ? null : admin.getPassword();
        boolean passwordOk = mgmtPassword.matches(req.getPassword(), storedHash); // luôn chạy, kể cả admin = null
        if (admin == null || !passwordOk || !admin.isLoginAllowed()) {
            return LoginOutcome.of(LoginStatus.BAD_CREDENTIALS);
        }

        // (b) Chỉ credential ĐÃ xác nhận bên mobile mới tính là bật 2FA (pending không tính).
        //     - Chưa bật 2FA: bỏ qua ô mã 6 số (kể cả khi có gửi lên) -> vào luôn.
        //     - Đã bật 2FA: BẮT BUỘC có mã 6 số. Chỉ trả TOTP_REQUIRED sau khi mật khẩu đã đúng.
        Optional<TotpCredential> credOpt = totpCredentialRepository
                .findFirstByAdmin_AdminIdAndConfirmedTrueOrderByConfirmedAtDesc(adminId);
        if (credOpt.isEmpty()) {
            // An enabled or unknown flag without a confirmed credential is inconsistent. Never
            // silently downgrade to password-only login; an operator must investigate the data.
            // A confirmed credential always takes precedence over the flag in the opposite case.
            if (!Boolean.FALSE.equals(admin.getTotpEnabled())) {
                log.error("Refusing login for admin {}: 2FA flag is enabled or unknown but no confirmed credential exists", adminId);
                return LoginOutcome.of(LoginStatus.BAD_CREDENTIALS);
            }
            return issueTokens(admin, client, device, platform, ip);
        }

        String code = req.getTotpCode() == null ? null : req.getTotpCode().trim();
        if (code == null || code.isEmpty()) {
            return LoginOutcome.of(LoginStatus.TOTP_REQUIRED);
        }

        long totpWait = limiter.reserve("totp-login", adminId, MAX_TOTP_FAILS, TOTP_FAIL_WINDOW);
        if (totpWait > 0) return LoginOutcome.locked(totpWait);

        // (d) Giải mã secret (cùng cách EnrollmentConfirmServiceImpl) rồi dò mã trong cửa sổ chu kỳ lân cận
        TotpCredential cred = credOpt.get();
        String secretBase32;
        try {
            // adminId lấy từ tham số, không dùng cred.getAdmin() (proxy LAZY)
            secretBase32 = cryptoService.decrypt(cred.getKdfSalt(), cred.getIv(), cred.getSecretCipher(),
                    cred.getKeyVersion(), adminId);
        } catch (CryptoService.CryptoTamperedException e) {
            auditService.log(adminId, AuthEvent.TOTP_LOGIN_FAILED, false, ip, userAgent,
                    "credential ciphertext authentication failed (possible tampering)");
            return LoginOutcome.of(LoginStatus.TOTP_INVALID);
        }

        TotpLoginVerifier.Outcome outcome = totpLoginVerifier.verify(
                adminId, secretBase32, cred.getPeriodSeconds(), cred.getDigits(),
                cred.getAlgorithm(), code, Instant.now());

        if (outcome.result() == TotpLoginVerifier.Result.OK) {
            // offset: -1 = mã chu kỳ trước (POST tới trễ), 0 = đúng chu kỳ, +1 = app chạy nhanh. Dùng để thống kê độ trễ cho luận văn.
            auditService.log(adminId, AuthEvent.TOTP_LOGIN_OK, true, ip, userAgent,
                    "offset=" + outcome.matchedOffset());
            return issueTokens(admin, client, device, platform, ip);
        }

        auditService.log(adminId, AuthEvent.TOTP_LOGIN_FAILED, false, ip, userAgent,
                outcome.result() == TotpLoginVerifier.Result.REPLAYED ? "replayed code" : "wrong code");

        // INVALID và REPLAYED trả chung một thông báo để không lộ thêm thông tin
        return LoginOutcome.of(LoginStatus.TOTP_INVALID);
    }

    private LoginOutcome issueTokens(Admin admin, AuthSessionStore.Client client, String device, String platform, String ip) {
        AuthSessionStore.Identity identity = sessions.open(admin.getAdminId(), client, device, platform, ip).orElse(null);
        if (identity == null) return LoginOutcome.of(LoginStatus.MOBILE_CONFLICT);
        Map<String, Object> tokens;
        try {
            tokens = adminTokenService.createTokenAdmin(identity);
        } catch (RuntimeException e) {
            try { sessions.revoke(identity); } catch (RuntimeException ignored) { }
            throw e;
        }
        Object accessToken = tokens.get("accessToken");
        Object refreshToken = tokens.get("refreshToken");
        if (accessToken == null || refreshToken == null) {
            sessions.revoke(identity);
            throw new IllegalStateException("Không tạo được token cho admin " + admin.getAdminId());
        }
        // Ghi mốc đăng nhập cuối (chỉ bước đăng nhập tương tác mới ghi).
        // Best-effort: lỗi ghi không được làm hỏng luồng đăng nhập đã thành công.
        try {
            adminRepository.updateLastLoginAt(admin.getAdminId(), Instant.now());
        } catch (Exception e) {
            log.warn("Can't record lastLoginAt of admin {}: {}", admin.getAdminId(), e.getMessage());
        }
        return LoginOutcome.ok(String.valueOf(accessToken), String.valueOf(refreshToken));
    }
}
