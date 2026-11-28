package com.example.be_web_admin.service.redis;

import com.example.be_web_admin.model.jpa.Admin;
import com.example.be_web_admin.repository.jpa.AdminRepository;
import com.example.be_web_admin.repository.jpa.TotpCredentialRepository;
import com.example.be_web_admin.security.EnrollmentFenceStore;
import com.example.be_web_admin.security.EnrollmentGenerationStore;
import com.example.be_web_admin.security.private_key.CryptoService;
import com.example.be_web_admin.service.jpa.impl.AuthAuditService;
import com.example.be_web_admin.service.redis.impl.EnrollmentConfirmServiceImpl;
import com.example.be_web_admin.service.redis.impl.EnrollmentServiceImpl;
import com.example.be_web_admin.service.redis.impl.TwoFaEventServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Unit tests only: real transaction/failover tests on MariaDB and Redis remain required. */
class EnrollmentFenceReconciliationTest {
    private static final String GEN = "a9c62262-bdd5-43a9-8a3b-f89bc0c3a870";
    private static final String CLAIM = "6f5cb2cc-a391-4a11-992e-6a64652dc2af";
    private final EnrollmentGenerationStore redis = mock(EnrollmentGenerationStore.class);
    private final EnrollmentFenceStore fences = mock(EnrollmentFenceStore.class);
    private final AdminRepository admins = mock(AdminRepository.class);
    private final TotpCredentialRepository credentials = mock(TotpCredentialRepository.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);
    private final EnrollmentServiceImpl service = new EnrollmentServiceImpl(redis, fences, admins, credentials,
            mock(AuthAuditService.class), mock(EnrollmentConfirmServiceImpl.class), tx, mock(TwoFaEventServiceImpl.class));

    @Test
    void canceledFenceIsCommittedBeforeOwnerCheckedCleanup() {
        when(redis.claimed(7L, GEN)).thenReturn(new EnrollmentGenerationStore.ClaimedGeneration(GEN, CLAIM));
        when(admins.findForTotpUpdate(7L)).thenReturn(new Admin());
        AtomicBoolean committed = new AtomicBoolean(false);
        when(tx.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            Object result = callback.doInTransaction(mock(TransactionStatus.class));
            committed.set(true); // represents successful return from the transaction manager
            return result;
        });
        when(redis.finishIfOwned(7L, GEN, CLAIM)).thenAnswer(i -> {
            assertTrue(committed.get());
            return true;
        });
        var result = service.reconcileClaim(7L, GEN, "peer", "ua");
        assertEquals("CANCELLED", result.outcome());
        assertTrue(result.claimReleased());
        var order = inOrder(admins, fences, redis);
        order.verify(admins).findForTotpUpdate(7L);
        order.verify(fences).find(7L, GEN);
        order.verify(fences).cancel(7L, GEN);
        order.verify(redis).finishIfOwned(7L, GEN, CLAIM);
        verifyNoInteractions(credentials);
    }

    @Test
    void unknownCommitDoesNotReleaseClaim() {
        when(redis.claimed(7L, GEN)).thenReturn(new EnrollmentGenerationStore.ClaimedGeneration(GEN, CLAIM));
        when(admins.findForTotpUpdate(7L)).thenReturn(new Admin());
        when(tx.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            callback.doInTransaction(mock(TransactionStatus.class));
            throw new IllegalStateException("commit outcome unknown");
        });
        assertThrows(IllegalStateException.class, () -> service.reconcileClaim(7L, GEN, "peer", "ua"));
        verify(redis, never()).finishIfOwned(anyLong(), anyString(), anyString());
    }

    @Test
    void missingRedisClaimStillFencesDelayedWorkerBeforeCleanup() {
        when(admins.findForTotpUpdate(7L)).thenReturn(new Admin());
        when(tx.execute(any())).thenAnswer(invocation -> ((TransactionCallback<?>) invocation.getArgument(0))
                .doInTransaction(mock(TransactionStatus.class)));
        var result = service.reconcileClaim(7L, GEN, "peer", "ua");
        assertEquals("CANCELLED", result.outcome());
        assertFalse(result.claimReleased());
        var order = inOrder(fences, redis);
        order.verify(fences).cancel(7L, GEN);
        order.verify(redis).claimed(7L, GEN);
        order.verify(redis).cancel(7L, GEN);
        verifyNoInteractions(credentials);
    }

    @Test
    void committedFenceDoesNotCancelOrMutateCredential() {
        when(redis.claimed(7L, GEN)).thenReturn(new EnrollmentGenerationStore.ClaimedGeneration(GEN, CLAIM));
        when(admins.findForTotpUpdate(7L)).thenReturn(new Admin());
        when(fences.find(7L, GEN)).thenReturn(new EnrollmentFenceStore.Fence(EnrollmentFenceStore.State.COMMITTED, 42L));
        when(tx.execute(any())).thenAnswer(invocation -> ((TransactionCallback<?>) invocation.getArgument(0))
                .doInTransaction(mock(TransactionStatus.class)));
        when(redis.finishIfOwned(7L, GEN, CLAIM)).thenReturn(true);
        assertEquals("COMMITTED", service.reconcileClaim(7L, GEN, "peer", "ua").outcome());
        verify(fences, never()).cancel(anyLong(), anyString());
        verifyNoInteractions(credentials);
    }

    @Test
    void missingFenceTableDoesNotTakeRedisClaim() {
        doThrow(new IllegalStateException("missing migration")).when(fences).requireAvailable();
        assertThrows(IllegalStateException.class, () -> service.activate(7L, GEN, 30, "peer", "ua"));
        verifyNoInteractions(redis, credentials);
    }

    @Test
    void delayedActivationCannotWriteAfterCancellationFence() {
        var sealed = new CryptoService.Sealed(new byte[16], new byte[12], new byte[32], 1);
        var pending = new EnrollmentGenerationStore.Pending(GEN, "READY", sealed.toBase64(), 1, 30);
        when(redis.claim(7L, GEN, 30)).thenReturn(new EnrollmentGenerationStore.Claim(CLAIM, pending));
        when(admins.findForTotpUpdate(7L)).thenReturn(new Admin());
        when(fences.find(7L, GEN)).thenReturn(new EnrollmentFenceStore.Fence(EnrollmentFenceStore.State.CANCELLED, null));
        when(tx.execute(any())).thenAnswer(invocation -> ((TransactionCallback<?>) invocation.getArgument(0))
                .doInTransaction(mock(TransactionStatus.class)));
        assertThrows(EnrollmentServiceImpl.EnrollmentExpiredException.class,
                () -> service.activate(7L, GEN, 30, "peer", "ua"));
        verifyNoInteractions(credentials);
        verify(redis, never()).finish(anyLong(), anyString(), anyString());
    }
}
