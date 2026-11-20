package com.example.be_server_ai.service.llm;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.be_server_ai.config.AiProperties;
import com.example.be_server_ai.enums.AiErrorCode;
import com.example.be_server_ai.enums.AiTaskStatus;
import com.example.be_server_ai.enums.AiTaskType;
import com.example.be_server_ai.model.jpa.AiTask;
import com.example.be_server_ai.service.jpa.AiTaskService;
import com.example.be_server_ai.service.redis.AiCacheService;
import com.example.be_server_ai.service.redis.AiQueueService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Xử lý MỘT task đã lấy khỏi hàng đợi, chạy trên một node đã giữ chỗ (lease).
 *
 * <p>Luồng: claim (UPDATE pending -> running) -> gọi Colab NGOÀI mọi transaction -> ghi kết quả
 * (UPDATE running -> succeeded + INSERT đề xuất, một transaction ngắn) -> PUBLISH ai:done.
 * Lỗi của node (không kết nối, quá hạn, token sai, 5xx) hay model trả sai dạng: tăng attempt_count và thử
 * ngay trên NODE KHÁC (không có node nào rảnh trong vài giây thì trả task về hàng đợi). Hết max-attempts
 * thì failed (MAX_ATTEMPTS). Chỉ khi cả 5 node đều đầy hoặc nghỉ, task mới chờ ở hàng đợi, quá max-wait
 * thì failed (QUEUE_TIMEOUT).
 */
@Component
public class AiTaskProcessor {

    private static final Logger log = LoggerFactory.getLogger(AiTaskProcessor.class);
    private static final Duration RETRY_WAIT = Duration.ofSeconds(3);

    private final AiTaskService tasks;
    private final NodePool pool;
    private final OllamaClient ollama;
    private final AiPromptFactory prompts;
    private final AiResultParser parser;
    private final AiCacheService cache;
    private final AiQueueService queue;
    private final AiProperties props;
    private final ObjectMapper om;

    public AiTaskProcessor(AiTaskService tasks, NodePool pool, OllamaClient ollama, AiPromptFactory prompts,
                           AiResultParser parser, AiCacheService cache, AiQueueService queue, AiProperties props,
                           ObjectMapper om) {
        this.tasks = tasks;
        this.pool = pool;
        this.ollama = ollama;
        this.prompts = prompts;
        this.parser = parser;
        this.cache = cache;
        this.queue = queue;
        this.props = props;
        this.om = om;
    }

    /** Không bao giờ ném lỗi; luôn trả lease. */
    public void process(String taskId, NodePool.Lease lease) {
        try {
            Optional<AiTask> claimed = tasks.claim(taskId);
            if (claimed.isEmpty()) {
                lease.release();   // task đã hủy, đã có worker khác lấy, hoặc id trùng trong hàng đợi
                return;
            }
            execute(claimed.get(), lease);
        } catch (Throwable e) {
            log.error("Lỗi không lường trước khi xử lý task {}", taskId, e);
            lease.release();
            try {
                tasks.fail(taskId, AiErrorCode.INTERNAL_ERROR, String.valueOf(e), null);
                cache.putStatus(taskId, null, AiTaskStatus.FAILED, null, AiErrorCode.INTERNAL_ERROR.name());
                queue.publishDone(taskId, AiTaskStatus.FAILED.getValue());
            } catch (Throwable ignore) {
                // CSDL không ghi được: sweeper sẽ xử lý task kẹt.
            }
        }
    }

