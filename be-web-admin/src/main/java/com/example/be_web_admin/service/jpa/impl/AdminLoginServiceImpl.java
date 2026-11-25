package com.example.be_web_admin.service.jpa.impl;

import com.example.be_web_admin.dto.AdminLoginDTO;
import com.example.be_web_admin.dto.LoginOutcome;
import com.example.be_web_admin.enums.AuthEvent;
import com.example.be_web_admin.enums.LoginStatus;
import com.example.be_web_admin.model.jpa.Admin;
import com.example.be_web_admin.model.jpa.TotpCredential;
import com.example.be_web_admin.repository.jpa.AdminRepository;
import com.example.be_web_admin.repository.jpa.TotpCredentialRepository;
import com.example.be_web_admin.security.password.MgmtPassword;
import com.example.be_web_admin.security.private_key.CryptoService;
import com.example.be_web_admin.security.totp.TotpLoginVerifier;
import com.example.be_web_admin.service.redis.AdminTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
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
    private static final String TOTP_FAIL_KEY = "totp:fail:";

    /**
     * Chống dò mật khẩu: 10 lần sai liên tiếp cho MỘT adminId thì khoá 15 phút.
     *
     * <p>Trước bản vá chỉ mã TOTP được đếm; còn mật khẩu thì không — mà {@code adminId} là số tăng
     * dần và hiện ngay trên form đăng nhập, nên kẻ tấn công biết trước tài khoản cần dò. BCrypt
     * chỉ làm chậm mỗi lần thử (khoảng 100ms), không chặn được vét cạn chạy song song.</p>
     *
     * <p>Đếm được SAU khi mật khẩu đã đúng hay chưa đều phải làm, nhưng cố ý khác TOTP: ở bước
     * mật khẩu thì đếm cả khi SAI (đó chính là thứ cần chặn), nên kẻ không biết mật khẩu vẫn khoá
     * được tài khoản người khác trong 15 phút. Đây là đánh đổi đã cân nhắc: thà khoá tạm 15 phút
     * còn hơn để lộ mật khẩu. Khoá chỉ áp cho MỘT adminId (không theo IP) để NAT/proxy dùng chung
     * không khoá oan cả trường, và không đếm được thì không khoá (Redis hỏng vẫn đăng nhập được).</p>
     */
    private static final int MAX_PASSWORD_FAILS = 10;
    private static final Duration PASSWORD_FAIL_WINDOW = Duration.ofMinutes(15);
    private static final String PASSWORD_FAIL_KEY = "pwd:fail:";

    private final AdminRepository adminRepository;
    private final TotpCredentialRepository totpCredentialRepository;
    private final MgmtPassword mgmtPassword;
    private final TotpLoginVerifier totpLoginVerifier;
    private final CryptoService cryptoService;
    private final AuthAuditService auditService;
    private final StringRedisTemplate redis;

    // Inject interface (không inject AdminTokenServiceImpl): đã tạo JWT và lưu access/refresh token vào Redis
    private final AdminTokenService adminTokenService;

    public LoginOutcome login(AdminLoginDTO req, String ip, String userAgent) {
        if (req == null || req.getAdminId() == null || req.getPassword() == null) {
            return LoginOutcome.of(LoginStatus.BAD_CREDENTIALS);
        }
        Long adminId = req.getAdminId();

        // (0) Đã sai mật khẩu quá nhiều lần thì từ chối ngay, KHÔNG chạy BCrypt.
        //     Đặt trước mọi thứ khác để mỗi lần thử bị khoá không tốn 100ms băm — nếu không, kẻ
        //     tấn công vẫn ép được server băm liên tục (khuếch đại tải) dù biết chắc sẽ bị chặn.
        //     Redis hỏng -> bỏ qua chặn (đăng nhập được như trước), không biến sự cố Redis thành
        //     sự cố đăng nhập toàn hệ thống.
        String pwdFailKey = PASSWORD_FAIL_KEY + adminId;
        int pwdFails = readFailCount(pwdFailKey);
        if (pwdFails >= MAX_PASSWORD_FAILS) {
            Long ttl = redis.getExpire(pwdFailKey);
            return LoginOutcome.locked((ttl == null || ttl < 1) ? PASSWORD_FAIL_WINDOW.toSeconds() : ttl);
        }

        // (a) Mật khẩu BCrypt. Cột password trong DB ĐÃ là hash BCrypt, so khớp thẳng với mật khẩu người dùng nhập
        //     (không được băm lại hash đó). Admin không tồn tại: matches() vẫn chạy 1 lần BCrypt giả
        //     để thời gian phản hồi không lộ tài khoản có tồn tại hay không.
        Admin admin = adminRepository.findAdminByAdminId(adminId);
        String storedHash = (admin == null) ? null : admin.getPassword();
        boolean passwordOk = mgmtPassword.matches(req.getPassword(), storedHash); // luôn chạy, kể cả admin = null
        if (admin == null || !passwordOk) {
            recordPasswordFailure(pwdFailKey);
            return LoginOutcome.of(LoginStatus.BAD_CREDENTIALS);
        }
        // Mật khẩu đúng -> xoá bộ đếm của admin này, để lần sai rải rác trong nhiều ngày không
        // cộng dồn thành khoá oan.
        clearFailCount(pwdFailKey);

        // (b) Chỉ credential ĐÃ xác nhận bên mobile mới tính là bật 2FA (pending không tính).
        //     - Chưa bật 2FA: bỏ qua ô mã 6 số (kể cả khi có gửi lên) -> vào luôn.
        //     - Đã bật 2FA: BẮT BUỘC có mã 6 số. Chỉ trả TOTP_REQUIRED sau khi mật khẩu đã đúng.
        Optional<TotpCredential> credOpt = totpCredentialRepository
                .findFirstByAdmin_AdminIdAndConfirmedTrueOrderByConfirmedAtDesc(adminId);
        if (credOpt.isEmpty()) {
            return issueTokens(admin);
        }

        String code = req.getTotpCode() == null ? null : req.getTotpCode().trim();
        if (code == null || code.isEmpty()) {
            return LoginOutcome.of(LoginStatus.TOTP_REQUIRED);
        }

        // (c) Khóa tạm khi nhập sai mã nhiều lần. Chỉ tính SAU khi mật khẩu đã đúng,
        //     nên người không biết mật khẩu không thể cố ý khóa tài khoản của admin.
        String failKey = TOTP_FAIL_KEY + adminId;
        String failStr = redis.opsForValue().get(failKey);
        int fails = (failStr == null) ? 0 : Integer.parseInt(failStr);
        if (fails >= MAX_TOTP_FAILS) {
            Long ttl = redis.getExpire(failKey);
            return LoginOutcome.locked((ttl == null || ttl < 1) ? TOTP_FAIL_WINDOW.toSeconds() : ttl);
        }

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
            redis.delete(failKey);
            // offset: -1 = mã chu kỳ trước (POST tới trễ), 0 = đúng chu kỳ, +1 = app chạy nhanh. Dùng để thống kê độ trễ cho luận văn.
            auditService.log(adminId, AuthEvent.TOTP_LOGIN_OK, true, ip, userAgent,
                    "offset=" + outcome.matchedOffset());
            return issueTokens(admin);
        }

        Long count = redis.opsForValue().increment(failKey);
        if (count != null && count == 1L) {
            redis.expire(failKey, TOTP_FAIL_WINDOW);
        }
        // Không ghi mã người dùng nhập vào log
        auditService.log(adminId, AuthEvent.TOTP_LOGIN_FAILED, false, ip, userAgent,
                outcome.result() == TotpLoginVerifier.Result.REPLAYED ? "replayed code" : "wrong code");

        // Lần sai thứ MAX_TOTP_FAILS: khóa NGAY (không đợi lần thử thứ MAX + 1) để web khóa form và đếm ngược.
        // Đặt lại TTL đúng 5 phút kể từ lần sai cuối, nên thời gian khóa luôn đủ 5 phút
        // (nếu giữ TTL tính từ lần sai đầu tiên thì có thể chỉ còn vài giây).
        if (count != null && count >= MAX_TOTP_FAILS) {
            redis.expire(failKey, TOTP_FAIL_WINDOW);
            return LoginOutcome.locked(TOTP_FAIL_WINDOW.toSeconds());
        }
        // INVALID và REPLAYED trả chung một thông báo để không lộ thêm thông tin
        return LoginOutcome.of(LoginStatus.TOTP_INVALID);
    }

    /** Đọc số lần sai; Redis lỗi trả 0 để không chặn nhầm người dùng hợp lệ. */
    private int readFailCount(String key) {
        try {
            String raw = redis.opsForValue().get(key);
            return raw == null ? 0 : Integer.parseInt(raw);
        } catch (Exception e) {
            log.warn("Không đọc được bộ đếm {} trên Redis: {}", key, e.getMessage());
            return 0;
        }
    }

    /**
     * Tăng bộ đếm và đặt TTL. TTL được đặt lại mỗi lần sai (giống khoá TOTP) để cửa sổ 15 phút
     * luôn tính từ lần sai cuối — nếu tính từ lần sai đầu, người dùng có thể bị mở khoá sớm hơn
     * dự kiến ngay sau lần sai thứ 10.
     */
    private void recordPasswordFailure(String key) {
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null) {
                redis.expire(key, PASSWORD_FAIL_WINDOW);
            }
        } catch (Exception e) {
            log.warn("Không ghi được bộ đếm {} trên Redis: {}", key, e.getMessage());
        }
    }

    private void clearFailCount(String key) {
        try {
            redis.delete(key);
        } catch (Exception e) {
            log.warn("Không xoá được bộ đếm {} trên Redis: {}", key, e.getMessage());
        }
    }

    private LoginOutcome issueTokens(Admin admin) {
        // createTokenAdmin: sinh access + refresh token, lưu Redis; trả map rỗng nếu không tạo được
        Map<String, Object> tokens = adminTokenService.createTokenAdmin(admin.getAdminId());
        Object accessToken = tokens.get("accessToken");
        Object refreshToken = tokens.get("refreshToken");
        if (accessToken == null || refreshToken == null) {
            throw new IllegalStateException("Không tạo được token cho admin " + admin.getAdminId());
        }
        // Ghi mốc đăng nhập cuối (chỉ bước đăng nhập tương tác mới ghi).
        // Best-effort: lỗi ghi không được làm hỏng luồng đăng nhập đã thành công.
        try {
            admin.setLastLoginAt(Instant.now());
            adminRepository.save(admin);
        } catch (Exception e) {
            log.warn("Can't record lastLoginAt of admin {}: {}", admin.getAdminId(), e.getMessage());
        }
        return LoginOutcome.ok(String.valueOf(accessToken), String.valueOf(refreshToken));
    }
}
