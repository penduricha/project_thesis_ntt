package com.example.server_ai_worker.service.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.server_ai_worker.config.AiProperties;
import com.example.server_ai_worker.enums.AiErrorCode;
import com.example.server_ai_worker.enums.AiTaskStatus;
import com.example.server_ai_worker.enums.AiTaskType;
import com.example.server_ai_worker.model.jpa.AiTask;
import com.example.server_ai_worker.repository.jpa.AiSuggestionRepository;
import com.example.server_ai_worker.repository.jpa.AiTaskRepository;
import com.example.server_ai_worker.service.redis.AiCacheService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Thực thi trọn vòng đời một task AI sau khi được lấy từ hàng đợi Redis.
 *
 * <p>Hợp đồng DB với Server 1:
 *  - Tránh thử lại trên cùng node vừa lỗi trong 1 lượt chạy (Set&lt;String&gt; triedNodes).
 *  - Nếu luồng bị ngắt do tắt worker êm (graceful shutdown), trả task về pending sạch sẽ (requeueOnShutdown).
 */
@Component
public class AiTaskProcessor {

    private static final Logger log = LoggerFactory.getLogger(AiTaskProcessor.class);
    private static final int MAX_RETRIES_PER_RUN = 3;

    private final AiTaskRepository tasks;
    private final AiSuggestionRepository suggestions;
    private final AiQueueFacade queueFacade;
    private final AiCacheService cache;
    private final NodePool nodePool;
    private final OllamaClient ollama;
    private final AiPromptFactory prompts;
    private final AiResultParser parser;
    private final SubjectCatalogResolver catalogResolver;
    private final AiProperties props;
    private final ObjectMapper json;

    public AiTaskProcessor(AiTaskRepository tasks,
                           AiSuggestionRepository suggestions,
                           AiQueueFacade queueFacade,
                           AiCacheService cache,
                           NodePool nodePool,
                           OllamaClient ollama,
                           AiPromptFactory prompts,
                           AiResultParser parser,
                           SubjectCatalogResolver catalogResolver,
                           AiProperties props,
                           ObjectMapper json) {
        this.tasks = tasks;
        this.suggestions = suggestions;
        this.queueFacade = queueFacade;
        this.cache = cache;
        this.nodePool = nodePool;
        this.ollama = ollama;
        this.prompts = prompts;
        this.parser = parser;
        this.catalogResolver = catalogResolver;
        this.props = props;
        this.json = json;
    }

    public void process(String taskId) {
        Optional<AiTask> opt = tasks.findById(taskId);
        if (opt.isEmpty()) {
            return;
        }
        AiTask task = opt.get();
        if (task.getStatus() != AiTaskStatus.PENDING) {
            return;
        }
        if (Instant.now().isAfter(task.getCreatedAt().plus(props.getOrchestrator().getMaxWait()))) {
            if (tasks.claim(taskId) > 0) {
                fail(taskId, task.getTaskType(), task.getAttemptCount(), AiErrorCode.QUEUE_TIMEOUT,
                        "Tác vụ chờ trong hàng đợi quá lâu", null, null);
            }
            return;
        }
        if (tasks.claim(taskId) == 0) {
            return;
        }

        int attempt = task.getAttemptCount();
        cache.putStatus(taskId, task.getTaskType(), AiTaskStatus.RUNNING, attempt, null);

        JsonNode input;
        try {
            input = json.readTree(task.getInputJson());
        } catch (Exception e) {
            fail(taskId, task.getTaskType(), attempt, AiErrorCode.BAD_INPUT,
                    "input_json không phải JSON hợp lệ", null, null);
            return;
        }

        if (task.getTaskType() == AiTaskType.NL_SEARCH) {
            String query = input.path("query").asText("").trim();
            if (!query.isEmpty()) {
                SubjectCatalogResolver.Catalog catalog = catalogResolver.resolve(input);
                String qHash = hashQuery(query, props.getLlm().getPromptVersion(), catalog.catalogHash());
                Optional<String> cached = cache.getNlFilter(qHash);
                if (cached.isPresent()) {
                    tasks.markSucceeded(taskId, cached.get(), null, null, null, null,
                            props.getLlm().getPromptVersion(), "{\"cache\":\"hit\"}", null, 0);
                    cache.putStatus(taskId, AiTaskType.NL_SEARCH, AiTaskStatus.SUCCEEDED, attempt, null);
                    queueFacade.publishDone(taskId, AiTaskStatus.SUCCEEDED.getValue());
                    return;
                }
                executeWithRetry(task, input, qHash);
                return;
            }
            fail(taskId, AiTaskType.NL_SEARCH, attempt, AiErrorCode.BAD_INPUT,
                    "Trường query bị rỗng", null, null);
            return;
        }

        executeWithRetry(task, input, null);
    }

