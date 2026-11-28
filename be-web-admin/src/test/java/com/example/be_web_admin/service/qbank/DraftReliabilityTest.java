package com.example.be_web_admin.service.qbank;

import com.example.be_web_admin.dto.qbank.QuestionDraft;
import com.example.be_web_admin.exception.qbank.QBankException;
import com.example.be_web_admin.repository.redis.UiAiJobViewRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DraftReliabilityTest {
    @Test
    void redisFailureIsNotReportedAsSavedOrMissing() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        doThrow(new IllegalStateException("offline")).when(values).set(anyString(), anyString(), any(Duration.class));
        when(values.get(anyString())).thenThrow(new IllegalStateException("offline"));
        UiStateStore store = new UiStateStore(redis, new ObjectMapper(), mock(UiAiJobViewRepository.class));
        QuestionDraft draft = new QuestionDraft(1L, null, null, "question", "A", "B", "C", "D", "A", "plain", null, null, null);
        QBankException write = assertThrows(QBankException.class, () -> store.saveDraft(1L, "new", draft));
        QBankException read = assertThrows(QBankException.class, () -> store.readDraft(1L, "new"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, write.getStatus());
        assertEquals("DRAFT_UNAVAILABLE", read.getCode());
    }

    @Test
    void explicitDeleteReportsRedisFailureAndAcceptsAlreadyAbsent() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        UiStateStore store = new UiStateStore(redis, new ObjectMapper(), mock(UiAiJobViewRepository.class));
        when(redis.delete(anyString())).thenThrow(new IllegalStateException("offline"));
        QBankException failure = assertThrows(QBankException.class, () -> store.deleteDraft(1L, "new"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failure.getStatus());
        doReturn(null).when(redis).delete(anyString());
        assertThrows(QBankException.class, () -> store.deleteDraft(1L, "new"));
        doReturn(false).when(redis).delete(anyString());
        assertDoesNotThrow(() -> store.deleteDraft(1L, "new"));
    }

    @Test
    void cleanupRunsOnlyAfterCommitAndIsBestEffort() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        UiStateStore store = new UiStateStore(redis, new ObjectMapper(), mock(UiAiJobViewRepository.class));
        assertThrows(IllegalStateException.class, () -> store.deleteDraftAfterCommit(1L, "new"));
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            store.deleteDraftAfterCommit(1L, "new");
            verify(redis, never()).delete(anyString());
            TransactionSynchronization sync = TransactionSynchronizationManager.getSynchronizations().get(0);
            when(redis.delete(anyString())).thenThrow(new IllegalStateException("offline"));
            assertDoesNotThrow(sync::afterCommit);
            verify(redis).delete(anyString());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void absentDraftIsDistinctFromUnavailable() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        UiStateStore store = new UiStateStore(redis, new ObjectMapper(), mock(UiAiJobViewRepository.class));
        assertTrue(store.readDraft(1L, "new").isEmpty());
    }
}
