package com.example.server_ai_worker.demo;

import com.example.server_ai_worker.enums.AiErrorCode;
import com.example.server_ai_worker.enums.SuggestionSeverity;
import com.example.server_ai_worker.model.jpa.AiSuggestion;
import com.example.server_ai_worker.service.ai.AiPromptFactory;
import com.example.server_ai_worker.service.ai.AiResultParser;
import com.example.server_ai_worker.service.ai.ChatResult;
import com.example.server_ai_worker.service.ai.CheckOutcome;
import com.example.server_ai_worker.service.ai.LlmException;
import com.example.server_ai_worker.service.ai.NodePool;
import com.example.server_ai_worker.service.ai.OllamaClient;
import com.example.server_ai_worker.service.ai.RulePreflightChecker;
import com.example.server_ai_worker.service.ai.ChatSpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Module đánh giá DEMO (không tích hợp vào pipeline production):
 * chạy bộ phân loại lỗi câu hỏi trên 1000 câu demo, so với gold label, và tổng hợp metrics.
 *
 * <p>Quy tắc gán predicted label:
 * <ol>
 *   <li>Rule-only ({@code rule}): chỉ dùng RulePreflightChecker tất định
 *       (trùng phương án → distractor; rỗng/lộ đáp án → logic; không phát hiện → none).</li>
 *   <li>AI ({@code ai}): tiền kiểm tất định + Ollama trên Colab (giống hệt question_check pipeline);
 *       nếu kết quả có suggestion thì lấy suggestion có severity cao nhất (hoà thì theo seq_no);
 *       {@code key_doubt} được quy đổi thành {@code logic} vì gold taxonomy không có key_doubt;
 *       không có suggestion nào → {@code none}.</li>
 *   <li>Câu lỗi (LLM unreachable/schema…) ghi predicted_label = {@code error} và bị loại khỏi
 *       accuracy/confusion (đếm riêng trong error_count).</li>
 * </ol>
 */
@Service
public class DemoEvalService {

    public static final List<String> LABELS = List.of("none", "spelling", "grammar", "logic", "distractor");
    private static final int MAX_AI_ATTEMPTS = 2;
    private static final int MAX_CONSECUTIVE_NO_NODE = 5;

