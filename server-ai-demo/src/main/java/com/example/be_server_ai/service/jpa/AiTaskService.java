package com.example.be_server_ai.service.jpa;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.example.be_server_ai.config.AiProperties;
import com.example.be_server_ai.enums.AiErrorCode;
import com.example.be_server_ai.enums.AiTaskStatus;
import com.example.be_server_ai.enums.AiTaskType;
import com.example.be_server_ai.model.jpa.AiSuggestion;
import com.example.be_server_ai.model.jpa.AiTask;
import com.example.be_server_ai.model.jpa.IssueType;
import com.example.be_server_ai.repository.jpa.AiSuggestionRepository;
import com.example.be_server_ai.repository.jpa.AiTaskRepository;
import com.example.be_server_ai.repository.jpa.IssueTypeRepository;
import com.example.be_server_ai.service.llm.SuggestionDraft;
import com.example.be_server_ai.service.redis.AiCacheService;
import com.example.be_server_ai.service.redis.AiQueueService;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Truy cập ntt_ai_tasks, ntt_ai_suggestions, ntt_issue_types. Server AI chỉ đổi các cột thực thi của task
 * (xem AiTaskRepository) và INSERT đề xuất. Nhóm create* chỉ phục vụ demo: Server AI đóng vai Server 1
 * (INSERT task pending, commit, rồi LPUSH ai:queue).
 */
@Service
public class AiTaskService {

    private static final Logger log = LoggerFactory.getLogger(AiTaskService.class);
    private static final long ISSUE_TYPE_TTL_MS = 60_000;
    private static final String[] BURST_SUBJECTS = {"TOEIC", "IELTS", "ngữ pháp", "từ vựng", "đọc hiểu"};
    private static final String[] BURST_LEVELS = {"dễ", "trung bình", "khó"};
    private static final String[] BURST_WHERE = {"dòng câu hỏi", "đáp án"};

    private final AiTaskRepository tasks;
    private final AiSuggestionRepository suggestions;
    private final IssueTypeRepository issueTypes;
    private final AiQueueService queue;
    private final AiCacheService cache;
    private final AiProperties props;
    private final ObjectMapper om;

    private volatile List<String> issueTypeCodes = List.of();
    private volatile long issueTypeLoadedAt;

    public AiTaskService(AiTaskRepository tasks, AiSuggestionRepository suggestions, IssueTypeRepository issueTypes,
                         AiQueueService queue, AiCacheService cache, AiProperties props, ObjectMapper om) {
        this.tasks = tasks;
        this.suggestions = suggestions;
        this.issueTypes = issueTypes;
        this.queue = queue;
        this.cache = cache;
        this.props = props;
        this.om = om;
    }

    // ------------------------------------------------------------------ tạo task (demo, vai trò Server 1)

    public AiTask createQuestionCheck(Long questionId, Long revisionId, String stem, String a, String b, String c,
                                      String d, String correct, Long createdBy) {
        if (questionId == null || revisionId == null) {
            throw new IllegalArgumentException("question_check cần questionId và questionRevisionId có thật trong CSDL.");
        }
        ObjectNode in = om.createObjectNode();
        in.put("stem", requireText(stem, "stem"));
        in.put("option_a", requireText(a, "optionA"));
        in.put("option_b", requireText(b, "optionB"));
        in.put("option_c", requireText(c, "optionC"));
        in.put("option_d", requireText(d, "optionD"));
        String label = requireText(correct, "correctLabel").toUpperCase();
        if (!label.matches("[ABCD]")) {
            throw new IllegalArgumentException("correctLabel phải là A, B, C hoặc D.");
        }
        in.put("correct_label", label);
        in.put("content_format", "plain");
        return create(AiTaskType.QUESTION_CHECK, questionId, revisionId, in, createdBy);
    }

    public AiTask createNlSearch(String query, Long createdBy) {
        String q = requireText(query, "query");
        if (q.length() > 500) {
            throw new IllegalArgumentException("query dài quá 500 ký tự.");
        }
        ObjectNode in = om.createObjectNode();
        in.put("query", q);
        return create(AiTaskType.NL_SEARCH, null, null, in, createdBy);
    }

