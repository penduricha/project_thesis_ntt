package com.example.be_web_admin.security;

import com.example.be_web_admin.chat.ws.ChatConnection;
import com.example.be_web_admin.chat.ws.ChatSessionRegistry;
import com.example.be_web_admin.chat.ChatProperties;
import com.example.be_web_admin.security.jwt.user.AdminJwtTokenProvider;
import com.example.be_web_admin.service.redis.AdminTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.WebSocketSession;

import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SessionSecurityTest {
    private final AuthSessionStore.Identity mobile = new AuthSessionStore.Identity(7L, AuthSessionStore.Client.MOBILE, "00000000-0000-0000-0000-000000000007");
    private final AuthSessionStore.Identity web = new AuthSessionStore.Identity(7L, AuthSessionStore.Client.WEB, "00000000-0000-0000-0000-000000000008");

    @Test
    void logoutRejectsMixedSessionsAndRevokesOnlyMatchingSession() {
        AdminJwtTokenProvider jwt = mock(AdminJwtTokenProvider.class);
        AdminTokenService tokens = mock(AdminTokenService.class);
        AuthSessionStore sessions = mock(AuthSessionStore.class);
        ChatSessionRegistry sockets = mock(ChatSessionRegistry.class);
        SessionLogoutService logout = new SessionLogoutService(jwt, tokens, sessions, sockets);
        when(jwt.parseIdentity("access", "ACCESS")).thenReturn(mobile);
        when(jwt.parseIdentity("other", "REFRESH")).thenReturn(web);
        assertFalse(logout.logout("access", "other", AuthSessionStore.Client.MOBILE));
        verifyNoInteractions(sessions, sockets);
        when(tokens.findAdminAccessTokenByJwtToken("access")).thenReturn(true);
        assertFalse(logout.logout("access", null, AuthSessionStore.Client.WEB));
        when(sessions.revoke(mobile)).thenReturn(true);
        assertTrue(logout.logout("access", null, AuthSessionStore.Client.MOBILE));
        verify(sessions).revoke(mobile);
        verify(sockets).closeSession(mobile);
    }

    @Test
    void retryAfterConfirmedRevokeDoesNotRequireTokenRecordsAndDoesNotTouchOtherSessions() {
        AdminJwtTokenProvider jwt = mock(AdminJwtTokenProvider.class);
        AdminTokenService tokens = mock(AdminTokenService.class);
        AuthSessionStore sessions = mock(AuthSessionStore.class);
        ChatSessionRegistry sockets = mock(ChatSessionRegistry.class);
        SessionLogoutService logout = new SessionLogoutService(jwt, tokens, sessions, sockets);
        when(jwt.parseIdentity("refresh", "REFRESH")).thenReturn(mobile);
        when(sessions.wasRevoked(mobile)).thenReturn(true);
        assertTrue(logout.logout(null, "refresh", AuthSessionStore.Client.MOBILE));
        verifyNoInteractions(tokens, sockets);
        assertFalse(logout.logout(null, "refresh", AuthSessionStore.Client.WEB));
        verify(sessions, never()).revoke(any());
    }

    @Test
    void outboundSocketRejectsRevokedSession() throws Exception {
        AuthSessionStore sessions = mock(AuthSessionStore.class);
        ChatSessionRegistry registry = new ChatSessionRegistry(new ChatProperties(), sessions);
        WebSocketSession raw = mock(WebSocketSession.class);
        when(raw.isOpen()).thenReturn(true);
        ChatConnection connection = new ChatConnection(mobile, raw);
        assertTrue(registry.register(connection));
        when(sessions.state(mobile)).thenReturn(AuthSessionStore.State.REVOKED);
        registry.sendTo(connection, "sensitive");
        verify(raw, never()).sendMessage(any());
        verify(raw).close(ChatSessionRegistry.SESSION_REVOKED);
        assertEquals(0, registry.count(7L));
    }

    @Test
    void socketClosesWithRetryableErrorWhenSessionStoreIsUnavailable() throws Exception {
        AuthSessionStore sessions = mock(AuthSessionStore.class);
        ChatSessionRegistry registry = new ChatSessionRegistry(new ChatProperties(), sessions);
        WebSocketSession raw = mock(WebSocketSession.class);
        when(raw.isOpen()).thenReturn(true);
        ChatConnection connection = new ChatConnection(mobile, raw);
        assertTrue(registry.register(connection));
        when(sessions.state(mobile)).thenReturn(AuthSessionStore.State.UNAVAILABLE);
        registry.sendTo(connection, "sensitive");
        verify(raw, never()).sendMessage(any());
        verify(raw).close(org.springframework.web.socket.CloseStatus.SERVER_ERROR);
    }

    @Test
    void limiterFailsClosedWhenRedisCannotReserve() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        // A missing Redis result must not be treated as an allowed attempt.
        AuthenticationRateLimiter limiter = new AuthenticationRateLimiter(redis);
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> limiter.reserve("password", 7L, 10, Duration.ofMinutes(15)));
        assertEquals(503, e.getStatusCode().value());
    }
}