    private final DemoQuestionRepository questions;
    private final DemoEvalRunRepository runs;
    private final DemoEvalItemRepository items;
    private final AiPromptFactory prompts;
    private final OllamaClient ollama;
    private final AiResultParser parser;
    private final NodePool nodePool;
    private final RulePreflightChecker preflight;
    private final ObjectMapper json;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "demo-eval-runner");
        t.setDaemon(true);
        return t;
    });
    private final Map<Long, AtomicBoolean> stopFlags = new ConcurrentHashMap<>();

    public DemoEvalService(DemoQuestionRepository questions,
                           DemoEvalRunRepository runs,
                           DemoEvalItemRepository items,
                           AiPromptFactory prompts,
                           OllamaClient ollama,
                           AiResultParser parser,
                           NodePool nodePool,
                           RulePreflightChecker preflight,
                           ObjectMapper json) {
        this.questions = questions;
        this.runs = runs;
        this.items = items;
        this.prompts = prompts;
        this.ollama = ollama;
        this.parser = parser;
        this.nodePool = nodePool;
        this.preflight = preflight;
        this.json = json;
    }

    @PreDestroy
    void shutdown() {
        stopFlags.values().forEach(f -> f.set(true));
        executor.shutdownNow();
    }

    public record StartResult(DemoEvalRun run, String message) {
    }

    /** Tạo một lần chạy mới. Ném IllegalStateException nếu đã có run đang chạy. */
    @Transactional
    public DemoEvalRun start(String mode, Integer limit) {
        String m = mode == null ? "rule" : mode.trim().toLowerCase();
        if (!m.equals("rule") && !m.equals("ai")) {
            throw new IllegalArgumentException("mode phải là 'rule' hoặc 'ai'.");
        }
        if ("ai".equals(m) && !nodePool.hasEnabledNode()) {
            throw new IllegalStateException("Không có node Colab nào đang bật — không thể chạy chế độ AI.");
        }
        if (runs.countRunning() > 0) {
            throw new IllegalStateException("Đã có một run đang chạy. Hãy chờ xong hoặc bấm Dừng.");
        }
        long available = questions.count();
        if (available == 0) {
            throw new IllegalStateException("Bảng ntt_demo_questions trống — chưa seed câu hỏi demo.");
        }
        int total = limit == null ? (int) available : Math.max(1, Math.min(limit, (int) available));
        DemoEvalRun run = new DemoEvalRun(m, total);
        run = runs.saveAndFlush(run);
        stopFlags.put(run.getId(), new AtomicBoolean(false));
        final long runId = run.getId();
        executor.submit(() -> execute(runId, m, total));
        return run;
    }

    /** Yêu cầu dừng một run đang chạy (cooperative stop giữa hai câu). */
    public boolean stop(long runId) {
        AtomicBoolean flag = stopFlags.get(runId);
        if (flag != null) {
            flag.set(true);
            return true;
        }
        Optional<DemoEvalRun> opt = runs.findById(runId);
        if (opt.isPresent() && "running".equals(opt.get().getStatus())) {
            DemoEvalRun r = opt.get();
            r.setStatus("stopped");
            r.setFinishedAt(Instant.now());
            r.setNotes("Run bị dừng thủ công (worker không kiểm soát được tiểu trình chạy).");
            runs.save(r);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Vòng đời một run
    // ------------------------------------------------------------------
    private void execute(long runId, String mode, int limit) {
        DemoEvalRun run = runs.findById(runId).orElse(null);
        if (run == null) {
            return;
        }
        int processed = 0;
        int correct = 0;
        int errors = 0;
        int consecutiveNoNode = 0;
        try {
            List<DemoQuestion> all = questions.findAllByOrderByExtIdAsc();
            for (DemoQuestion q : all) {
                if (processed >= limit) {
                    break;
                }
                AtomicBoolean stop = stopFlags.get(runId);
                if (stop != null && stop.get()) {
                    run.setStatus("stopped");
                    break;
                }
                EvalOutcome out = "ai".equals(mode) ? evalWithAi(runId, q) : evalWithRules(q);
                if (out.noNode()) {
                    consecutiveNoNode++;
                    if (consecutiveNoNode >= MAX_CONSECUTIVE_NO_NODE) {
                        run.setStatus("failed");
                        run.setNotes("Không nhận được node Colab sau " + MAX_CONSECUTIVE_NO_NODE
                                + " lần liên tiếp — run hủy. Kiểm tra kết nối các node ở trang node-colab.");
                        break;
                    }
                    sleepQuiet(2000);
                    continue; // không tăng processed, thử lại câu này
                }
                consecutiveNoNode = 0;

                DemoEvalItem item = new DemoEvalItem(runId, q.getId(), q.getExtId(),
                        q.getSubjectCode(), q.getGoldIssueType());
                item.setPredictedLabel(out.predictedLabel());
                item.setRawPredictedIssue(out.rawIssue());
                item.setSuggestionCount(out.suggestionCount());
                item.setTopSeverity(out.topSeverity());
                item.setLatencyMs(out.latencyMs());
                item.setModelName(out.modelName());
                item.setNodeName(out.nodeName());
                item.setErrorCode(out.errorCode());
                boolean ok = out.errorCode() == null && out.predictedLabel().equals(q.getGoldIssueType());
                item.setPredictionCorrect(ok);
                items.save(item);

                processed++;
                if (ok) {
                    correct++;
                }
                if (out.errorCode() != null) {
                    errors++;
                }
                run.setProcessedCount(processed);
                run.setCorrectCount(correct);
                run.setErrorCount(errors);
                runs.save(run);
            }
            if (!"stopped".equals(run.getStatus()) && !"failed".equals(run.getStatus())) {
                run.setStatus("done");
            }
        } catch (Exception e) {
            run.setStatus("failed");
            run.setNotes("Lỗi nội bộ khi chạy đánh giá: " + e.getMessage());
        } finally {
            run.setFinishedAt(Instant.now());
            if ("done".equals(run.getStatus()) || "stopped".equals(run.getStatus()) || "failed".equals(run.getStatus())) {
                persistMetrics(run);
            }
            runs.save(run);
            stopFlags.remove(runId);
        }
    }

    // ------------------------------------------------------------------
    // Chấm từng câu
    // ------------------------------------------------------------------
    private record EvalOutcome(String predictedLabel, String rawIssue, int suggestionCount,
                               String topSeverity, long latencyMs, String modelName,
                               String nodeName, String errorCode, boolean noNode) {
        static EvalOutcome noNode() {
            return new EvalOutcome("error", null, 0, null, 0L, null, null, "LLM_UNREACHABLE", true);
        }

        static EvalOutcome error(String code, long latencyMs) {
            return new EvalOutcome("error", null, 0, null, latencyMs, null, null, code, false);
        }
    }

    private EvalOutcome evalWithRules(DemoQuestion q) {
        long t0 = System.currentTimeMillis();
        JsonNode input = buildInput(q);
        List<RulePreflightChecker.Finding> findings = preflight.check(input);
        if (findings.isEmpty()) {
            return new EvalOutcome("none", null, 0, null,
                    System.currentTimeMillis() - t0, null, "rule-preflight", null, false);
        }
        RulePreflightChecker.Finding top = findings.stream()
                .max(Comparator.comparingInt(f -> severityRank(f.severity())))
                .orElse(findings.get(0));
        return new EvalOutcome(top.issueType(), top.issueType(), findings.size(),
                top.severity().name().toLowerCase(),
                System.currentTimeMillis() - t0, null, "rule-preflight", null, false);
    }

    private EvalOutcome evalWithAi(long runId, DemoQuestion q) {
        long t0 = System.currentTimeMillis();
        JsonNode input = buildInput(q);
        ChatSpec spec = prompts.buildQuestionCheck(input);
        Set<String> excluded = new HashSet<>();
        LlmException lastLlm = null;

        for (int attempt = 1; attempt <= MAX_AI_ATTEMPTS; attempt++) {
            NodePool.Lease lease = null;
            try {
                lease = nodePool.acquire(Duration.ofSeconds(2), excluded);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return EvalOutcome.error("INTERNAL_ERROR", System.currentTimeMillis() - t0);
            }
            if (lease == null) {
                lastLlm = new LlmException(AiErrorCode.LLM_UNREACHABLE, "Không có node rảnh", null, true);
                continue; // thử lại theo lượt (hoặc báo noNode ở ngoài nếu lặp lại nhiều)
            }
            String nodeName = lease.name();
            final NodePool.Lease activeLease = lease;
            try (activeLease) {
                ChatResult chat = ollama.chat(lease.url(), lease.token(), spec);
                double tps = chat.evalDurationNs() > 0
                        ? chat.evalCount() / (chat.evalDurationNs() / 1_000_000_000.0)
                        : 0.0;
                lease.success(chat.wallMs(), tps, chat.modelDigest(), chat.runtimeVersion());

                CheckOutcome outcome = parser.parseQuestionCheck(
                        "demo-" + runId + "-" + q.getExtId(), input, chat);
                List<AiSuggestion> sug = outcome.suggestions();
                if (sug.isEmpty()) {
                    return new EvalOutcome("none", null, 0, null,
                            System.currentTimeMillis() - t0, chat.modelName(), nodeName, null, false);
                }
                AiSuggestion top = sug.stream()
                        .min(Comparator.<AiSuggestion>comparingInt(s -> -severityRank(s.getSeverity()))
                                .thenComparingInt(AiSuggestion::getSeqNo))
                        .orElse(sug.get(0));
                String raw = top.getIssueType();
                String predicted = "key_doubt".equals(raw) ? "logic" : raw;
                long latency = System.currentTimeMillis() - t0;
                return new EvalOutcome(predicted, raw, sug.size(),
                        top.getSeverity() == null ? null : top.getSeverity().name().toLowerCase(),
                        latency, chat.modelName(), nodeName, null, false);

            } catch (LlmException e) {
                lastLlm = e;
                if (isNodeFault(e.getCode()) || e.getCode().isRetryable()) {
                    // Node chịu lỗi (hoặc trả output không parse được): loại khỏi câu này ở lượt kế tiếp.
                    lease.failure(e.getCode(), e.getMessage());
                    excluded.add(nodeName);
                } else {
                    lease.release();
                }
                if (!e.getCode().isRetryable()) {
                    return EvalOutcome.error(e.getCode().name(), System.currentTimeMillis() - t0);
                }
            } catch (Exception e) {
                lease.release();
                return EvalOutcome.error("INTERNAL_ERROR", System.currentTimeMillis() - t0);
            }
        }

        if (lastLlm != null && lastLlm.getCode() == AiErrorCode.LLM_UNREACHABLE && excluded.isEmpty()) {
            // Không lấy được lease nào (toàn node bận/tắt)
            return EvalOutcome.noNode();
        }
        String code = lastLlm != null ? lastLlm.getCode().name() : "LLM_UNREACHABLE";
        return EvalOutcome.error(code, System.currentTimeMillis() - t0);
    }

    private JsonNode buildInput(DemoQuestion q) {
        ObjectNode o = json.createObjectNode();
        o.put("stem", q.getStem());
        o.put("option_a", q.getOptionA());
        o.put("option_b", q.getOptionB());
        o.put("option_c", q.getOptionC());
        o.put("option_d", q.getOptionD());
        o.put("correct_label", q.getCorrectLabel());
        if (q.getExplanation() != null && !q.getExplanation().isBlank()) {
            o.put("explanation", q.getExplanation());
        }
        o.put("subject_code", q.getSubjectCode());
        o.put("subject_name", q.getSubjectName());
        if (q.getTopicName() != null) {
            o.put("topic_name", q.getTopicName());
        }
        o.put("difficulty", q.getDifficulty());
        return o;
    }

    private static int severityRank(SuggestionSeverity severity) {
        if (severity == null) {
            return 0;
        }
        return switch (severity.name().toUpperCase()) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    private static boolean isNodeFault(AiErrorCode code) {
        return code == AiErrorCode.LLM_UNREACHABLE
                || code == AiErrorCode.LLM_TIMEOUT
                || code == AiErrorCode.LLM_UNAUTHORIZED
                || code == AiErrorCode.LLM_HTTP_5XX;
    }

    private static void sleepQuiet(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------------
    // Metrics
    // ------------------------------------------------------------------
    public record LabelMetrics(String label, int tp, int fp, int fn,
                               double precision, double recall, double f1, int support) {
    }

    public record DomainMetrics(String subjectCode, int total, int correct, double accuracy) {
    }

    public record RunMetricsView(long runId, String mode, String status,
                                 int totalQuestions, int evaluated, int errors,
                                 double accuracy, double macroPrecision, double macroRecall,
                                 double macroF1, double microF1,
                                 List<String> labels, List<List<Integer>> confusion,
                                 List<LabelMetrics> perLabel, List<DomainMetrics> perDomain,
                                 String notes) {
    }

    /** Tính confusion matrix & metrics từ DB (luôn chính xác theo dữ liệu hiện có). */
    @Transactional(readOnly = true)
    public RunMetricsView metrics(long runId) {
        DemoEvalRun run = runs.findById(runId).orElse(null);
        if (run == null) {
            return null;
        }
        List<DemoEvalItem> list = items.findByRunIdOrderByExtIdAsc(runId);
        int n = LABELS.size();
        int[][] confusion = new int[n][n];
        int evaluated = 0, errors = 0, correct = 0;
        Map<String, int[]> perDomain = new HashMap<>();

        for (DemoEvalItem it : list) {
            String pred = it.getPredictedLabel();
            String gold = it.getGoldLabel();
            int[] d = perDomain.computeIfAbsent(it.getSubjectCode(), k -> new int[2]);
            d[0]++;
            if ("error".equals(pred)) {
                errors++;
                continue;
            }
            int gi = LABELS.indexOf(gold);
            int pi = LABELS.indexOf(pred);
            if (gi < 0 || pi < 0) {
                errors++;
                continue;
            }
            confusion[gi][pi]++;
            evaluated++;
            if (gi == pi) {
                correct++;
                d[1]++;
            }
        }

        List<LabelMetrics> perLabel = new ArrayList<>();
        double macroP = 0, macroR = 0, macroF1 = 0;
        for (int i = 0; i < n; i++) {
            int tp = confusion[i][i];
            int fp = 0, fn = 0, support = 0;
            for (int j = 0; j < n; j++) {
                if (j != i) {
                    fp += confusion[j][i];
                    fn += confusion[i][j];
                }
                support += confusion[i][j];
            }
            double p = tp + fp == 0 ? 0.0 : (double) tp / (tp + fp);
            double r = tp + fn == 0 ? 0.0 : (double) tp / (tp + fn);
            double f1 = p + r == 0 ? 0.0 : 2 * p * r / (p + r);
            macroP += p;
            macroR += r;
            macroF1 += f1;
            perLabel.add(new LabelMetrics(LABELS.get(i), tp, fp, fn, p, r, f1, support));
        }
        macroP /= n;
        macroR /= n;
        macroF1 /= n;
        double accuracy = evaluated == 0 ? 0.0 : (double) correct / evaluated;
        // Micro-F1 cho phân loại đơn nhãn = accuracy
        double microF1 = evaluated == 0 ? 0.0
                : 2.0 * correct / (2.0 * correct + (evaluated - correct) + (evaluated - correct));

        List<List<Integer>> matrix = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            List<Integer> row = new ArrayList<>();
            for (int j = 0; j < n; j++) {
                row.add(confusion[i][j]);
            }
            matrix.add(row);
        }

        List<DomainMetrics> perDomainList = new ArrayList<>();
        perDomain.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> perDomainList.add(new DomainMetrics(
                        e.getKey(), e.getValue()[0], e.getValue()[1],
                        e.getValue()[0] == 0 ? 0.0 : (double) e.getValue()[1] / e.getValue()[0])));

        return new RunMetricsView(run.getId(), run.getMode(), run.getStatus(),
                run.getTotalQuestions(), evaluated, errors,
                accuracy, macroP, macroR, macroF1, microF1,
                LABELS, matrix, perLabel, perDomainList, run.getNotes());
    }

    /** Ghi accuracy/macro/micro F1 vào run (dùng khi kết thúc run). */
    private void persistMetrics(DemoEvalRun run) {
        try {
            RunMetricsView m = metrics(run.getId());
            if (m != null) {
                run.setAccuracy(m.accuracy());
                run.setMacroF1(m.macroF1());
                run.setMicroF1(m.microF1());
            }
        } catch (Exception ignored) {
            // metrics không khả dụng — run vẫn được đóng với trạng thái đã ghi nhận lỗi
        }
    }
}
