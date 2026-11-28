package com.example.server_ai_worker.service.ai;

import com.example.server_ai_worker.config.AiProperties;
import com.example.server_ai_worker.repository.jpa.AiTaskRepository;
import com.example.server_ai_worker.service.redis.AiQueueService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import java.time.Duration;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PendingRecoveryTest {
    @Test
    void recoversPendingWithDistributedThrottleAndRotatingCursor() {
        AiTaskRepository tasks = mock(AiTaskRepository.class);
        AiQueueService queue = mock(AiQueueService.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(tasks.findPendingForRecovery("")).thenReturn(List.of("a", "b"));
        when(tasks.findPendingForRecovery("b")).thenReturn(List.of());
        when(values.setIfAbsent(eq("ai:pending-recovery:a"), eq("1"), any(Duration.class))).thenReturn(true, false);
        when(values.setIfAbsent(eq("ai:pending-recovery:b"), eq("1"), any(Duration.class))).thenReturn(false);
        AiDispatcher dispatcher = new AiDispatcher(queue, mock(AiTaskProcessor.class), tasks,
                mock(NodePool.class), new AiProperties(), redis);
        dispatcher.recoverPending();
        dispatcher.recoverPending(); // end of scan resets cursor
        dispatcher.recoverPending(); // active marker suppresses duplicate recovery
        verify(queue).push("a");
        verify(queue, never()).push("b");
        verify(tasks, times(2)).findPendingForRecovery("");
    }
    @Test
    void enqueueFailureIsRetriedAfterMarkerExpires() {
        AiTaskRepository tasks = mock(AiTaskRepository.class);
        AiQueueService queue = mock(AiQueueService.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(tasks.findPendingForRecovery("")).thenReturn(List.of("a"));
        when(tasks.findPendingForRecovery("a")).thenReturn(List.of());
        when(values.setIfAbsent(eq("ai:pending-recovery:a"), eq("1"), any(Duration.class)))
                .thenReturn(true, false, true); // expiry happens between second and third claims
        doThrow(new IllegalStateException("Redis push failed")).doNothing().when(queue).push("a");
        AiDispatcher dispatcher = new AiDispatcher(queue, mock(AiTaskProcessor.class), tasks,
                mock(NodePool.class), new AiProperties(), redis);
        assertThrows(IllegalStateException.class, dispatcher::recoverPending);
        dispatcher.recoverPending(); // marker still present, cursor advances
        dispatcher.recoverPending(); // end of scan resets cursor
        dispatcher.recoverPending(); // marker expired, another enqueue
        verify(queue, times(2)).push("a");
    }

}
