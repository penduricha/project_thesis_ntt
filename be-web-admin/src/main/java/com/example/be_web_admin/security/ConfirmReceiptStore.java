package com.example.be_web_admin.security;

import com.example.be_web_admin.security.totp.TotpVerifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/** Experimental receipt storage. Call only inside the same DB transaction as 2FA confirmation. */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class ConfirmReceiptStore {
    public record Fingerprint(byte[] requestHash, byte[] payloadMac) {}
    public record Receipt(long credentialId, String sessionId, byte[] payloadMac, Instant expiresAt) {}
    private final JdbcTemplate jdbc;
    public ConfirmReceiptStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Request IDs are 32 securely generated random bytes encoded as lowercase hex. */
    public static byte[] requestHash(String requestId) {
        if (requestId == null || !requestId.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid request ID");
        try {
            return MessageDigest.getInstance("SHA-256").digest(HexFormat.of().parseHex(requestId));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Receipt fingerprint unavailable", e);
        }
    }

    /**
     * HMAC is keyed by the credential's decrypted high-entropy TOTP secret, not by the
     * six-digit OTP or client-supplied request ID. A DB-only leak cannot brute force the
     * OTP even if a client chose a weak request ID. The raw OTP/secret are never stored.
     * Domain separation prevents treating this MAC as a TOTP. This is NOT sender binding.
     */
    public static Fingerprint fingerprint(String secret, Long adminId, Long credentialId,
                                          String sessionId, String requestId, String code) {
        if (adminId == null || adminId <= 0 || credentialId == null || credentialId <= 0 ||
                !EnrollmentGenerationStore.validGeneration(sessionId) || code == null || !code.matches("[0-9]{6}")) {
            throw new IllegalArgumentException("Invalid receipt request");
        }
        byte[] hash = requestHash(requestId);
        byte[] key = TotpVerifier.base32Decode(secret);
        try {
            if (key.length < 16) throw new IllegalArgumentException("Invalid TOTP secret length");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            String payload = "totp-confirm-v2\n" + adminId + "\n" + credentialId + "\n" + sessionId + "\n" + requestId + "\n" + code;
            return new Fingerprint(hash, mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Receipt fingerprint unavailable", e);
        } finally {
            java.util.Arrays.fill(key, (byte) 0);
        }
    }

    /** Caller locks the admin row before this locking read. */
    public Receipt find(Long adminId, byte[] requestHash) {
        List<Receipt> rows = jdbc.query("SELECT credential_id, session_id, payload_mac, expires_at " +
                        "FROM ntt_totp_confirm_receipts WHERE admin_id = ? AND request_hash = ? FOR UPDATE",
                (rs, n) -> new Receipt(rs.getLong("credential_id"), rs.getString("session_id"),
                        rs.getBytes("payload_mac"), rs.getTimestamp("expires_at").toInstant()), adminId, requestHash);
        if (rows.size() > 1) throw new IllegalStateException("Duplicate receipt");
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void insert(Long adminId, Long credentialId, String sessionId, Fingerprint fp, Instant now, Instant expiresAt) {
        if (jdbc.update("INSERT INTO ntt_totp_confirm_receipts " +
                        "(admin_id, credential_id, session_id, request_hash, payload_mac, created_at, expires_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)", adminId, credentialId, sessionId,
                fp.requestHash(), fp.payloadMac(), Timestamp.from(now), Timestamp.from(expiresAt)) != 1) {
            throw new IllegalStateException("Cannot persist confirmation receipt");
        }
    }
}
