package com.example.be_web_admin.service.qbank;

import com.example.be_web_admin.dto.qbank.ActivityItem;
import com.example.be_web_admin.dto.qbank.AiJobView;
import com.example.be_web_admin.dto.qbank.AiSuggestionView;
import com.example.be_web_admin.enums.ai.TaskStatusD;
import com.example.be_web_admin.enums.ai.TaskTypeD;
import com.example.be_web_admin.enums.qbank.QuestionStatusD;
import com.example.be_web_admin.exception.qbank.QbankException;
import com.example.be_web_admin.model.jpa.ai.AiSuggestion;
import com.example.be_web_admin.model.jpa.ai.AiTask;
import com.example.be_web_admin.model.jpa.ai.IssueType;
import com.example.be_web_admin.model.jpa.qbank.Question;
import com.example.be_web_admin.model.jpa.qbank.QuestionRevision;
import com.example.be_web_admin.model.redis.UiAiJobView;
import com.example.be_web_admin.repository.jpa.ai.AiSuggestionRepository;
import com.example.be_web_admin.repository.jpa.ai.AiTaskRepository;
import com.example.be_web_admin.repository.jpa.ai.IssueTypeRepository;
import com.example.be_web_admin.repository.jpa.qbank.QuestionRepository;
import com.example.be_web_admin.repository.jpa.qbank.QuestionRevisionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Gửi yêu cầu kiểm định AI và theo dõi tiến trình cho giao diện.
 *
 * <p>Luồng: kiểm tra câu hỏi + revision hiện tại -> chống bấm liên tục bằng khoá Redis ->
 * gọi Server 2 -> lưu ảnh chụp trạng thái vào Redis cho modal polling. Sự thật vẫn nằm ở
 * ntt_ai_tasks; Redis chỉ là bản sao có TTL và được làm mới khi quá cũ.</p>
 */
@Slf4j
@Service
public class AiCheckService {

    /** Trạng thái còn sống — khớp cột sinh active_flag. */
    private static final List<TaskStatusD> ACTIVE_STATUSES = List.of(TaskStatusD.PENDING, TaskStatusD.RUNNING);
    private static final List<TaskStatusD> TERMINAL_STATUSES = List.of(
            TaskStatusD.SUCCEEDED, TaskStatusD.FAILED, TaskStatusD.CANCELLED);

    /** Ảnh chụp Redis cũ hơn mốc này thì đọc lại MariaDB (worker ghi DB, không ghi Redis này). */
    private static final Duration CACHE_STALE_AFTER = Duration.ofSeconds(5);
    private static final int POLL_INTERVAL_MS = 1500;

    private final QuestionRepository questions;
    private final QuestionRevisionRepository revisions;
    private final AiTaskRepository tasks;
    private final AiSuggestionRepository suggestions;
    private final IssueTypeRepository issueTypes;
    private final AiWorkerClient worker;
    private final UiStateStore uiState;

    public AiCheckService(QuestionRepository questions,
                          QuestionRevisionRepository revisions,
                          AiTaskRepository tasks,
                          AiSuggestionRepository suggestions,
                          IssueTypeRepository issueTypes,
                          AiWorkerClient worker,
                          UiStateStore uiState) {
        this.questions = questions;
        this.revisions = revisions;
        this.tasks = tasks;
        this.suggestions = suggestions;
        this.issueTypes = issueTypes;
        this.worker = worker;
        this.uiState = uiState;
    }