    private void executeWithRetry(AiTask task, JsonNode input, String nlQueryHash) {
        String taskId = task.getTaskId();
        AiTaskType type = task.getTaskType();
        int maxAttempts = props.getOrchestrator().getMaxAttempts();
        int attempt = task.getAttemptCount();
        Set<String> excludedNodes = new HashSet<>();

        ChatSpec spec = type == AiTaskType.QUESTION_CHECK
                ? prompts.buildQuestionCheck(input)
                : prompts.buildNlSearch(input);

        for (int i = 0; i < MAX_RETRIES_PER_RUN; i++) {
            if (Thread.currentThread().isInterrupted()) {
                requeueCleanOnShutdown(taskId, type, attempt);
                return;
            }
            NodePool.Lease lease;
            try {
                lease = nodePool.acquire(Duration.ofSeconds(2), excludedNodes);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                requeueCleanOnShutdown(taskId, type, attempt);
                return;
            }

            if (lease == null) {
                if (attempt >= maxAttempts) {
                    fail(taskId, type, attempt, AiErrorCode.MAX_ATTEMPTS,
                            "Không có node Colab rảnh sau nhiều lần thử", null, null);
                    return;
                }
                if (tasks.requeue(taskId, AiErrorCode.LLM_UNREACHABLE.name(),
                        "Chưa có node Colab rảnh, đưa lại hàng đợi", maxAttempts) > 0) {
                    cache.putStatus(taskId, type, AiTaskStatus.PENDING, attempt + 1,
                            AiErrorCode.LLM_UNREACHABLE.name());
                    sleepQuiet(Duration.ofSeconds(1));
                    queueFacade.push(taskId);
                } else {
                    fail(taskId, type, attempt, AiErrorCode.MAX_ATTEMPTS,
                            "Không thể requeue do hết lượt thử", null, null);
                }
                return;
            }

            try (lease) {
                ChatResult chat = ollama.chat(lease.url(), lease.token(), spec);
                int ms = (int) chat.wallMs();
                double tps = chat.evalDurationNs() > 0
                        ? chat.evalCount() / (chat.evalDurationNs() / 1_000_000_000.0)
                        : 0.0;

                String resultJson;
                if (type == AiTaskType.QUESTION_CHECK) {
                    CheckOutcome out = parser.parseQuestionCheck(taskId, input, chat);
                    if (!out.suggestions().isEmpty()) {
                        suggestions.saveAll(out.suggestions());
                    }
                    resultJson = out.resultJson();
                } else {
                    resultJson = parser.parseNlSearch(input, chat);
                    cache.putNlFilter(nlQueryHash, resultJson, props.getLlm().getPromptVersion());
                }

                lease.success(chat.wallMs(), tps, chat.modelDigest(), chat.runtimeVersion());
                tasks.markSucceeded(taskId, resultJson, chat.rawBody(), chat.modelName(),
                        chat.modelDigest(), chat.runtimeVersion(),
                        props.getLlm().getPromptVersion(), paramsJson(type, spec),
                        usageJson(chat, lease.name()), ms);
                cache.putStatus(taskId, type, AiTaskStatus.SUCCEEDED, attempt, null);
                queueFacade.publishDone(taskId, AiTaskStatus.SUCCEEDED.getValue());
                log.info("Task {} ({}) xong sau {}ms trên {} (model={})",
                        taskId, type.getValue(), ms, lease.name(), chat.modelName());
                return;

            } catch (LlmException e) {
                if (Thread.currentThread().isInterrupted()) {
                    lease.release();
                    requeueCleanOnShutdown(taskId, type, attempt);
                    return;
                }
                if (isNodeFault(e.getCode())) {
                    lease.failure(e.getCode(), e.getMessage());
                    excludedNodes.add(lease.name());
                } else {
                    lease.release();
                }

                log.warn("Task {} thử lần {}/{} lỗi trên {}: {} - {}",
                        taskId, attempt, maxAttempts, lease.name(), e.getCode(), e.getMessage());

                if (!e.getCode().isRetryable()) {
                    fail(taskId, type, attempt, e.getCode(), e.getMessage(), e.getRawResponse(), e.isJsonValid());
                    return;
                }
                if (attempt >= maxAttempts) {
                    fail(taskId, type, attempt, AiErrorCode.MAX_ATTEMPTS,
                            "Hết " + maxAttempts + " lần thử: " + e.getMessage(), e.getRawResponse(), e.isJsonValid());
                    return;
                }
                if (i + 1 < MAX_RETRIES_PER_RUN
                        && tasks.retryInPlace(taskId, e.getCode().name(), trim500(e.getMessage()), maxAttempts) > 0) {
                    attempt++;
                    cache.putStatus(taskId, type, AiTaskStatus.RUNNING, attempt, e.getCode().name());
                    continue;
                }
                if (tasks.requeue(taskId, e.getCode().name(), trim500(e.getMessage()), maxAttempts) > 0) {
                    cache.putStatus(taskId, type, AiTaskStatus.PENDING, attempt + 1, e.getCode().name());
                    queueFacade.push(taskId);
                } else {
                    fail(taskId, type, attempt, AiErrorCode.MAX_ATTEMPTS,
                            "Hết lượt thử: " + e.getMessage(), e.getRawResponse(), e.isJsonValid());
                }
                return;
            } catch (Exception e) {
                lease.release();
                log.error("Task {} lỗi nội bộ", taskId, e);
                fail(taskId, type, attempt, AiErrorCode.INTERNAL_ERROR, e.getMessage(), null, null);
                return;
            }
        }
    }

