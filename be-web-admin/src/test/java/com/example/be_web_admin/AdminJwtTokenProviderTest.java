package com.example.be_web_admin;

import com.example.be_web_admin.security.AuthSessionStore;
import com.example.be_web_admin.security.JwtSecretGuard;
import com.example.be_web_admin.security.jwt.user.AdminJwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminJwtTokenProviderTest {

    private static final String VALID_TEST_SECRET = "TestOnlySecretKeyForJwtSigning_AtLeast32BytesLong_2026";

    @Test
    void rejectsShortConfiguredSecret() {
        assertThrows(IllegalStateException.class, () -> new AdminJwtTokenProvider("too-short"));
    }

    @Test
    void separatesAccessAndRefreshTokenTypes() {
        AdminJwtTokenProvider provider = new AdminJwtTokenProvider(VALID_TEST_SECRET);

        String accessToken = provider.generateAccessToken(2500021775L);
        String refreshToken = provider.generateRefreshToken(2500021775L);

        assertTrue(provider.validateAccessToken(accessToken));
        assertFalse(provider.validateRefreshToken(accessToken));
        assertEquals(2500021775L, provider.parseAccessTokenAdminId(accessToken));
        assertNull(provider.parseRefreshTokenAdminId(accessToken));

        assertTrue(provider.validateRefreshToken(refreshToken));
        assertFalse(provider.validateAccessToken(refreshToken));
        assertEquals(2500021775L, provider.parseRefreshTokenAdminId(refreshToken));
        assertNull(provider.parseAccessTokenAdminId(refreshToken));
    }

    @Test
    void accessAndRefreshCarryTheSameSessionAndLegacyTokensHaveNoIdentity() {
        AdminJwtTokenProvider provider = new AdminJwtTokenProvider(VALID_TEST_SECRET);
        AuthSessionStore.Identity identity = new AuthSessionStore.Identity(7L, AuthSessionStore.Client.MOBILE, "00000000-0000-0000-0000-000000000007");
        String access = provider.generateAccessToken(7L, identity);
        String refresh = provider.generateRefreshToken(7L, identity);
        assertEquals(identity, provider.parseIdentity(access, "ACCESS"));
        assertEquals(identity, provider.parseIdentity(refresh, "REFRESH"));
        assertNull(provider.parseIdentity(access, "REFRESH"));
        assertNull(provider.parseIdentity(provider.generateAccessToken(7L), "ACCESS"));
    }

    @Test
    void guardFailsOutsideDevProfileWhenSecretBlank() {
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");
        AdminJwtTokenProvider provider = new AdminJwtTokenProvider("", prodEnv);
        JwtSecretGuard guard = new JwtSecretGuard(provider, prodEnv);

        assertThrows(IllegalStateException.class, guard::validateOnStartup);
    }

    @Test
    void guardAllowsBlankSecretInDevProfileWithEphemeralKey() {
        MockEnvironment devEnv = new MockEnvironment();
        devEnv.setActiveProfiles("dev");
        AdminJwtTokenProvider provider = new AdminJwtTokenProvider("", devEnv);
        JwtSecretGuard guard = new JwtSecretGuard(provider, devEnv);

        assertTrue(provider.isUsingEphemeralDevKey());
        guard.validateOnStartup();
    }
}