    /**
     * Tạo task kiểm định cho revision mới nhất. Idempotent: nếu đã có task pending/running cho
     * câu này thì trả luôn task đó (bấm 2 lần không sinh 2 task).
     */
    // KHÔNG bọc @Transactional: luồng này gọi HTTP sang Server 2 (có thể tới 20 giây) và không
    // ghi dòng nào vào MariaDB, nên giữ transaction chỉ tổ giữ kết nối Hikari vô ích.
    public AiJobView submit(Long questionId, Long adminId) {
        Question question = questions.findById(questionId)
                .orElseThrow(() -> QbankException.notFound("QUESTION_NOT_FOUND",
                        "Không tìm thấy câu hỏi " + questionId));
        if (question.getStatus() == QuestionStatusD.ARCHIVED) {
            throw QbankException.conflict("QUESTION_ARCHIVED", "Câu hỏi đã lưu trữ, không kiểm định.");
        }

        // D3: task question_check bắt buộc có cặp (question_id, question_revision_id) hợp lệ.
        QuestionRevision revision = revisions.findTopByQuestionIdOrderByRevNoDesc(questionId)
                .orElseThrow(() -> QbankException.conflict("QUESTION_WITHOUT_REVISION",
                        "Câu hỏi chưa có revision nào — không thể kiểm định."));

        Optional<AiTask> active = tasks.findFirstByQuestionIdAndTaskTypeAndStatusIn(
                questionId, TaskTypeD.QUESTION_CHECK, ACTIVE_STATUSES);
        if (active.isPresent()) {
            UiAiJobView existing = uiState.readJobView(active.get().getTaskId())
                    .orElseGet(() -> fromTask(active.get()));
            return toView(existing, true);
        }

        if (!uiState.tryLockCheck(questionId)) {
            throw QbankException.tooManyRequests("CHECK_IN_PROGRESS",
                    "Đang gửi yêu cầu kiểm định cho câu này, vui lòng đợi vài giây.");
        }

        boolean submitted = false;
        try {
            AiWorkerClient.WorkerTask workerTask = worker.submitQuestionCheck(question, revision, adminId);
            Instant now = Instant.now();
            UiAiJobView view = new UiAiJobView(
                    workerTask.taskId(),
                    questionId,
                    TaskTypeD.QUESTION_CHECK.dbValue(),
                    normalizeStatus(workerTask.status()),
                    1,
                    0,
                    null,
                    now,
                    now,
                    POLL_INTERVAL_MS);
            uiState.saveJobView(view);
            uiState.pushActivity(adminId, ActivityItem.of(
                    "AI_CHECK_SUBMITTED",
                    "Gửi kiểm định AI cho câu #" + questionId + " (rev " + revision.getRevNo() + ")",
                    "QUESTION", questionId));
            uiState.evictDashboard(question.getSubjectId());
            submitted = true;
            return toView(view, true);
        } finally {
            if (!submitted) {
                // Gọi worker thất bại: mở khoá ngay để người dùng bấm lại được.
                uiState.unlockCheck(questionId);
            }
        }
    }

    /**
     * Trạng thái task cho UI polling. Redis trước; nếu ảnh chụp còn "đang chạy" và đã cũ hơn
     * 5 giây thì đọc lại MariaDB rồi làm mới Redis — nhờ vậy Server 2 không cần ghi Redis của
     * Server 1 mà giao diện vẫn thấy tiến trình.
     */
    @Transactional(readOnly = true)
    public AiJobView job(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw QbankException.badRequest("TASK_ID_REQUIRED", "Thiếu taskId.");
        }
        Optional<UiAiJobView> cached = uiState.readJobView(taskId);
        if (cached.isPresent() && !shouldRefresh(cached.get())) {
            return toView(cached.get(), true);
        }

