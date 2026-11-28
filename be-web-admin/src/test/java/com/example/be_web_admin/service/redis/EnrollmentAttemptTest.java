package com.example.be_web_admin.service.redis;

import com.example.be_web_admin.config.TotpProperties;
import com.example.be_web_admin.model.jpa.TotpCredential;
import com.example.be_web_admin.repository.jpa.AdminRepository;
import com.example.be_web_admin.repository.jpa.TotpCredentialRepository;
import com.example.be_web_admin.security.private_key.CryptoService;
import com.example.be_web_admin.service.jpa.impl.AuthAuditService;
import com.example.be_web_admin.service.redis.impl.EnrollmentConfirmServiceImpl;
import com.example.be_web_admin.service.redis.impl.TwoFaEventServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import com.example.be_web_admin.model.jpa.Admin;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EnrollmentAttemptTest {
    @Test
    void noRedisResultFailsClosedBeforeCredentialDecryption() {
        TotpCredentialRepository credentials = mock(TotpCredentialRepository.class);
        TotpCredential pending = new TotpCredential();
        pending.setCredentialId(42L);
        pending.setCreatedAt(Instant.now());
        when(credentials.findFirstByAdmin_AdminIdAndConfirmedFalseOrderByCreatedAtDesc(7L))
                .thenReturn(Optional.of(pending));
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        CryptoService crypto = mock(CryptoService.class);
        EnrollmentConfirmServiceImpl service = new EnrollmentConfirmServiceImpl(credentials,
                mock(AdminRepository.class), mock(AuthAuditService.class), crypto, new TotpProperties(),
                redis, mock(TransactionTemplate.class), mock(TwoFaEventServiceImpl.class));
        assertThrows(IllegalStateException.class, () -> service.confirm(7L, "123456", "peer", "ua"));
        verify(redis).execute(any(RedisScript.class), eq(List.of("enroll_confirm_attempts:7:42")), eq("600"));
        verifyNoInteractions(crypto);
    }

    @Test
    void confirmRejectsAnOldPendingGenerationBeforeCountingOrDecrypting() {
        TotpCredentialRepository credentials = mock(TotpCredentialRepository.class);
        TotpCredential pending = new TotpCredential();
        pending.setCredentialId(43L);
        pending.setCreatedAt(Instant.now());
        when(credentials.findFirstByAdmin_AdminIdAndConfirmedFalseOrderByCreatedAtDesc(7L))
                .thenReturn(Optional.of(pending));
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        CryptoService crypto = mock(CryptoService.class);
        EnrollmentConfirmServiceImpl service = new EnrollmentConfirmServiceImpl(credentials, mock(AdminRepository.class),
                mock(AuthAuditService.class), crypto, new TotpProperties(), redis, mock(TransactionTemplate.class),
                mock(TwoFaEventServiceImpl.class));
        assertThrows(EnrollmentConfirmServiceImpl.NoPendingEnrollmentException.class,
                () -> service.confirm(7L, 42L, "123456", "peer", "ua"));
        verifyNoInteractions(redis, crypto);
    }

    @Test
    void receiptDoesNotReconcileAReplacementGeneration() {
        TotpCredentialRepository credentials = mock(TotpCredentialRepository.class);
        TotpCredential active = new TotpCredential();
        active.setCredentialId(43L);
        when(credentials.findFirstByAdmin_AdminIdAndConfirmedTrueOrderByConfirmedAtDesc(7L))
                .thenReturn(Optional.of(active));
        Admin admin = new Admin();
        admin.setTotpEnabled(true);
        AdminRepository admins = mock(AdminRepository.class);
        when(admins.findForTotpUpdate(7L)).thenReturn(admin);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        CryptoService crypto = mock(CryptoService.class);
        TransactionTemplate tx = mock(TransactionTemplate.class);
        when(tx.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        EnrollmentConfirmServiceImpl service = new EnrollmentConfirmServiceImpl(credentials, admins,
                mock(AuthAuditService.class), crypto, new TotpProperties(), redis, tx,
                mock(TwoFaEventServiceImpl.class));
        assertFalse(service.verifyConfirmedGeneration(7L, 42L, "123456"));
        verifyNoInteractions(redis, crypto);
    }
}