    private void requeueCleanOnShutdown(String taskId, AiTaskType type, int attempt) {
        if (tasks.requeueOnShutdown(taskId) > 0) {
            cache.putStatus(taskId, type, AiTaskStatus.PENDING, attempt, null);
            queueFacade.push(taskId);
            log.info("Task {} được trả về hàng đợi pending do worker tắt êm", taskId);
        }
    }

    private void fail(String taskId, AiTaskType type, int attempt, AiErrorCode code,
                      String msg, String raw, Boolean jsonValid) {
        tasks.markFailed(taskId, code.name(), trim500(msg), raw, jsonValid);
        cache.putStatus(taskId, type, AiTaskStatus.FAILED, attempt, code.name());
        queueFacade.publishDone(taskId, AiTaskStatus.FAILED.getValue());
        log.warn("Task {} thất bại [{}]: {}", taskId, code.name(), msg);
    }

    private static boolean isNodeFault(AiErrorCode code) {
        return code == AiErrorCode.LLM_UNREACHABLE
                || code == AiErrorCode.LLM_TIMEOUT
                || code == AiErrorCode.LLM_UNAUTHORIZED
                || code == AiErrorCode.LLM_HTTP_5XX;
    }

    private String paramsJson(AiTaskType type, ChatSpec spec) {
        try {
            ObjectNode n = json.createObjectNode();
            n.put("num_ctx", spec.options().getNumCtx());
            n.put("num_predict", spec.options().getNumPredict());
            n.put("temperature", spec.options().getTemperature());
            if (spec.options().getSeed() != null) {
                n.put("seed", spec.options().getSeed());
            }
            if (type == AiTaskType.QUESTION_CHECK) {
                n.put("mode", props.getLlm().getQuestionCheckMode());
                n.put("rule_preflight", props.getLlm().isRulePreflight());
            }
            n.put("prompt_version", props.getLlm().getPromptVersion());
            return json.writeValueAsString(n);
        } catch (Exception e) {
            return null;
        }
    }

    private String usageJson(ChatResult c, String nodeName) {
        try {
            ObjectNode n = json.createObjectNode();
            n.put("node", nodeName);
            n.put("prompt_tokens", c.promptEvalCount());
            n.put("eval_tokens", c.evalCount());
            n.put("eval_duration_ms", TimeUnit.NANOSECONDS.toMillis(c.evalDurationNs()));
            n.put("total_duration_ms", TimeUnit.NANOSECONDS.toMillis(c.totalDurationNs()));
            if (c.doneReason() != null && !c.doneReason().isBlank()) {
                n.put("done_reason", c.doneReason());
            }
            return json.writeValueAsString(n);
        } catch (Exception e) {
            return null;
        }
    }

    static String hashQuery(String query, String promptVersion, String catalogHash) {
        String norm = query.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT)
                + "|" + promptVersion + "|" + catalogHash;
        try {
            byte[] dig = MessageDigest.getInstance("SHA-256").digest(norm.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : dig) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String trim500(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 500 ? s : s.substring(0, 500);
    }

    private static void sleepQuiet(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