        AiTask task = tasks.findById(taskId)
                .orElseThrow(() -> QbankException.notFound("TASK_NOT_FOUND",
                        "Không tìm thấy task AI " + taskId));
        UiAiJobView fresh = fromTask(task);
        uiState.saveJobView(fresh);
        return toView(fresh, cached.isPresent());
    }

    /** Đề xuất của chính task này (modal tiến trình mở sang bảng duyệt). */
    @Transactional(readOnly = true)
    public List<AiSuggestionView> suggestions(String taskId) {
        AiTask task = tasks.findById(taskId)
                .orElseThrow(() -> QbankException.notFound("TASK_NOT_FOUND",
                        "Không tìm thấy task AI " + taskId));
        if (task.getTaskType() != TaskTypeD.QUESTION_CHECK) {
            return List.of();
        }
        Map<String, String> labels = issueTypes.findAll().stream()
                .collect(Collectors.toMap(IssueType::getIssueType, IssueType::getLabelVi,
                        (left, right) -> left, LinkedHashMap::new));
        List<AiSuggestion> list = suggestions.findByTaskIdOrderBySeqNoAsc(taskId);
        List<AiSuggestionView> views = new ArrayList<>(list.size());
        for (AiSuggestion s : list) {
            views.add(new AiSuggestionView(
                    s.getSuggestionId(),
                    s.getSeqNo(),
                    s.getIssueType(),
                    labels.getOrDefault(s.getIssueType(), s.getIssueType()),
                    s.getSeverity() == null ? null : s.getSeverity().dbValue(),
                    s.getTargetField() == null ? null : s.getTargetField().dbValue(),
                    s.getOriginalText(),
                    s.getSpanStart(),
                    s.getSpanEnd(),
                    s.getExplanation(),
                    s.getProposedText(),
                    s.getSuggestedDifficulty(),
                    s.getDecision() == null ? null : s.getDecision().dbValue(),
                    s.getDecidedAt(),
                    s.getRejectReason(),
                    s.getFinalText()));
        }
        return views;
    }

    private UiAiJobView fromTask(AiTask task) {
        int suggestionCount = task.getTaskType() == TaskTypeD.QUESTION_CHECK
                ? (int) suggestions.countByTaskId(task.getTaskId())
                : 0;
        Instant updated = task.getFinishedAt() != null
                ? task.getFinishedAt()
                : task.getStartedAt() != null ? task.getStartedAt() : task.getCreatedAt();
        return new UiAiJobView(
                task.getTaskId(),
                task.getQuestionId(),
                task.getTaskType() == null ? null : task.getTaskType().dbValue(),
                task.getStatus() == null ? null : task.getStatus().dbValue(),
                task.getAttemptCount(),
                suggestionCount,
                task.getErrorCode(),
                task.getCreatedAt(),
                updated == null ? Instant.now() : updated,
                isTerminal(task.getStatus()) ? 0 : POLL_INTERVAL_MS);
    }

    private static boolean isTerminal(TaskStatusD status) {
        return status != null && TERMINAL_STATUSES.contains(status);
    }

    private static boolean shouldRefresh(UiAiJobView view) {
        if (view.getStatus() == null) {
            return true;
        }
        TaskStatusD status;
        try {
            status = TaskStatusD.fromDbValue(view.getStatus());
        } catch (IllegalArgumentException e) {
            return true;
        }
        if (isTerminal(status)) {
            return false;
        }
        Instant updated = view.getUpdatedAt() == null ? Instant.EPOCH : view.getUpdatedAt();
        return updated.isBefore(Instant.now().minus(CACHE_STALE_AFTER));
    }

    private static String normalizeStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return TaskStatusD.PENDING.dbValue();
        }
        try {
            return TaskStatusD.fromDbValue(raw.trim().toLowerCase(Locale.ROOT)).dbValue();
        } catch (IllegalArgumentException e) {
            return TaskStatusD.PENDING.dbValue();
        }
    }

    private static AiJobView toView(UiAiJobView view, boolean fromCache) {
        return new AiJobView(
                view.getTaskId(),
                view.getQuestionId(),
                view.getTaskType(),
                view.getStatus(),
                view.getAttemptCount(),
                view.getSuggestionCount() == null ? 0 : view.getSuggestionCount(),
                view.getErrorCode(),
                view.getCreatedAt(),
                view.getUpdatedAt(),
                view.getPollAfterMs() == null ? 0 : view.getPollAfterMs(),
                fromCache);
    }
}
