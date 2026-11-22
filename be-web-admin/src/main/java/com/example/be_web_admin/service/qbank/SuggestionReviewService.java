package com.example.be_web_admin.service.qbank;

import com.example.be_web_admin.dto.qbank.ActivityItem;
import com.example.be_web_admin.dto.qbank.QuestionDetail;
import com.example.be_web_admin.dto.qbank.SuggestionDecisionRequest;
import com.example.be_web_admin.enums.ai.SuggestionDecisionD;
import com.example.be_web_admin.enums.ai.TargetFieldD;
import com.example.be_web_admin.enums.qbank.ChangeSourceD;
import com.example.be_web_admin.enums.qbank.ValidationStatusD;
import com.example.be_web_admin.exception.qbank.QbankException;
import com.example.be_web_admin.model.jpa.ai.AiSuggestion;
import com.example.be_web_admin.model.jpa.ai.AiTask;
import com.example.be_web_admin.model.jpa.qbank.Question;
import com.example.be_web_admin.model.jpa.qbank.QuestionRevision;
import com.example.be_web_admin.model.jpa.qbank.Subject;
import com.example.be_web_admin.model.jpa.qbank.Topic;
import com.example.be_web_admin.repository.jpa.ai.AiSuggestionRepository;
import com.example.be_web_admin.repository.jpa.ai.AiTaskRepository;
import com.example.be_web_admin.repository.jpa.qbank.QuestionRepository;
import com.example.be_web_admin.repository.jpa.qbank.QuestionRevisionRepository;
import com.example.be_web_admin.repository.jpa.qbank.SubjectRepository;
import com.example.be_web_admin.repository.jpa.qbank.TopicRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Duyệt đề xuất AI — chốt đóng góp #1 của đề án: "AI chỉ đề xuất, con người quyết định,
 * mọi thay đổi nội dung đều sinh revision và lưu vết".
 *
 * <p>Bốn ràng buộc được thực thi ở đây (và một phần ở CSDL):</p>
 * <ol>
 *   <li>Đề xuất đã xử lý thì không xử lý lại (PENDING mới được quyết).</li>
 *   <li>Chỉ áp được lên ĐÚNG revision mà AI đã đọc; câu đã bị sửa thì đề xuất thành stale (D6).</li>
 *   <li>accepted bắt buộc sinh revision mới và ghi applied_revision_id (D8).</li>
 *   <li>target_field = 'question' (toàn câu) KHÔNG tự áp — người duyệt phải sửa tay, vì AI
 *       không được đổi cấu trúc/đáp án đúng (đề cương 3.3).</li>
 * </ol>
 */
@Slf4j
@Service
public class SuggestionReviewService {

    private final AiSuggestionRepository suggestions;
    private final AiTaskRepository tasks;
    private final QuestionRepository questions;
    private final QuestionRevisionRepository revisions;
    private final SubjectRepository subjects;
    private final TopicRepository topics;
    private final QuestionSnapshotFactory snapshots;
    private final QuestionService questionService;
    private final UiStateStore uiState;

    public SuggestionReviewService(AiSuggestionRepository suggestions,
                                   AiTaskRepository tasks,
                                   QuestionRepository questions,
                                   QuestionRevisionRepository revisions,
                                   SubjectRepository subjects,
                                   TopicRepository topics,
                                   QuestionSnapshotFactory snapshots,
                                   QuestionService questionService,
                                   UiStateStore uiState) {
        this.suggestions = suggestions;
        this.tasks = tasks;
        this.questions = questions;
        this.revisions = revisions;
        this.subjects = subjects;
        this.topics = topics;
        this.snapshots = snapshots;
        this.questionService = questionService;
        this.uiState = uiState;
    }

