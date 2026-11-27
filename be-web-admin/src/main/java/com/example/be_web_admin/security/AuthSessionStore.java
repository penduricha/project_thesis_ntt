package com.example.be_web_admin.security;

import com.example.be_web_admin.model.jpa.Admin;
import com.example.be_web_admin.model.redis.SessionLoginMobile;
import com.example.be_web_admin.repository.jpa.AdminRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Server-side session is authoritative for every token and WebSocket, across instances. */
@Service
public class AuthSessionStore {
    public enum Client { WEB, MOBILE }
    public record Identity(Long adminId, Client client, String sessionId) { }
    public enum State { ACTIVE, REVOKED, UNAVAILABLE }
    private static final String TTL = "2592000"; // 30 days, matches refresh-token lifetime
    // All three keys use the same Redis Cluster hash tag (the admin ID).
    private static final DefaultRedisScript<Long> OPEN = new DefaultRedisScript<>(
            "if ARGV[1]=='MOBILE' and redis.call('EXISTS',KEYS[2])==1 then return 0 end " +
            "if redis.call('EXISTS',KEYS[1])==1 then return 0 end " +
            "redis.call('SET',KEYS[1],ARGV[1],'EX',ARGV[3]) " +
            "if ARGV[1]=='MOBILE' then redis.call('SET',KEYS[2],ARGV[2],'EX',ARGV[3]); " +
            "redis.call('SET',KEYS[3],ARGV[4],'EX',ARGV[3]) end return 1", Long.class);
    private static final DefaultRedisScript<Long> ACTIVE = new DefaultRedisScript<>(
            "if redis.call('GET',KEYS[1]) ~= ARGV[1] then return 0 end " +
            "if ARGV[1]=='MOBILE' and redis.call('GET',KEYS[2]) ~= ARGV[2] then return 0 end return 1", Long.class);
    private static final DefaultRedisScript<Long> TOUCH = new DefaultRedisScript<>(
            "if redis.call('GET',KEYS[1]) ~= ARGV[1] then return 0 end " +
            "if ARGV[1]=='MOBILE' and redis.call('GET',KEYS[2]) ~= ARGV[2] then return 0 end " +
            "redis.call('EXPIRE',KEYS[1],ARGV[3]); if ARGV[1]=='MOBILE' then " +
            "redis.call('EXPIRE',KEYS[2],ARGV[3]); redis.call('EXPIRE',KEYS[3],ARGV[3]) end return 1", Long.class);
    private static final DefaultRedisScript<Long> REVOKE = new DefaultRedisScript<>(
            "if redis.call('GET',KEYS[1]) ~= ARGV[1] then return 0 end " +
            "redis.call('DEL',KEYS[1]); if ARGV[1]=='MOBILE' and redis.call('GET',KEYS[2])==ARGV[2] " +
            "then redis.call('DEL',KEYS[2],KEYS[3]) end return 1", Long.class);

    private final StringRedisTemplate redis;
    private final AdminRepository admins;
    private final ObjectMapper json;

    public AuthSessionStore(StringRedisTemplate redis, AdminRepository admins, ObjectMapper json) {
        this.redis = redis; this.admins = admins; this.json = json;
    }

    public Optional<Identity> open(Long adminId, Client client, String device, String platform, String ip) {
        Identity id = new Identity(adminId, client, UUID.randomUUID().toString());
        Instant now = Instant.now();
        SessionLoginMobile info = new SessionLoginMobile(adminId, truncate(device, 100), truncate(platform, 20),
                truncate(ip, 100), now, now);
        String details;
        try { details = json.writeValueAsString(info); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot encode session metadata", e); }
        Long created = redis.execute(OPEN, keys(id), client.name(), id.sessionId(), TTL, details);
        if (created == null) throw new IllegalStateException("Cannot claim authentication session");
        return created == 1L ? Optional.of(id) : Optional.empty();
    }

    public Optional<SessionLoginMobile> mobileInfo(Long adminId) {
        String value = redis.opsForValue().get(infoKey(adminId));
        if (value == null) return Optional.empty();
        try { return Optional.of(json.readValue(value, SessionLoginMobile.class)); }
        catch (JsonProcessingException e) { return Optional.empty(); }
    }

    /** Throws on Redis failure; callers must not treat unavailable as authorized. */
    public boolean isActive(Identity id) {
        if (!valid(id)) return false;
        return accepted(redis.execute(ACTIVE, keys(id), id.client().name(), id.sessionId()));
    }

    public boolean touch(Identity id) {
        if (!valid(id)) return false;
        return accepted(redis.execute(TOUCH, keys(id), id.client().name(), id.sessionId(), TTL));
    }

    public boolean revoke(Identity id) {
        if (!valid(id)) return false;
        return accepted(redis.execute(REVOKE, keys(id), id.client().name(), id.sessionId()));
    }

    /** Used on WebSocket sends/sweeps: account lock and session revocation apply after handshake. */
    public State state(Identity id) {
        try {
            if (!isActive(id)) return State.REVOKED;
            Admin admin = admins.findAdminByAdminId(id.adminId());
            return admin != null && admin.isLoginAllowed() ? State.ACTIVE : State.REVOKED;
        } catch (RuntimeException e) { return State.UNAVAILABLE; }
    }

    private static boolean accepted(Long result) {
        if (result == null) throw new IllegalStateException("Session store unavailable");
        return result == 1L;
    }
    private static boolean valid(Identity id) {
        return id != null && id.adminId() != null && id.client() != null && id.sessionId() != null
                && id.sessionId().matches("[0-9a-fA-F-]{36}");
    }
    private static List<String> keys(Identity id) {
        return List.of("auth:session:{" + id.adminId() + "}:" + id.sessionId(),
                "auth:mobile:{" + id.adminId() + "}", infoKey(id.adminId()));
    }
    private static String infoKey(Long adminId) { return "auth:mobile-info:{" + adminId + "}"; }
    private static String truncate(String s, int max) { return s == null ? null : s.substring(0, Math.min(s.length(), max)); }
}
