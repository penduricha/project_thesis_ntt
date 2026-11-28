package com.example.be_web_admin.service.qbank;

import com.example.be_web_admin.dto.qbank.QuestionDraft;
import com.example.be_web_admin.exception.qbank.QbankException;
import com.example.be_web_admin.repository.redis.UiAiJobViewRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;

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
        QbankException write = assertThrows(QbankException.class, () -> store.saveDraft(1L, "new", draft));
        QbankException read = assertThrows(QbankException.class, () -> store.readDraft(1L, "new"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, write.getStatus());
        assertEquals("DRAFT_UNAVAILABLE", read.getCode());
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