    @Transactional
    public QuestionDetail decide(Long suggestionId, SuggestionDecisionRequest request, Long adminId) {
        AiSuggestion suggestion = suggestions.findById(suggestionId)
                .orElseThrow(() -> QbankException.notFound("SUGGESTION_NOT_FOUND",
                        "Không tìm thấy đề xuất " + suggestionId));
        if (suggestion.getDecision() != SuggestionDecisionD.PENDING) {
            throw QbankException.conflict("SUGGESTION_ALREADY_DECIDED",
                    "Đề xuất đã được xử lý (" + suggestion.getDecision().dbValue() + ").");
        }

        String decision = request.decision() == null ? "" : request.decision().trim().toLowerCase(Locale.ROOT);
        if ("rejected".equals(decision)) {
            return reject(suggestion, request, adminId);
        }
        if (!"accepted".equals(decision)) {
            throw QbankException.badRequest("INVALID_DECISION", "decision phải là accepted hoặc rejected.");
        }
        return accept(suggestion, request, adminId);
    }

    // ------------------------------------------------------------------ Từ chối

    private QuestionDetail reject(AiSuggestion suggestion, SuggestionDecisionRequest request, Long adminId) {
        suggestion.setDecision(SuggestionDecisionD.REJECTED);
        suggestion.setDecidedBy(adminId);
        suggestion.setDecidedAt(Instant.now());
        suggestion.setRejectReason(trimToNull(request.rejectReason()));
        suggestions.saveAndFlush(suggestion);

        Long questionId = questionIdOf(suggestion);
        uiState.pushActivity(adminId, ActivityItem.of(
                "AI_SUGGESTION_REJECTED",
                "Từ chối đề xuất #" + suggestion.getSuggestionId() + " (" + suggestion.getIssueType() + ")",
                "SUGGESTION", suggestion.getSuggestionId()));
        return questionService.get(questionId);
    }

    // ------------------------------------------------------------------ Chấp nhận & áp dụng