    /** Tạo count task nl_search khác nhau (tối đa 60) để xem việc chia tải giữa các node. */
    public List<AiTask> createBurst(int count, Long createdBy) {
        int n = Math.max(1, Math.min(60, count));
        List<AiTask> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String q = "Bộ đề " + BURST_SUBJECTS[i % 5] + " cấp độ " + BURST_LEVELS[(i / 5) % 3]
                    + " ở " + BURST_WHERE[(i / 15) % 2] + " part " + (1 + (i / 30) % 7);
            out.add(createNlSearch(q, createdBy));
        }
        return out;
    }

    /** INSERT task pending. Sau khi commit mới ghi cache và LPUSH ai:queue (worker không bao giờ thấy task chưa commit). */
    @Transactional
    public AiTask create(AiTaskType type, Long questionId, Long revisionId, ObjectNode input, Long createdBy) {
        long by = createdBy != null ? createdBy : props.getDemo().getCreatedBy();
        AiTask t = new AiTask(UUID.randomUUID().toString(), type, questionId, revisionId, input.toString(), by);
        tasks.saveAndFlush(t);
        String id = t.getTaskId();
        Runnable afterCommit = () -> {
            cache.putStatus(id, type, AiTaskStatus.PENDING, 1, null);
            queue.push(id);
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    afterCommit.run();
                }
            });
        } else {
            afterCommit.run();
        }
        return t;
    }

    // ------------------------------------------------------------------ đọc

    @Transactional(readOnly = true)
    public Optional<AiTask> find(String id) {
        return tasks.findById(id);
    }

    @Transactional(readOnly = true)
    public List<AiTask> recent() {
        return tasks.findTop30ByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<AiSuggestion> suggestionsOf(String taskId) {
        return suggestions.findByTaskIdOrderBySeqNo(taskId);
    }

    /** Các issue_type hợp lệ (đọc ntt_issue_types, nhớ 60 giây). */
    public List<String> issueTypeCodes() {
        long now = System.currentTimeMillis();
        if (issueTypeCodes.isEmpty() || now - issueTypeLoadedAt > ISSUE_TYPE_TTL_MS) {
            issueTypeCodes = issueTypes.findAll().stream().map(IssueType::getIssueType).toList();
            issueTypeLoadedAt = now;
        }
        return issueTypeCodes;
    }

    // ------------------------------------------------------------------ chuyển trạng thái (Server 2)

    /** pending -> running. Trả task nếu giành được, rỗng nếu không còn pending. */
    @Transactional
    public Optional<AiTask> claim(String id) {
        if (tasks.claim(id) == 0) {
            return Optional.empty();
        }
        return tasks.findById(id);
    }

    /** running -> succeeded và INSERT các đề xuất, trong một transaction ngắn. false nếu task không còn running. */
    @Transactional
    public boolean complete(String id, String resultJson, String raw, String model, String promptVersion,
                            String paramsJson, String usageJson, Integer inferenceMs, List<SuggestionDraft> drafts) {
        if (tasks.markSucceeded(id, resultJson, raw, model, promptVersion, paramsJson, usageJson, inferenceMs) == 0) {
            return false;
        }
        int seq = 1;
        for (SuggestionDraft d : drafts) {
            suggestions.save(new AiSuggestion(id, seq++, d.issueType(), d.severity(), d.targetField(),
                    d.originalText(), null, null, d.explanation(), d.proposedText(), null));
        }
        return true;
    }

    @Transactional
    public boolean fail(String id, AiErrorCode code, String message, String raw) {
        return tasks.markFailed(id, code.name(), cut(message, 500), raw, raw == null ? null : Boolean.FALSE) == 1;
    }

    @Transactional
    public boolean retryInPlace(String id, AiErrorCode code, String message) {
        return tasks.retryInPlace(id, code.name(), cut(message, 500), props.getOrchestrator().getMaxAttempts()) == 1;
    }

    @Transactional
    public boolean requeue(String id, AiErrorCode code, String message) {
        return tasks.requeue(id, code.name(), cut(message, 500), props.getOrchestrator().getMaxAttempts()) == 1;
    }

    /**
     * Quét task running quá stuck-after (worker chết giữa chừng): trả về pending nếu còn lượt thử, ngược lại failed.
     * Mỗi lệnh UPDATE tự có transaction (trên repository) và chỉ thắng khi status vẫn là running.
     * Trả về id các task cần LPUSH lại vào hàng đợi.
     */
    public List<String> sweepStuck() {
        AiProperties.Orchestrator o = props.getOrchestrator();
        List<String> back = new ArrayList<>();
        for (String id : tasks.findStuckRunningIds(o.getStuckAfter().toSeconds())) {
            if (tasks.requeue(id, AiErrorCode.LLM_TIMEOUT.name(), "Task kẹt ở running quá lâu, trả về hàng đợi.",
                    o.getMaxAttempts()) == 1) {
                back.add(id);
                cache.putStatus(id, null, AiTaskStatus.PENDING, null, AiErrorCode.LLM_TIMEOUT.name());
            } else if (tasks.markFailed(id, AiErrorCode.MAX_ATTEMPTS.name(),
                    "Task kẹt ở running và đã hết số lần thử.", null, null) == 1) {
                cache.putStatus(id, null, AiTaskStatus.FAILED, null, AiErrorCode.MAX_ATTEMPTS.name());
                queue.publishDone(id, AiTaskStatus.FAILED.getValue());
            }
        }
        if (!back.isEmpty()) {
            log.warn("Sweeper trả {} task kẹt về hàng đợi", back.size());
        }
        return back;
    }

    private static String requireText(String v, String field) {
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Thiếu " + field + ".");
        }
        return v.trim();
    }

    private static String cut(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
