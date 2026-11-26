package com.example.server_ai_worker.service.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.server_ai_worker.config.AiProperties;
import com.example.server_ai_worker.enums.AiErrorCode;
import com.example.server_ai_worker.enums.AiNodeStatus;
import com.example.server_ai_worker.service.redis.AiNodeStateService;

class NodePoolTest {

    private NodePool pool;

    @BeforeEach
    void setUp() {
        AiProperties props = new AiProperties();
        props.getOrchestrator().setFailThreshold(2);
        props.getOrchestrator().setCooldown(Duration.ofSeconds(30));
        AiNodeStateService noopState = state -> { };
        pool = new NodePool(props, noopState);
    }

    @Test
    void enforcesHttpsAndTryCloudflareAllowlist() {
        assertEquals("https://demo-node.trycloudflare.com",
                pool.normalizeAndValidateUrl("https://DEMO-NODE.trycloudflare.com/api/chat"));
        assertThrows(IllegalArgumentException.class,
                () -> pool.normalizeAndValidateUrl("http://demo-node.trycloudflare.com"));
        assertThrows(IllegalArgumentException.class,
                () -> pool.normalizeAndValidateUrl("https://127.0.0.1:11434"));
        assertThrows(IllegalArgumentException.class,
                () -> pool.normalizeAndValidateUrl("https://demo-node.trycloudflare.com:8443/extra"));
    }

    @Test
    void coolsDownNodeAfterThresholdFailuresAndResetsOnCredentialChange() throws Exception {
        pool.upsert("colab-1", "https://node-one.trycloudflare.com", "secret_token_1234567890", true, 2);

        try (NodePool.Lease l1 = pool.acquire(Duration.ofMillis(200), Set.of())) {
            assertNotNull(l1);
            l1.failure(AiErrorCode.LLM_TIMEOUT, "timeout 1");
        }
        try (NodePool.Lease l2 = pool.acquire(Duration.ofMillis(200), Set.of())) {
            assertNotNull(l2);
            l2.failure(AiErrorCode.LLM_TIMEOUT, "timeout 2");
        }

        // Sau 2 lỗi liên tiếp (threshold=2), colab-1 chuyển sang COOLING
        assertEquals(AiNodeStatus.COOLING, pool.views().get(0).status());
        assertNull(pool.acquire(Duration.ofMillis(100), Set.of()));

        // Đổi URL mới (Colab restart) -> tự xóa lỗi và sẵn sàng lại
        pool.upsert("colab-1", "https://node-one-restarted.trycloudflare.com", null, true, null);
        assertEquals(AiNodeStatus.IDLE, pool.views().get(0).status());
    }
}
