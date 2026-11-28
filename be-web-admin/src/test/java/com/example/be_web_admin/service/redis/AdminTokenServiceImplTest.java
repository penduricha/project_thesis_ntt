package com.example.be_web_admin.service.redis;

import com.example.be_web_admin.security.AuthSessionStore;
import com.example.be_web_admin.enums.AccountStatus;
import com.example.be_web_admin.model.jpa.Admin;
import com.example.be_web_admin.model.redis.AdminRefreshToken;
import com.example.be_web_admin.repository.jpa.AdminRepository;
import com.example.be_web_admin.repository.redis.AdminAccessTokenRepository;
import com.example.be_web_admin.repository.redis.AdminRefreshTokenRepository;
import com.example.be_web_admin.security.jwt.user.AdminJwtTokenProvider;
import com.example.be_web_admin.service.redis.impl.AdminTokenServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminTokenServiceImplTest {
    @Test
    void refreshConsumptionUsesOneDistributedClaim() {
        AdminRefreshTokenRepository refresh = mock(AdminRefreshTokenRepository.class);
        AdminJwtTokenProvider provider = mock(AdminJwtTokenProvider.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(provider.parseRefreshTokenAdminId("token")).thenReturn(7L);
        AdminRefreshToken stored = new AdminRefreshToken();
        when(refresh.findAdminRefreshTokenByJwtToken("token")).thenReturn(stored);
        when(values.setIfAbsent(anyString(), eq("1"), eq(Duration.ofDays(31)))).thenReturn(true, false);
        AdminTokenServiceImpl service = new AdminTokenServiceImpl(mock(AdminAccessTokenRepository.class),
                refresh, mock(AdminRepository.class), provider, redis, mock(AuthSessionStore.class));
        assertTrue(service.removeAdminRefreshTokenByJwtToken("token"));
        assertFalse(service.removeAdminRefreshTokenByJwtToken("token"));
        verify(refresh, times(1)).delete(stored);
    }

    @Test
    void disabledAccountCannotUseStoredRefreshToken() {
        AdminRefreshTokenRepository refresh = mock(AdminRefreshTokenRepository.class);
        AdminRepository admins = mock(AdminRepository.class);
        AdminJwtTokenProvider provider = mock(AdminJwtTokenProvider.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(refresh.findAdminRefreshTokenByJwtToken("token")).thenReturn(new AdminRefreshToken());
        AuthSessionStore.Identity identity = new AuthSessionStore.Identity(7L, AuthSessionStore.Client.WEB, "00000000-0000-0000-0000-000000000007");
        when(provider.parseIdentity("token", "REFRESH")).thenReturn(identity);
        AuthSessionStore sessions = mock(AuthSessionStore.class);
        when(sessions.isActive(identity)).thenReturn(true);
        Admin admin = new Admin(); admin.setAccountStatus(AccountStatus.DISABLED);
        when(admins.findAdminByAdminId(7L)).thenReturn(admin);
        AdminTokenServiceImpl service = new AdminTokenServiceImpl(mock(AdminAccessTokenRepository.class), refresh, admins, provider, redis, sessions);
        assertFalse(service.findAdminRefreshTokenByJwtToken("token"));
        admin.setAccountStatus(AccountStatus.ACTIVE);
        assertTrue(service.findAdminRefreshTokenByJwtToken("token"));
    }
}
