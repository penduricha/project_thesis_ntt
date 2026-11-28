package com.example.be_web_admin.service.redis.impl;

import com.example.be_web_admin.config.TotpProperties;
import com.example.be_web_admin.enums.AuthEvent;
import com.example.be_web_admin.model.jpa.Admin;
import com.example.be_web_admin.model.jpa.TotpCredential;
import com.example.be_web_admin.repository.jpa.AdminRepository;
import com.example.be_web_admin.repository.jpa.TotpCredentialRepository;
import com.example.be_web_admin.security.AuthSessionStore;
import com.example.be_web_admin.security.AuthenticationRateLimiter;
import com.example.be_web_admin.security.ConfirmReceiptStore;
import com.example.be_web_admin.security.EnrollmentGenerationStore;
import com.example.be_web_admin.security.private_key.CryptoService;
import com.example.be_web_admin.security.totp.TotpVerifier;
import com.example.be_web_admin.service.jpa.impl.AuthAuditService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;

/** Experimental v2; controller is disabled by default until migration and integration tests. */
@Service
@ConditionalOnProperty(prefix = "app.totp", name = "idempotent-confirm-enabled", havingValue = "true")
public class IdempotentEnrollmentConfirmService {
    public enum Result { CREATED, REPLAYED, INVALID, LOCKED, EXPIRED, NOT_FOUND, CONFLICT, UNAUTHORIZED, RATE_LIMITED }
    private static final Duration RECEIPT_TTL = Duration.ofHours(24);
    private final AdminRepository admins;
    private final TotpCredentialRepository credentials;
    private final ConfirmReceiptStore receipts;
    private final EnrollmentConfirmServiceImpl legacy;
    private final TotpProperties properties;
    private final CryptoService crypto;
    private final AuthSessionStore sessions;
    private final AuthenticationRateLimiter limiter;
    private final TransactionTemplate tx;
    private final AuthAuditService audit;
    private final TwoFaEventServiceImpl events;

    public IdempotentEnrollmentConfirmService(AdminRepository admins, TotpCredentialRepository credentials,
            ConfirmReceiptStore receipts, EnrollmentConfirmServiceImpl legacy, TotpProperties properties,
            CryptoService crypto, AuthSessionStore sessions, AuthenticationRateLimiter limiter,
            TransactionTemplate tx, AuthAuditService audit, TwoFaEventServiceImpl events) {
        this.admins = admins; this.credentials = credentials; this.receipts = receipts; this.legacy = legacy;
        this.properties = properties; this.crypto = crypto; this.sessions = sessions; this.limiter = limiter;
        this.tx = tx; this.audit = audit; this.events = events;
    }

    public Result confirm(AuthSessionStore.Identity identity, Long credentialId, String requestId, String code,
                          String ip, String userAgent) {
        if (identity == null || identity.client() != AuthSessionStore.Client.MOBILE || identity.adminId() == null ||
                identity.adminId() <= 0 || !EnrollmentGenerationStore.validGeneration(identity.sessionId()) ||
                credentialId == null || credentialId <= 0 || code == null || !code.matches("[0-9]{6}")) {
            throw new IllegalArgumentException("Invalid confirmation request");
        }
        byte[] requestHash = ConfirmReceiptStore.requestHash(requestId);
        if (TransactionSynchronizationManager.isActualTransactionActive() ||
                tx.getPropagationBehavior() != TransactionDefinition.PROPAGATION_REQUIRED) {
            throw new IllegalStateException("Confirmation requires its own transaction");
        }
        if (limiter.reserve("totp-confirm-v2", identity.adminId(), 30, Duration.ofMinutes(1)) > 0) return Result.RATE_LIMITED;
        Result result = tx.execute(status -> confirmLocked(identity, credentialId, requestId, code, requestHash));
        if (result == null) throw new IllegalStateException("Confirmation transaction returned no result");
        if (result == Result.CREATED) {
            audit.log(identity.adminId(), AuthEvent.TOTP_ENROLL_CONFIRMED, true, ip, userAgent, null);
            events.publish(identity.adminId(), TwoFaEventServiceImpl.ENABLED);
        } else if (result == Result.LOCKED) {
            audit.log(identity.adminId(), AuthEvent.TOTP_ENROLL_CONFIRM_FAILED, false, ip, userAgent, "too many attempts");
            events.publish(identity.adminId(), TwoFaEventServiceImpl.NOT_ENABLED);
        } else if (result == Result.INVALID) {
            audit.log(identity.adminId(), AuthEvent.TOTP_ENROLL_CONFIRM_FAILED, false, ip, userAgent, null);
        }
        return result;
    }

