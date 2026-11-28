package com.example.be_web_admin.service.redis;

import com.example.be_web_admin.config.TotpProperties;
import com.example.be_web_admin.model.jpa.Admin;
import com.example.be_web_admin.model.jpa.TotpCredential;
import com.example.be_web_admin.repository.jpa.AdminRepository;
import com.example.be_web_admin.repository.jpa.TotpCredentialRepository;
import com.example.be_web_admin.security.AuthSessionStore;
import com.example.be_web_admin.security.AuthenticationRateLimiter;
import com.example.be_web_admin.security.ConfirmReceiptStore;
import com.example.be_web_admin.security.private_key.CryptoService;
import com.example.be_web_admin.security.totp.TotpVerifier;
import com.example.be_web_admin.service.jpa.impl.AuthAuditService;
import com.example.be_web_admin.service.redis.impl.EnrollmentConfirmServiceImpl;
import com.example.be_web_admin.service.redis.impl.IdempotentEnrollmentConfirmService;
import com.example.be_web_admin.service.redis.impl.TwoFaEventServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Unit mocks only; they do not prove DB transaction atomicity. */
class IdempotentEnrollmentConfirmTest {
    private static final String SESSION = "a9c62262-bdd5-43a9-8a3b-f89bc0c3a870";
    private static final String REQUEST = "6b".repeat(32);
    private static final String SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"; // public test vector
    private final AuthSessionStore.Identity id = new AuthSessionStore.Identity(7L, AuthSessionStore.Client.MOBILE, SESSION);
    private final AdminRepository admins = mock(AdminRepository.class);
    private final TotpCredentialRepository credentials = mock(TotpCredentialRepository.class);
    private final ConfirmReceiptStore receipts = mock(ConfirmReceiptStore.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final CryptoService crypto = mock(CryptoService.class);
    private final AuthSessionStore sessions = mock(AuthSessionStore.class);
    private final AuthenticationRateLimiter limiter = mock(AuthenticationRateLimiter.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);
    private final AuthAuditService audit = mock(AuthAuditService.class);
    private final TwoFaEventServiceImpl events = mock(TwoFaEventServiceImpl.class);
    private final TotpProperties props = new TotpProperties();
    private final EnrollmentConfirmServiceImpl legacy = new EnrollmentConfirmServiceImpl(credentials, admins, audit,
            crypto, props, redis, tx, events);
    private final IdempotentEnrollmentConfirmService service = new IdempotentEnrollmentConfirmService(admins,
            credentials, receipts, legacy, props, crypto, sessions, limiter, tx, audit, events);

    private void transaction() {
        when(tx.execute(any())).thenAnswer(inv -> ((TransactionCallback<?>) inv.getArgument(0))
                .doInTransaction(mock(TransactionStatus.class)));
        when(sessions.isActive(id)).thenReturn(true);
    }
    private TotpCredential credential() {
        TotpCredential c = new TotpCredential();
        c.setCredentialId(42L); c.setCreatedAt(Instant.now());
        c.setKdfSalt(new byte[16]); c.setIv(new byte[12]); c.setSecretCipher(new byte[32]);
        c.setAlgorithm("HmacSHA256"); c.setDigits((byte) 6); c.setPeriodSeconds((short) 30);
        when(crypto.decrypt(any(), any(), any(), eq(1), eq(7L))).thenReturn(SECRET);
        return c;
    }
    private String codeNow() {
        return TotpVerifier.generate(TotpVerifier.base32Decode(SECRET), Instant.now().getEpochSecond() / 30, 6, "HmacSHA256");
    }

    @Test
    void createWritesReceiptForExactlyTheConfirmedCredential() {
        transaction();
        Admin admin = new Admin();
        when(admins.findForTotpUpdate(7L)).thenReturn(admin);
        TotpCredential pending = credential();
        when(credentials.findFirstByAdmin_AdminIdAndConfirmedFalseOrderByCreatedAtDesc(7L)).thenReturn(Optional.of(pending));
        when(redis.execute(any(RedisScript.class), eq(List.of("enroll_confirm_attempts:7:42")), eq("600"))).thenReturn(1L);
        assertEquals(IdempotentEnrollmentConfirmService.Result.CREATED, service.confirm(id, 42L, REQUEST, codeNow(), "peer", "ua"));
        assertTrue(admin.getTotpEnabled());
        assertTrue(pending.getConfirmed());
        verify(receipts).insert(eq(7L), eq(42L), eq(SESSION), any(), any(), any());
        verify(events).publish(7L, TwoFaEventServiceImpl.ENABLED);
    }

    @Test
    void replayMatchesPayloadAndCurrentCredentialWithoutAnotherTotpAttempt() {
        transaction();
        Admin admin = new Admin(); admin.setTotpEnabled(true);
        when(admins.findForTotpUpdate(7L)).thenReturn(admin);
        TotpCredential current = credential(); current.setConfirmed(true);
        when(credentials.findFirstByAdmin_AdminIdAndConfirmedTrueOrderByConfirmedAtDesc(7L)).thenReturn(Optional.of(current));
        String code = "123456";
        var fp = ConfirmReceiptStore.fingerprint(SECRET, 7L, 42L, SESSION, REQUEST, code);
        when(receipts.find(eq(7L), any())).thenReturn(new ConfirmReceiptStore.Receipt(42L, SESSION, fp.payloadMac(), Instant.now().plusSeconds(60)));
        assertEquals(IdempotentEnrollmentConfirmService.Result.REPLAYED, service.confirm(id, 42L, REQUEST, code, "peer", "ua"));
        assertEquals(IdempotentEnrollmentConfirmService.Result.CONFLICT, service.confirm(id, 42L, REQUEST, "123457", "peer", "ua"));
        verifyNoInteractions(redis, audit, events);
        verify(receipts, never()).insert(anyLong(), anyLong(), anyString(), any(), any(), any());
        when(credentials.findFirstByAdmin_AdminIdAndConfirmedTrueOrderByConfirmedAtDesc(7L)).thenReturn(Optional.empty());
        assertEquals(IdempotentEnrollmentConfirmService.Result.NOT_FOUND, service.confirm(id, 42L, REQUEST, code, "peer", "ua"));
    }

    @Test
    void expiredOrDifferentSessionReceiptCannotBeReplayed() {
        transaction();
        Admin admin = new Admin(); admin.setTotpEnabled(true);
        when(admins.findForTotpUpdate(7L)).thenReturn(admin);
        var fp = ConfirmReceiptStore.fingerprint(SECRET, 7L, 42L, SESSION, REQUEST, "123456");
        when(receipts.find(eq(7L), any())).thenReturn(
                new ConfirmReceiptStore.Receipt(42L, SESSION, fp.payloadMac(), Instant.now().minusSeconds(1)),
                new ConfirmReceiptStore.Receipt(42L, "a9c62262-bdd5-43a9-8a3b-f89bc0c3a871", fp.payloadMac(), Instant.now().plusSeconds(60)));
        assertEquals(IdempotentEnrollmentConfirmService.Result.CONFLICT, service.confirm(id, 42L, REQUEST, "123456", "peer", "ua"));
        assertEquals(IdempotentEnrollmentConfirmService.Result.CONFLICT, service.confirm(id, 42L, REQUEST, "123456", "peer", "ua"));
        when(sessions.isActive(id)).thenReturn(false);
        assertEquals(IdempotentEnrollmentConfirmService.Result.UNAUTHORIZED, service.confirm(id, 42L, REQUEST, "123456", "peer", "ua"));
        verifyNoInteractions(crypto, redis, events, audit);
    }

    @Test
    void fingerprintBindsAccountCredentialSessionRequestAndCode() {
        var base = ConfirmReceiptStore.fingerprint(SECRET, 7L, 42L, SESSION, REQUEST, "123456");
        assertArrayEquals(base.payloadMac(), ConfirmReceiptStore.fingerprint(SECRET, 7L, 42L, SESSION, REQUEST, "123456").payloadMac());
        assertFalse(java.security.MessageDigest.isEqual(base.payloadMac(), ConfirmReceiptStore.fingerprint(SECRET, 8L, 42L, SESSION, REQUEST, "123456").payloadMac()));
        assertFalse(java.security.MessageDigest.isEqual(base.payloadMac(), ConfirmReceiptStore.fingerprint(SECRET, 7L, 43L, SESSION, REQUEST, "123456").payloadMac()));
        assertFalse(java.security.MessageDigest.isEqual(base.payloadMac(), ConfirmReceiptStore.fingerprint(SECRET, 7L, 42L, SESSION, REQUEST, "123457").payloadMac()));
        assertThrows(IllegalArgumentException.class, () -> ConfirmReceiptStore.requestHash("not-random"));
    }

    @Test
    void receiptInsertFailureDoesNotPublishSuccess() {
        transaction();
        Admin admin = new Admin();
        when(admins.findForTotpUpdate(7L)).thenReturn(admin);
        TotpCredential pending = credential();
        when(credentials.findFirstByAdmin_AdminIdAndConfirmedFalseOrderByCreatedAtDesc(7L)).thenReturn(Optional.of(pending));
        when(redis.execute(any(RedisScript.class), eq(List.of("enroll_confirm_attempts:7:42")), eq("600"))).thenReturn(1L);
        doThrow(new IllegalStateException("insert failed")).when(receipts)
                .insert(eq(7L), eq(42L), eq(SESSION), any(), any(), any());
        assertThrows(IllegalStateException.class, () -> service.confirm(id, 42L, REQUEST, codeNow(), "peer", "ua"));
        // Mocks do not roll back the mutated entities; a real DB test must prove that behavior.
        verifyNoInteractions(audit, events);
    }

    @Test
    void transactionFailureNeverPublishesSuccess() {
        transaction();
        when(tx.execute(any())).thenThrow(new IllegalStateException("unknown commit outcome"));
        assertThrows(IllegalStateException.class, () -> service.confirm(id, 42L, REQUEST, "123456", "peer", "ua"));
        verifyNoInteractions(audit, events);
    }
}