    private QuestionDetail accept(AiSuggestion suggestion, SuggestionDecisionRequest request, Long adminId) {
        AiTask task = tasks.findById(suggestion.getTaskId())
                .orElseThrow(() -> QbankException.conflict("TASK_MISSING",
                        "Task của đề xuất không còn tồn tại."));
        if (task.getQuestionId() == null || task.getQuestionRevisionId() == null) {
            throw QbankException.conflict("SUGGESTION_WITHOUT_REVISION",
                    "Đề xuất không gắn với revision nào — không thể áp dụng an toàn.");
        }

        Question question = questions.findById(task.getQuestionId())
                .orElseThrow(() -> QbankException.notFound("QUESTION_NOT_FOUND",
                        "Không tìm thấy câu hỏi " + task.getQuestionId()));

        int latestRev = revisions.findMaxRevNo(question.getQuestionId());
        QuestionRevision revision = revisions.findById(task.getQuestionRevisionId())
                .orElseThrow(() -> QbankException.conflict("REVISION_MISSING",
                        "Revision mà AI đã đọc không còn tồn tại."));
        if (revision.getRevNo() == null || revision.getRevNo() != latestRev) {
            suggestion.setDecision(SuggestionDecisionD.STALE);
            suggestions.saveAndFlush(suggestion);
            throw QbankException.conflict("SUGGESTION_STALE",
                    "Câu hỏi đã được sửa sau khi AI đọc (rev " + revision.getRevNo()
                            + " < rev " + latestRev + "). Đề xuất này đã bị đánh dấu stale.");
        }
        if (request.expectedVersion() != null
                && !Objects.equals(question.getVersion(), request.expectedVersion())) {
            throw QbankException.conflict("VERSION_CONFLICT",
                    "Câu hỏi đã bị người khác sửa (bạn giữ phiên bản " + request.expectedVersion()
                            + ", hiện tại là " + question.getVersion() + ").");
        }

        String newText = firstNonBlank(request.finalText(), suggestion.getProposedText());
        if (newText == null) {
            throw QbankException.badRequest("FINAL_TEXT_REQUIRED",
                    "Đề xuất không có proposed_text, cần nhập finalText để áp dụng.");
        }

        applyTargetField(question, suggestion.getTargetField(), newText);
        if (suggestion.getSuggestedDifficulty() != null) {
            question.setDifficulty(suggestion.getSuggestedDifficulty());
        }
        // Nội dung đổi => kết quả kiểm định cũ không còn giá trị.
        question.setValidationStatus(ValidationStatusD.STALE);
        question.setContentHash(QuestionContentHasher.hash(
                question.getStem(), question.getOptionA(), question.getOptionB(),
                question.getOptionC(), question.getOptionD(),
                question.getCorrectLabel(), question.getContentFormat()));
        question.setUpdatedBy(adminId);
        questions.saveAndFlush(question);

        Subject subject = subjects.findById(question.getSubjectId()).orElse(null);
        Topic topic = question.getTopicId() == null ? null : topics.findById(question.getTopicId()).orElse(null);
        int nextRevNo = latestRev + 1;
        QuestionRevision newRevision = new QuestionRevision();
        newRevision.setQuestionId(question.getQuestionId());
        newRevision.setRevNo(nextRevNo);
        newRevision.setSnapshotJson(snapshots.build(question, subject, topic));
        newRevision.setChangeSource(ChangeSourceD.AI_SUGGESTION);
        newRevision.setChangeNote("Áp đề xuất #" + suggestion.getSuggestionId()
                + " (" + suggestion.getIssueType() + "/" + suggestion.getTargetField().dbValue() + ")");
        newRevision.setChangedBy(adminId);
        revisions.saveAndFlush(newRevision);

        suggestion.setDecision(SuggestionDecisionD.ACCEPTED);
        suggestion.setDecidedBy(adminId);
        suggestion.setDecidedAt(Instant.now());
        suggestion.setFinalText(newText);
        suggestion.setAppliedRevisionId(newRevision.getRevisionId());
        suggestions.saveAndFlush(suggestion);

        // Các đề xuất còn pending khác của cùng câu hỏi trỏ tới nội dung cũ -> stale.
        List<String> taskIds = tasks.findByQuestionIdOrderByCreatedAtDesc(question.getQuestionId()).stream()
                .map(AiTask::getTaskId).limit(50).toList();
        if (!taskIds.isEmpty()) {
            suggestions.markPendingStale(taskIds, SuggestionDecisionD.PENDING, SuggestionDecisionD.STALE);
        }

        uiState.pushActivity(adminId, ActivityItem.of(
                "AI_SUGGESTION_ACCEPTED",
                "Áp đề xuất #" + suggestion.getSuggestionId() + " vào câu #" + question.getQuestionId()
                        + " (rev " + nextRevNo + ")",
                "QUESTION", question.getQuestionId()));
        uiState.evictDashboard(question.getSubjectId());

        return questionService.get(question.getQuestionId());
    }

    /**
     * Chỉ áp cho các trường văn bản mà AI được phép đề xuất. TargetFieldD không có
     * correct_label nên về mặt dữ liệu AI không thể đổi đáp án đúng; ở đây chặn thêm
     * target 'question' để người duyệt tự sửa cấu trúc.
     */
    private static void applyTargetField(Question question, TargetFieldD field, String text) {
        if (field == null) {
            throw QbankException.badRequest("TARGET_FIELD_MISSING", "Đề xuất thiếu target_field.");
        }
        switch (field) {
            case STEM -> question.setStem(text);
            case OPTION_A -> question.setOptionA(text);
            case OPTION_B -> question.setOptionB(text);
            case OPTION_C -> question.setOptionC(text);
            case OPTION_D -> question.setOptionD(text);
            case QUESTION -> throw QbankException.badRequest("TARGET_NOT_APPLICABLE",
                    "Đề xuất ở mức 'question' phải sửa tay: AI không được đổi cấu trúc/đáp án đúng.");
        }
    }

    private Long questionIdOf(AiSuggestion suggestion) {
        return tasks.findById(suggestion.getTaskId())
                .map(AiTask::getQuestionId)
                .orElseThrow(() -> QbankException.conflict("TASK_MISSING",
                        "Task của đề xuất không còn tồn tại."));
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