    private Result confirmLocked(AuthSessionStore.Identity identity, Long credentialId, String requestId,
                                 String code, byte[] requestHash) {
        Long adminId = identity.adminId();
        Admin admin = admins.findForTotpUpdate(adminId);
        if (admin == null || !admin.isLoginAllowed() || !sessions.isActive(identity)) return Result.UNAUTHORIZED;
        ConfirmReceiptStore.Receipt receipt = receipts.find(adminId, requestHash);
        Instant now = Instant.now();
        if (receipt != null) {
            if (receipt.credentialId() != credentialId || !identity.sessionId().equals(receipt.sessionId()) ||
                    receipt.expiresAt() == null || !receipt.expiresAt().isAfter(now)) return Result.CONFLICT;
            if (!Boolean.TRUE.equals(admin.getTotpEnabled())) return Result.NOT_FOUND;
            TotpCredential current = credentials.findFirstByAdmin_AdminIdAndConfirmedTrueOrderByConfirmedAtDesc(adminId)
                    .filter(c -> credentialId.equals(c.getCredentialId())).orElse(null);
            if (current == null) return Result.NOT_FOUND;
            var fp = ConfirmReceiptStore.fingerprint(decrypt(current, adminId), adminId, credentialId,
                    identity.sessionId(), requestId, code);
            if (receipt.payloadMac() == null || !MessageDigest.isEqual(receipt.payloadMac(), fp.payloadMac())) return Result.CONFLICT;
            return Result.REPLAYED;
        }
        if (!Boolean.FALSE.equals(admin.getTotpEnabled()) || credentials
                .findFirstByAdmin_AdminIdAndConfirmedTrueOrderByConfirmedAtDesc(adminId).isPresent()) return Result.NOT_FOUND;
        TotpCredential pending = credentials.findFirstByAdmin_AdminIdAndConfirmedFalseOrderByCreatedAtDesc(adminId)
                .filter(c -> credentialId.equals(c.getCredentialId())).orElse(null);
        if (pending == null) return Result.NOT_FOUND;
        if (pending.getCreatedAt() == null) throw new IllegalStateException("Pending credential has no creation time");
        if (!pending.getCreatedAt().plus(legacy.getConfirmWindow()).isAfter(now)) return Result.EXPIRED;
        if (legacy.registerAttempt(adminId, credentialId) > properties.getConfirmMaxAttempts()) {
            if (credentials.deletePendingGeneration(adminId, credentialId) != 1) throw new IllegalStateException("Enrollment changed");
            return Result.LOCKED;
        }
        String secret = decrypt(pending, adminId);
        if (!TotpVerifier.verify(secret, code, pending.getPeriodSeconds(), pending.getDigits(), pending.getAlgorithm(), 1, now.getEpochSecond())) {
            return Result.INVALID;
        }
        var fp = ConfirmReceiptStore.fingerprint(secret, adminId, credentialId, identity.sessionId(), requestId, code);
        pending.setConfirmed(true);
        pending.setConfirmedAt(now);
        credentials.save(pending);
        admin.setTotpEnabled(true);
        admin.setUpdatedAt(now);
        admins.save(admin);
        // Receipt and enabling 2FA MUST commit or roll back together.
        receipts.insert(adminId, credentialId, identity.sessionId(), fp, now, now.plus(RECEIPT_TTL));
        return Result.CREATED;
    }

    private String decrypt(TotpCredential c, Long adminId) {
        return crypto.decrypt(c.getKdfSalt(), c.getIv(), c.getSecretCipher(), c.getKeyVersion(), adminId);
    }
}
