package com.example.be_web_admin.service.redis;

import com.example.be_web_admin.model.jpa.Admin;
import com.example.be_web_admin.repository.jpa.AdminRepository;
import com.example.be_web_admin.repository.jpa.TotpCredentialRepository;
import com.example.be_web_admin.security.EnrollmentGenerationStore;
import com.example.be_web_admin.security.EnrollmentFenceStore;
import com.example.be_web_admin.service.jpa.impl.AuthAuditService;
import com.example.be_web_admin.service.redis.impl.EnrollmentConfirmServiceImpl;
import com.example.be_web_admin.service.redis.impl.EnrollmentServiceImpl;
import com.example.be_web_admin.service.redis.impl.TwoFaEventServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EnrollmentCancellationRaceTest {
    @Test
    void staleCancelOnlyTargetsItsOwnCredentialAndDoesNotTouchRedisOrPublish() {
        AdminRepository admins = mock(AdminRepository.class);
        when(admins.findForTotpUpdate(7L)).thenReturn(new Admin());
        TotpCredentialRepository credentials = mock(TotpCredentialRepository.class);
        EnrollmentGenerationStore redis = mock(EnrollmentGenerationStore.class);
        TwoFaEventServiceImpl events = mock(TwoFaEventServiceImpl.class);
        AuthAuditService audit = mock(AuthAuditService.class);
        TransactionTemplate tx = mock(TransactionTemplate.class);
        when(tx.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        EnrollmentServiceImpl service = new EnrollmentServiceImpl(redis, mock(EnrollmentFenceStore.class), admins, credentials, audit,
                mock(EnrollmentConfirmServiceImpl.class), tx, events);
        assertFalse(service.cancelPending(7L, 41L, "peer", "ua"));
        verify(credentials).deletePendingGeneration(7L, 41L);
        verify(credentials, never()).deletePendingByAdminId(anyLong());
        verifyNoInteractions(redis, events, audit);
    }
}
