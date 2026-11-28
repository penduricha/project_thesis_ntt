package com.example.be_web_admin.security.recovery;

import com.example.be_web_admin.model.jpa.Admin;
import com.example.be_web_admin.model.jpa.TotpCredential;
import com.example.be_web_admin.repository.jpa.AdminRepository;
import com.example.be_web_admin.repository.jpa.TotpCredentialRepository;
import com.example.be_web_admin.security.AuthenticationRateLimiter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Internal persistence primitive only. No HTTP endpoint currently invokes this service.
 * Callers must authenticate, rate-limit, bind requests to the correct account/session and
 * implement safe one-time display and explicit acknowledgement before enabling recovery.
 */
@Service
public class RecoveryCodeStore {
    private static final int CODE_COUNT = 10;
    private static final int RANDOM_BYTES = 20; // 160-bit random bearer codes
    private static final Duration PENDING_TTL = Duration.ofMinutes(15);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final AdminRepository admins;
    private final TotpCredentialRepository credentials;
    private final AuthenticationRateLimiter limiter;

    public RecoveryCodeStore(JdbcTemplate jdbc, AdminRepository admins, TotpCredentialRepository credentials,
                             AuthenticationRateLimiter limiter) {
        this.jdbc = jdbc;
        this.admins = admins;
        this.credentials = credentials;
        this.limiter = limiter;
    }

    public static class RecoveryRateLimitException extends RuntimeException {
        private final long retryAfterSeconds;
        public RecoveryRateLimitException(long retryAfterSeconds) { this.retryAfterSeconds = retryAfterSeconds; }
        public long retryAfterSeconds() { return retryAfterSeconds; }
    }

    private void reserve(String scope, Long adminId, int max, Duration duration) {
        if (adminId == null) throw new IllegalArgumentException("Invalid account");
        long retry = limiter.reserve(scope, adminId, max, duration);
        if (retry > 0) throw new RecoveryRateLimitException(retry);
    }

    /** Never include the one-time codes in a log, exception, audit detail, or JSON by accident. */
    public static final class PreparedSet {
        private final String setId;
        private final List<String> codes;
        private final Instant expiresAt;
        PreparedSet(String setId, List<String> codes, Instant expiresAt) {
            this.setId = setId;
            this.codes = List.copyOf(codes);
            this.expiresAt = expiresAt;
        }
        public String setId() { return setId; }
        public List<String> codes() { return codes; }
        public Instant expiresAt() { return expiresAt; }
        @Override public String toString() { return "PreparedSet[redacted]"; }
    }

    /** Creates a pending set without invalidating any previously active set. */
    @Transactional
    public PreparedSet prepare(Long adminId, Long credentialId) {
        reserve("recovery-prepare", adminId, 5, Duration.ofHours(1));
        requireCurrentCredential(adminId, credentialId);
        String setId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant expires = now.plus(PENDING_TTL);
        jdbc.update("INSERT INTO ntt_totp_recovery_sets (set_id, admin_id, credential_id, state, created_at, expires_at) VALUES (?, ?, ?, 'PENDING', ?, ?)",
                setId, adminId, credentialId, Timestamp.from(now), Timestamp.from(expires));
        List<String> codes = new ArrayList<>(CODE_COUNT);
        for (int i = 0; i < CODE_COUNT; i++) {
            byte[] random = new byte[RANDOM_BYTES];
            RANDOM.nextBytes(random);
            String code = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
            codes.add(code);
            jdbc.update("INSERT INTO ntt_totp_recovery_codes (set_id, code_hash, used_at) VALUES (?, ?, NULL)", setId, hash(code));
        }
        return new PreparedSet(setId, codes, expires);
    }

    /** Acknowledgement must only be called after the user has saved the pending codes. */
    @Transactional
    public boolean activate(Long adminId, Long credentialId, String setId) {
        if (!validSetId(setId)) return false;
        requireCurrentCredential(adminId, credentialId);
        List<SetState> rows = jdbc.query(
                "SELECT state, created_at, expires_at FROM ntt_totp_recovery_sets WHERE set_id = ? AND admin_id = ? AND credential_id = ?",
                (rs, row) -> new SetState(rs.getString(1), rs.getTimestamp(2).toInstant(), rs.getTimestamp(3).toInstant()),
                setId, adminId, credentialId);
        if (rows.size() != 1) return false;
        SetState pending = rows.get(0);
        if ("ACTIVE".equals(pending.state())) return true;
        if (!"PENDING".equals(pending.state()) || !pending.expiresAt().isAfter(Instant.now())) return false;
        Integer newer = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ntt_totp_recovery_sets WHERE admin_id = ? AND state = 'ACTIVE' AND created_at >= ?",
                Integer.class, adminId, Timestamp.from(pending.createdAt()));
        if (newer == null || newer != 0) return false;
        jdbc.update("UPDATE ntt_totp_recovery_sets SET state = 'RETIRED' WHERE admin_id = ? AND state = 'ACTIVE'", adminId);
        int activated = jdbc.update("UPDATE ntt_totp_recovery_sets SET state = 'ACTIVE' WHERE set_id = ? AND admin_id = ? AND credential_id = ? AND state = 'PENDING' AND expires_at > ?",
                setId, adminId, credentialId, Timestamp.from(Instant.now()));
        if (activated != 1) throw new IllegalStateException("Recovery set activation changed concurrently");
        return true;
    }

    /** Atomically consume one active code. Redis failure denies the attempt. */
    @Transactional
    public boolean consume(Long adminId, Long credentialId, String code) {
        reserve("recovery-consume", adminId, 5, Duration.ofMinutes(10));
        if (code == null || !code.matches("[A-Za-z0-9_-]{27}")) return false;
        requireCurrentCredential(adminId, credentialId);
        Integer activeCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ntt_totp_recovery_sets WHERE admin_id = ? AND state = 'ACTIVE'",
                Integer.class, adminId);
        if (activeCount == null || activeCount != 1) return false;
        int changed = jdbc.update("UPDATE ntt_totp_recovery_codes c JOIN ntt_totp_recovery_sets s ON s.set_id = c.set_id " +
                        "SET c.used_at = ? WHERE s.admin_id = ? AND s.credential_id = ? AND s.state = 'ACTIVE' AND c.code_hash = ? AND c.used_at IS NULL",
                Timestamp.from(Instant.now()), adminId, credentialId, hash(code));
        if (changed > 1) throw new IllegalStateException("Multiple recovery code sets are active");
        return changed == 1;
    }

    private void requireCurrentCredential(Long adminId, Long credentialId) {
        if (adminId == null || credentialId == null || credentialId <= 0) throw new IllegalArgumentException("Invalid account or credential");
        Admin admin = admins.findForTotpUpdate(adminId);
        if (admin == null || !admin.isLoginAllowed() || !Boolean.TRUE.equals(admin.getTotpEnabled())) {
            throw new IllegalStateException("Account or 2FA is not active");
        }
        TotpCredential active = credentials.findFirstByAdmin_AdminIdAndConfirmedTrueOrderByConfirmedAtDesc(adminId).orElse(null);
        if (active == null || !credentialId.equals(active.getCredentialId())) {
            throw new IllegalStateException("Credential changed");
        }
    }

    private static boolean validSetId(String id) {
        if (id == null) return false;
        try { return UUID.fromString(id).toString().equals(id); } catch (IllegalArgumentException e) { return false; }
    }

    private static byte[] hash(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }

    private record SetState(String state, Instant createdAt, Instant expiresAt) { }
}