    private void execute(AiTask t, NodePool.Lease first) throws InterruptedException {
        String id = t.getTaskId();
        AiTaskType type = t.getTaskType();
        AiProperties.Orchestrator orch = props.getOrchestrator();
        String promptVersion = props.getLlm().getPromptVersion();
        NodePool.Lease cur = first;
        try {
            int attempt = t.getAttemptCount() == null ? 1 : t.getAttemptCount();
            touch(t, AiTaskStatus.RUNNING, attempt, null);

            if (waitedTooLong(t)) {
                cur.release();
                failFinal(t, AiErrorCode.QUEUE_TIMEOUT, "Chờ hàng đợi quá " + orch.getMaxWait().toMinutes() + " phút.", null);
                return;
            }

            ChatSpec spec;
            String nlHash = null;
            try {
                JsonNode input = om.readTree(t.getInputJson());
                spec = prompts.build(type, input, tasks.issueTypeCodes());
                if (type == AiTaskType.NL_SEARCH) {
                    nlHash = sha256(AiPromptFactory.normalizeQuery(input.path("query").asText("")) + "|" + promptVersion);
                    Optional<String> hit = cache.getNlFilter(nlHash);
                    if (hit.isPresent()) {
                        cur.release();
                        completeFromCache(t, hit.get(), promptVersion);
                        return;
                    }
                }
            } catch (IllegalArgumentException | JsonProcessingException e) {
                cur.release();
                failFinal(t, AiErrorCode.BAD_INPUT, e.getMessage(), null);
                return;
            }

            Set<String> tried = new HashSet<>();
            while (true) {
                ChatResult r = null;
                LlmException err = null;
                try {
                    r = ollama.chat(cur.url(), cur.token(), spec);
                    cur.success(r.wallMs(), r.tokensPerSec());
                } catch (LlmException e) {
                    err = e;
                    if (e.isNodeFault()) {
                        cur.failure(e.getCode(), e.getMessage());
                    } else {
                        cur.release();
                    }
                }
                String node = cur.name();
                tried.add(node);

                if (r != null) {
                    try {
                        List<SuggestionDraft> drafts = List.of();
                        JsonNode result;
                        if (type == AiTaskType.QUESTION_CHECK) {
                            CheckOutcome outcome = parser.parseQuestionCheck(r.content(), tasks.issueTypeCodes());
                            result = outcome.result();
                            drafts = outcome.drafts();
                        } else {
                            result = parser.parseNlFilter(r.content());
                        }
                        int ms = (int) (r.totalMs() > 0 ? r.totalMs() : r.wallMs());
                        boolean saved = tasks.complete(id, result.toString(), r.content(), r.model(), promptVersion,
                                paramsJson(spec), usageJson(r, node, attempt), ms, drafts);
                        if (!saved) {
                            log.info("Task {} đã đổi trạng thái (ví dụ bị hủy), bỏ kết quả", id);
                            return;
                        }
                        if (nlHash != null) {
                            cache.putNlFilter(nlHash, result.toString(), promptVersion);
                        }
                        touch(t, AiTaskStatus.SUCCEEDED, attempt, null);
                        queue.publishDone(id, AiTaskStatus.SUCCEEDED.getValue());
                        return;
                    } catch (LlmException parseError) {
                        err = parseError;
                    }
                }

                AiErrorCode code = err.getCode();
                String msg = "[" + node + "] " + err.getMessage();
                if (!code.isRetryable()) {
                    failFinal(t, code, msg, err.getRaw());
                    return;
                }
                if (attempt >= orch.getMaxAttempts()) {
                    failFinal(t, AiErrorCode.MAX_ATTEMPTS, "Hết " + attempt + " lần thử. Lỗi cuối: " + code + " " + msg, err.getRaw());
                    return;
                }
                if (waitedTooLong(t)) {
                    failFinal(t, AiErrorCode.QUEUE_TIMEOUT, "Quá " + orch.getMaxWait().toMinutes() + " phút kể từ lúc tạo. Lỗi cuối: " + msg, err.getRaw());
                    return;
                }

                NodePool.Lease next = acquireOther(tried);
                if (next == null) {
                    // Không còn node nào rảnh: trả về hàng đợi, dispatcher giao lại khi có node.
                    if (tasks.requeue(id, code, msg)) {
                        touch(t, AiTaskStatus.PENDING, attempt + 1, code.name());
                        queue.push(id);
                    }
                    return;
                }
                if (!tasks.retryInPlace(id, code, msg)) {
                    next.release();   // task không còn running (đã bị hủy)
                    return;
                }
                attempt++;
                cur = next;
                touch(t, AiTaskStatus.RUNNING, attempt, code.name());
            }
        } finally {
            cur.release();   // no-op nếu lease đã kết thúc
        }
    }

    /** Ưu tiên node chưa thử; không có thì cho phép thử lại node đã thử (nếu node đó còn đủ điều kiện). */
    private NodePool.Lease acquireOther(Set<String> tried) throws InterruptedException {
        NodePool.Lease l = pool.acquire(RETRY_WAIT, tried);
        return l != null ? l : pool.acquire(Duration.ofMillis(500), Set.of());
    }

    private void completeFromCache(AiTask t, String filterJson, String promptVersion) {
        ObjectNode usage = om.createObjectNode();
        usage.put("node", "cache");
        usage.put("cache", "hit");
        boolean saved = tasks.complete(t.getTaskId(), filterJson, filterJson, null, promptVersion,
                "{\"cache\":\"hit\"}", usage.toString(), 0, List.of());
        if (saved) {
            touch(t, AiTaskStatus.SUCCEEDED, t.getAttemptCount(), null);
            queue.publishDone(t.getTaskId(), AiTaskStatus.SUCCEEDED.getValue());
        }
    }

    private void failFinal(AiTask t, AiErrorCode code, String message, String raw) {
        if (tasks.fail(t.getTaskId(), code, message, raw)) {
            touch(t, AiTaskStatus.FAILED, t.getAttemptCount(), code.name());
            queue.publishDone(t.getTaskId(), AiTaskStatus.FAILED.getValue());
        }
    }

    private void touch(AiTask t, AiTaskStatus status, Integer attempt, String errorCode) {
        cache.putStatus(t.getTaskId(), t.getTaskType(), status, attempt, errorCode);
    }

    private boolean waitedTooLong(AiTask t) {
        return t.getCreatedAt() != null
                && Duration.between(t.getCreatedAt(), Instant.now()).compareTo(props.getOrchestrator().getMaxWait()) > 0;
    }

    private String paramsJson(ChatSpec spec) {
        ObjectNode p = om.createObjectNode();
        p.put("num_ctx", spec.options().getNumCtx());
        p.put("num_predict", spec.options().getNumPredict());
        p.put("temperature", spec.options().getTemperature());
        p.put("keep_alive", props.getLlm().getKeepAlive());
        p.put("format", "json_schema");
        return p.toString();
    }

    /** usage_json: node đã xử lý (để thấy chia tải giữa các tài khoản Colab), số token, tốc độ, thời gian. */
    private String usageJson(ChatResult r, String node, int attempt) {
        ObjectNode u = om.createObjectNode();
        u.put("node", node);
        u.put("prompt_tokens", r.promptTokens());
        u.put("eval_tokens", r.evalTokens());
        u.put("tokens_per_sec", Math.round(r.tokensPerSec() * 10) / 10.0);
        u.put("total_ms", r.totalMs());
        u.put("load_ms", r.loadMs());
        u.put("wall_ms", r.wallMs());
        u.put("attempts", attempt);
        return u.toString();
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
