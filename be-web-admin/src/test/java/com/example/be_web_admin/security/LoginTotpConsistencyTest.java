package com.example.be_web_admin.security;

import com.example.be_web_admin.dto.AdminLoginDTO;
import com.example.be_web_admin.enums.LoginStatus;
import com.example.be_web_admin.model.jpa.Admin;
import com.example.be_web_admin.model.jpa.TotpCredential;
import com.example.be_web_admin.repository.jpa.AdminRepository;
import com.example.be_web_admin.repository.jpa.TotpCredentialRepository;
import com.example.be_web_admin.security.password.MgmtPassword;
import com.example.be_web_admin.security.private_key.CryptoService;
import com.example.be_web_admin.security.totp.TotpLoginVerifier;
import com.example.be_web_admin.service.jpa.impl.AdminLoginServiceImpl;
import com.example.be_web_admin.service.jpa.impl.AuthAuditService;
import com.example.be_web_admin.service.redis.AdminTokenService;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LoginTotpConsistencyTest {
    private final AdminRepository admins = mock(AdminRepository.class);
    private final TotpCredentialRepository credentials = mock(TotpCredentialRepository.class);
    private final MgmtPassword passwords = mock(MgmtPassword.class);
    private final AuthenticationRateLimiter limiter = mock(AuthenticationRateLimiter.class);
    private final AuthSessionStore sessions = mock(AuthSessionStore.class);
    private final AdminTokenService tokens = mock(AdminTokenService.class);
    private final AdminLoginServiceImpl login = new AdminLoginServiceImpl(admins, credentials, passwords,
            mock(TotpLoginVerifier.class), mock(CryptoService.class), mock(AuthAuditService.class),
            limiter, sessions, tokens);
    private final AdminLoginDTO request = new AdminLoginDTO(7L, "password");

    private void account(Boolean enabled, Optional<TotpCredential> credential) {
        Admin admin = new Admin();
        admin.setAdminId(7L);
        admin.setPassword("hash");
        admin.setTotpEnabled(enabled);
        when(admins.findAdminByAdminId(7L)).thenReturn(admin);
        when(passwords.matches("password", "hash")).thenReturn(true);
        when(credentials.findFirstByAdmin_AdminIdAndConfirmedTrueOrderByConfirmedAtDesc(7L))
                .thenReturn(credential);
    }

    @Test
    void missingCredentialWithEnabledOrUnknownFlagNeverOpensEitherSession() {
        for (Boolean flag : new Boolean[]{true, null}) {
            account(flag, Optional.empty());
            assertEquals(LoginStatus.BAD_CREDENTIALS, login.login(request, "peer", "ua").status());
            assertEquals(LoginStatus.BAD_CREDENTIALS, login.loginMobile(request, "peer", "ua", "device", "ANDROID").status());
        }
        verifyNoInteractions(sessions, tokens);
    }

    @Test
    void confirmedCredentialRequiresTotpEvenWhenFlagIsFalseOrUnknown() {
        for (Boolean flag : new Boolean[]{false, null}) {
            account(flag, Optional.of(new TotpCredential()));
            assertEquals(LoginStatus.TOTP_REQUIRED, login.login(request, "peer", "ua").status());
            assertEquals(LoginStatus.TOTP_REQUIRED, login.loginMobile(request, "peer", "ua", "device", "ANDROID").status());
        }
        verifyNoInteractions(sessions, tokens);
    }

    @Test
    void explicitlyDisabledWithoutConfirmedCredentialStillAllowsPasswordLogin() {
        account(false, Optional.empty());
        AuthSessionStore.Identity identity = new AuthSessionStore.Identity(7L, AuthSessionStore.Client.WEB, "00000000-0000-0000-0000-000000000007");
        when(sessions.open(eq(7L), eq(AuthSessionStore.Client.WEB), isNull(), isNull(), eq("peer")))
                .thenReturn(Optional.of(identity));
        when(tokens.createTokenAdmin(identity)).thenReturn(Map.of("accessToken", "access", "refreshToken", "refresh"));
        assertEquals(LoginStatus.OK, login.login(request, "peer", "ua").status());
    }
}
