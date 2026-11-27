package com.example.be_web_admin.security;

import com.example.be_web_admin.chat.ws.ChatSessionRegistry;
import com.example.be_web_admin.security.jwt.user.AdminJwtTokenProvider;
import com.example.be_web_admin.service.redis.AdminTokenService;
import org.springframework.stereotype.Service;

/** Logout requires a live token belonging to the exact client/session being revoked. */
@Service
public class SessionLogoutService {
    private final AdminJwtTokenProvider jwt;
    private final AdminTokenService tokens;
    private final AuthSessionStore sessions;
    private final ChatSessionRegistry sockets;

    public SessionLogoutService(AdminJwtTokenProvider jwt, AdminTokenService tokens,
                                AuthSessionStore sessions, ChatSessionRegistry sockets) {
        this.jwt = jwt; this.tokens = tokens; this.sessions = sessions; this.sockets = sockets;
    }

    public boolean logout(String access, String refresh, AuthSessionStore.Client client) {
        AuthSessionStore.Identity a = jwt.parseIdentity(access, "ACCESS");
        AuthSessionStore.Identity r = jwt.parseIdentity(refresh, "REFRESH");
        if (a != null && r != null && !a.equals(r)) return false;
        AuthSessionStore.Identity identity = a != null && tokens.findAdminAccessTokenByJwtToken(access) ? a
                : r != null && tokens.findAdminRefreshTokenByJwtToken(refresh) ? r : null;
        if (identity == null || identity.client() != client || !sessions.revoke(identity)) return false;
        sockets.closeSession(identity);
        // Session removal is authoritative; token records expire naturally if cleanup fails.
        try { if (access != null) tokens.removeAdminAccessTokenByJwtToken(access); } catch (RuntimeException ignored) { }
        try { if (refresh != null) tokens.removeAdminRefreshTokenByJwtToken(refresh); } catch (RuntimeException ignored) { }
        return true;
    }
}
