package com.example.be_web_admin.service.qbank;

import com.example.be_web_admin.dto.qbank.AiSuggestionView;
import com.example.be_web_admin.dto.qbank.CreateQuestionRequest;
import com.example.be_web_admin.dto.qbank.PageResponse;
import com.example.be_web_admin.dto.qbank.QuestionDetail;
import com.example.be_web_admin.dto.qbank.QuestionListItem;
import com.example.be_web_admin.dto.qbank.UpdateQuestionRequest;
import com.example.be_web_admin.enums.ai.SuggestionDecisionD;
import com.example.be_web_admin.enums.ai.TaskTypeD;
import com.example.be_web_admin.enums.qbank.ChangeSourceD;
import com.example.be_web_admin.enums.qbank.ContentFormatD;
import com.example.be_web_admin.enums.qbank.OptionLabelD;
import com.example.be_web_admin.enums.qbank.QuestionSourceD;
import com.example.be_web_admin.enums.qbank.QuestionStatusD;
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
import com.example.be_web_admin.repository.jpa.ai.IssueTypeRepository;
import com.example.be_web_admin.repository.jpa.qbank.QuestionRepository;
import com.example.be_web_admin.repository.jpa.qbank.QuestionRevisionRepository;
import com.example.be_web_admin.repository.jpa.qbank.SubjectRepository;
import com.example.be_web_admin.repository.jpa.qbank.TopicRepository;
import com.example.be_web_admin.repository.jpa.qbank.ValidationStatusCount;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Nghiệp vụ ngân hàng câu hỏi: đọc danh sách/chi tiết, tạo, sửa (sinh revision), lưu trữ.
 *
 * <p>Ba quy tắc xương sống, mỗi quy tắc ứng với 1 phát hiện trong tài liệu đối chiếu:</p>
 * <ol>
 *   <li>Tạo câu hỏi phải sinh {@code rev_no = 1} trong CÙNG transaction (D3) — nếu không,
 *       mọi task question_check vi phạm chk_task_question_check.</li>
 *   <li>Sửa nội dung phải sinh revision mới + đặt validation_status = stale + đánh dấu
 *       pending suggestions thành stale (D6), không phụ thuộc việc DB có cài trigger hay không.</li>
 *   <li>Xoá = archive (status), không DELETE cứng: ntt_ai_tasks/ntt_eval_items còn FK RESTRICT.</li>
 * </ol>
 */
@Slf4j
@Service
public class QuestionService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int STEM_PREVIEW_LENGTH = 160;

    private final QuestionRepository questions;
    private final QuestionRevisionRepository revisions;
    private final SubjectRepository subjects;
    private final TopicRepository topics;
    private final AiTaskRepository aiTasks;
    private final AiSuggestionRepository suggestions;
    private final IssueTypeRepository issueTypes;
    private final QuestionSnapshotFactory snapshots;
    private final UiStateStore uiState;

    public QuestionService(QuestionRepository questions,
                           QuestionRevisionRepository revisions,
                           SubjectRepository subjects,
                           TopicRepository topics,
                           AiTaskRepository aiTasks,
                           AiSuggestionRepository suggestions,
                           IssueTypeRepository issueTypes,
                           QuestionSnapshotFactory snapshots,
                           UiStateStore uiState) {
        this.questions = questions;
        this.revisions = revisions;
        this.subjects = subjects;
        this.topics = topics;
        this.aiTasks = aiTasks;
        this.suggestions = suggestions;
        this.issueTypes = issueTypes;
        this.snapshots = snapshots;
        this.uiState = uiState;
    }

    // ============================================================== ĐỌC

    @Transactional(readOnly = true)
    public PageResponse<QuestionListItem> list(Long subjectId,
                                               Long topicId,
                                               String status,
                                               String validationStatus,
                                               String keyword,
                                               int page,
                                               int size) {
        return list(subjectId, topicId, status, validationStatus, keyword, page, size, false);
    }

    /** @param includeOptions true = kèm 4 phương án + đáp án đúng (màn danh sách đầy đủ). */
    @Transactional(readOnly = true)
    public PageResponse<QuestionListItem> list(Long subjectId,
                                               Long topicId,
                                               String status,
                                               String validationStatus,
                                               String keyword,
                                               int page,
                                               int size,
                                               boolean includeOptions) {
        QuestionStatusD statusFilter = parseQuestionStatus(status);
        ValidationStatusD validationFilter = parseValidationStatus(validationStatus);

        Pageable pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "updatedAt"));

        Page<Question> result = questions.findAll(
                QuestionSpecs.filter(subjectId, topicId, statusFilter, validationFilter, keyword), pageable);

        Map<Long, Subject> subjectMap = loadSubjects(result.getContent());
        Map<Long, Topic> topicMap = loadTopics(result.getContent());

        return PageResponse.of(result, q -> toListItem(q, subjectMap.get(q.getSubjectId()),
                q.getTopicId() == null ? null : topicMap.get(q.getTopicId()), includeOptions));
    }

    @Transactional(readOnly = true)
    public QuestionDetail get(Long questionId) {
        Question question = requireQuestion(questionId);
        Subject subject = subjects.findById(question.getSubjectId()).orElse(null);
        Topic topic = question.getTopicId() == null ? null
                : topics.findById(question.getTopicId()).orElse(null);
        int revNo = revisions.findTopByQuestionIdOrderByRevNoDesc(questionId)
                .map(QuestionRevision::getRevNo)
                .orElse(0);
        long pending = suggestions.countByDecisionForQuestion(questionId, SuggestionDecisionD.PENDING);
        return toDetail(question, subject, topic, revNo, pending);
    }

    @Transactional(readOnly = true)
    public List<QuestionRevision> revisions(Long questionId) {
        requireQuestion(questionId);
        return revisions.findByQuestionIdOrderByRevNoDesc(questionId);
    }

    /** Đề xuất của task MỚI NHẤT cho câu hỏi — dữ liệu cho modal duyệt đề xuất AI. */
    @Transactional(readOnly = true)
    public List<AiSuggestionView> latestSuggestions(Long questionId) {
        requireQuestion(questionId);
        Optional<AiTask> latest = aiTasks.findTopByQuestionIdAndTaskTypeOrderByCreatedAtDesc(
                questionId, TaskTypeD.QUESTION_CHECK);
        if (latest.isEmpty()) {
            return List.of();
        }
        Map<String, String> labels = issueTypes.findAll().stream()
                .collect(Collectors.toMap(it -> it.getIssueType(), it -> it.getLabelVi(), (a, b) -> a,
                        LinkedHashMap::new));
        List<AiSuggestion> list = suggestions.findByTaskIdOrderBySeqNoAsc(latest.get().getTaskId());
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

    // ============================================================== GHI

    @Transactional
    public QuestionDetail create(CreateQuestionRequest request, Long adminId) {
        Subject subject = subjects.findById(request.subjectId())
                .orElseThrow(() -> QbankException.notFound("SUBJECT_NOT_FOUND",
                        "Không tìm thấy môn học " + request.subjectId()));
        if (!Boolean.TRUE.equals(subject.getIsActive())) {
            throw QbankException.badRequest("SUBJECT_INACTIVE", "Môn học đang bị vô hiệu hoá.");
        }
        Topic topic = resolveTopic(subject.getSubjectId(), request.topicId());

        OptionLabelD correctLabel = parseCorrectLabel(request.correctLabel());
        ContentFormatD contentFormat = parseContentFormat(request.contentFormat());
        validateDifficulty(request.difficulty());
        String stem = requireText(request.stem(), "stem");
        String a = requireText(request.optionA(), "optionA");
        String b = requireText(request.optionB(), "optionB");
        String c = requireText(request.optionC(), "optionC");
        String d = requireText(request.optionD(), "optionD");

        String hash = QuestionContentHasher.hash(stem, a, b, c, d, correctLabel, contentFormat);
        boolean duplicate = questions.existsBySubjectIdAndContentHash(subject.getSubjectId(), hash);

        // Quy tắc CHỈ áp cho THÊM MỚI: tiêu đề của câu mới phải khác mọi câu đang có trong cùng
        // môn (bỏ khoảng trắng hai đầu, không phân biệt hoa/thường). Tiêu đề không bắt buộc —
        // để trống thì bỏ qua kiểm tra.
        String title = trimToNull(request.title());
        assertTitleAvailable(subject.getSubjectId(), title, -1L);

        Question question = new Question();
        question.setSubjectId(subject.getSubjectId());
        question.setTopicId(topic == null ? null : topic.getTopicId());
        question.setTitle(title);
        question.setStem(stem);
        question.setOptionA(a);
        question.setOptionB(b);
        question.setOptionC(c);
        question.setOptionD(d);
        question.setCorrectLabel(correctLabel);
        question.setContentFormat(contentFormat);
        question.setGeneralFeedback(trimToNull(request.generalFeedback()));
        question.setDifficulty(request.difficulty());
        question.setStatus(QuestionStatusD.ACTIVE);
        question.setValidationStatus(ValidationStatusD.NOT_CHECKED);
        question.setSource(QuestionSourceD.MANUAL);
        question.setContentHash(hash);
        /*
         * KHÔNG setVersion(0) trước khi lưu.
         *
         * Question có @Version, và Spring Data quyết định persist hay merge bằng
         * isNew() — với @Version thì isNew = (version == null). Gán version = 0 khiến
         * Spring Data coi entity ĐÃ TỒN TẠI, gọi em.merge(); merge() trả về bản copy
         * có id còn biến `question` gốc vẫn null questionId. Bước appendRevision sau
         * đó sẽ ghi NULL vào cột question_id và nổ:
         *     Column 'question_id' cannot be null (SQLState 23000, errno 1048)
         * Để Hibernate tự khởi tạo version, và luôn dùng entity TRẢ VỀ từ save().
         */
        question.setCreatedBy(adminId);
        question.setUpdatedBy(adminId);
        Question saved = questions.saveAndFlush(question);

        // D3: revision 1 là điều kiện để task AI có thể tham chiếu (question_id, revision_id).
        appendRevision(saved, 1, ChangeSourceD.CREATE, adminId, null, subject, topic);

        uiState.deleteDraft(adminId, "new");
        uiState.pushActivity(adminId, com.example.be_web_admin.dto.qbank.ActivityItem.of(
                "QUESTION_CREATED", "Tạo câu hỏi #" + saved.getQuestionId() + " (" + subject.getSubjectCode() + ")",
                "QUESTION", saved.getQuestionId()));
        uiState.evictDashboard(subject.getSubjectId());

        if (duplicate) {
            log.info("Câu hỏi #{} trùng nội dung với câu đã có trong môn {}", saved.getQuestionId(),
                    subject.getSubjectCode());
        }
        return toDetail(saved, subject, topic, 1, 0);
    }

    @Transactional
    public QuestionDetail update(Long questionId, UpdateQuestionRequest request, Long adminId) {
        Question question = requireQuestion(questionId);
        if (question.getStatus() == QuestionStatusD.ARCHIVED) {
            throw QbankException.conflict("QUESTION_ARCHIVED", "Câu hỏi đã bị lưu trữ, không sửa được.");
        }
        if (!Objects.equals(question.getVersion(), request.expectedVersion())) {
            throw QbankException.conflict("VERSION_CONFLICT",
                    "Câu hỏi đã bị người khác sửa (bạn giữ phiên bản " + request.expectedVersion()
                            + ", hiện tại là " + question.getVersion() + "). Tải lại rồi thử lại.");
        }

        // Cùng quy tắc với create(): môn bị vô hiệu hoá thì không sửa được câu hỏi của môn đó.
        // Trước bản vá, update() dùng orElse(null) nên vô hiệu hoá môn rồi vẫn sửa được câu hỏi,
        // và môn bị xoá thì lặng lẽ trả subjectName = null trong phản hồi.
        Subject subject = subjects.findById(question.getSubjectId())
                .orElseThrow(() -> QbankException.notFound("SUBJECT_NOT_FOUND",
                        "Không tìm thấy môn học " + question.getSubjectId()));
        if (!Boolean.TRUE.equals(subject.getIsActive())) {
            throw QbankException.badRequest("SUBJECT_INACTIVE", "Môn học đang bị vô hiệu hoá.");
        }
        Topic topic = resolveTopic(question.getSubjectId(), request.topicId());
        OptionLabelD correctLabel = parseCorrectLabel(request.correctLabel());
        ContentFormatD contentFormat = parseContentFormat(request.contentFormat());
        validateDifficulty(request.difficulty());
        String stem = requireText(request.stem(), "stem");
        String a = requireText(request.optionA(), "optionA");
        String b = requireText(request.optionB(), "optionB");
        String c = requireText(request.optionC(), "optionC");
        String d = requireText(request.optionD(), "optionD");

        String newHash = QuestionContentHasher.hash(stem, a, b, c, d, correctLabel, contentFormat);
        boolean contentChanged = !QuestionContentHasher.sameContent(question.getContentHash(), newHash);

        // KHÔNG kiểm tra trùng tiêu đề ở đây — cố ý.
        // Quy tắc "câu thêm mới phải khác tiêu đề câu khác" chỉ chặn ở đường tạo mới.
        // Chặn cả khi sửa sẽ khoá vĩnh viễn những câu đã có tiêu đề trùng từ TRƯỚC khi có quy
        // tắc: người dùng mở một trong số đó ra sửa nội dung thì bị chặn bởi chính câu kia,
        // buộc phải đổi tiêu đề mới lưu được. Đó là hành vi sai: sửa nội dung không làm phát
        // sinh thêm bản trùng nào, mà lại cản người dùng sửa được chính câu hỏng.
        String title = trimToNull(request.title());

        question.setTopicId(topic == null ? null : topic.getTopicId());
        question.setTitle(title);
        question.setStem(stem);
        question.setOptionA(a);
        question.setOptionB(b);
        question.setOptionC(c);
        question.setOptionD(d);
        question.setCorrectLabel(correctLabel);
        question.setContentFormat(contentFormat);
        question.setGeneralFeedback(trimToNull(request.generalFeedback()));
        question.setDifficulty(request.difficulty());
        question.setContentHash(newHash);
        question.setUpdatedBy(adminId);
        if (contentChanged && question.getValidationStatus() != ValidationStatusD.NOT_CHECKED) {
            question.setValidationStatus(ValidationStatusD.STALE);
        }
        questions.saveAndFlush(question);

        int revNo = revisions.findMaxRevNo(questionId) + 1;
        appendRevision(question, revNo, ChangeSourceD.MANUAL, adminId, request.changeNote(), subject, topic);

        if (contentChanged) {
            // D6: trigger chỉ chạy khi validation_status chuyển sang 'stale'; ở đây service làm
            // tường minh để đề xuất cũ không thể bị "chấp nhận" lên nội dung mới.
            markPendingSuggestionsStale(questionId);
            uiState.pushActivity(adminId, com.example.be_web_admin.dto.qbank.ActivityItem.of(
                    "QUESTION_UPDATED", "Sửa câu hỏi #" + questionId + " (rev " + revNo + ")",
                    "QUESTION", questionId));
        }
        uiState.evictDashboard(question.getSubjectId());
        uiState.deleteDraft(adminId, String.valueOf(questionId));

        long pending = suggestions.countByDecisionForQuestion(questionId, SuggestionDecisionD.PENDING);
        return toDetail(question, subject, topic, revNo, pending);
    }

    /** Xoá mềm: chuyển status = archived, giữ nguyên dữ liệu cho thống kê và lịch sử. */
    @Transactional
    public QuestionDetail archive(Long questionId, Long adminId) {
        Question question = requireQuestion(questionId);
        if (question.getStatus() == QuestionStatusD.ARCHIVED) {
            throw QbankException.conflict("ALREADY_ARCHIVED", "Câu hỏi đã ở trạng thái lưu trữ.");
        }
        question.setStatus(QuestionStatusD.ARCHIVED);
        question.setUpdatedBy(adminId);
        questions.saveAndFlush(question);

        Subject subject = subjects.findById(question.getSubjectId()).orElse(null);
        Topic topic = question.getTopicId() == null ? null : topics.findById(question.getTopicId()).orElse(null);
        int revNo = revisions.findMaxRevNo(questionId) + 1;
        appendRevision(question, revNo, ChangeSourceD.ARCHIVE, adminId, "Lưu trữ câu hỏi", subject, topic);
        markPendingSuggestionsStale(questionId);

        uiState.pushActivity(adminId, com.example.be_web_admin.dto.qbank.ActivityItem.of(
                "QUESTION_ARCHIVED", "Lưu trữ câu hỏi #" + questionId, "QUESTION", questionId));
        uiState.evictDashboard(question.getSubjectId());

        long pending = suggestions.countByDecisionForQuestion(questionId, SuggestionDecisionD.PENDING);
        return toDetail(question, subject, topic, revNo, pending);
    }

    /**
     * Khôi phục câu hỏi đã lưu trữ: status về active.
     *
     * <p><b>Vì sao cần:</b> {@code archive()} đặt {@code ARCHIVED} và {@code update()} chặn vĩnh
     * viễn câu đã lưu trữ, nên trước bản này bấm nhầm "Lưu trữ" (nhất là "lưu trữ hàng loạt") là
     * mất câu hỏi: dữ liệu còn trong DB nhưng không sửa, không dùng lại được nữa. Hằng
     * {@code ChangeSourceD.RESTORE} đã có sẵn trong enum và {@code SQL_TYPE} đã khai báo
     * {@code 'restore'}, tức CSDL v4 đã thiết kế cho việc này — chỉ thiếu phần cài đặt.</p>
     *
     * <p>Không kiểm tra gì thêm ngoài trạng thái: nội dung câu hỏi không đổi khi lưu trữ nên khôi
     * phục chỉ là đổi cờ. Revision vẫn được ghi để lịch sử thấy rõ ai khôi phục và khi nào.</p>
     */
    @Transactional
    public QuestionDetail restore(Long questionId, Long adminId) {
        Question question = requireQuestion(questionId);
        if (question.getStatus() != QuestionStatusD.ARCHIVED) {
            // Câu đang active: không phải lỗi nặng, nhưng trả conflict để giao diện biết nút này
            // không áp dụng — tránh im lặng "thành công" trong khi chẳng có gì đổi.
            throw QbankException.conflict("NOT_ARCHIVED",
                    "Câu hỏi đang hoạt động, không cần khôi phục.");
        }
        question.setStatus(QuestionStatusD.ACTIVE);
        question.setUpdatedBy(adminId);
        questions.saveAndFlush(question);

        Subject subject = subjects.findById(question.getSubjectId()).orElse(null);
        Topic topic = question.getTopicId() == null ? null : topics.findById(question.getTopicId()).orElse(null);
        int revNo = revisions.findMaxRevNo(questionId) + 1;
        appendRevision(question, revNo, ChangeSourceD.RESTORE, adminId, "Khôi phục câu hỏi", subject, topic);

        uiState.pushActivity(adminId, com.example.be_web_admin.dto.qbank.ActivityItem.of(
                "QUESTION_RESTORED", "Khôi phục câu hỏi #" + questionId, "QUESTION", questionId));
        uiState.evictDashboard(question.getSubjectId());

        long pending = suggestions.countByDecisionForQuestion(questionId, SuggestionDecisionD.PENDING);
        return toDetail(question, subject, topic, revNo, pending);
    }

    // ============================================================== NỘI BỘ

    private void appendRevision(Question question,
                                int revNo,
                                ChangeSourceD source,
                                Long adminId,
                                String note,
                                Subject subject,
                                Topic topic) {
        QuestionRevision revision = new QuestionRevision();
        revision.setQuestionId(question.getQuestionId());
        revision.setRevNo(revNo);
        revision.setSnapshotJson(snapshots.build(question, subject, topic));
        revision.setChangeSource(source);
        revision.setChangeNote(trimToNull(note));
        revision.setChangedBy(adminId);
        revisions.saveAndFlush(revision);
    }

    private void markPendingSuggestionsStale(Long questionId) {
        List<String> taskIds = aiTasks.findByQuestionIdOrderByCreatedAtDesc(questionId).stream()
                .map(AiTask::getTaskId)
                .limit(50)
                .toList();
        if (taskIds.isEmpty()) {
            return;
        }
        int updated = suggestions.markPendingStale(taskIds, SuggestionDecisionD.PENDING, SuggestionDecisionD.STALE);
        if (updated > 0) {
            log.info("Đánh dấu {} đề xuất pending của câu #{} thành stale", updated, questionId);
        }
    }

    private Question requireQuestion(Long questionId) {
        if (questionId == null) {
            throw QbankException.badRequest("QUESTION_ID_REQUIRED", "Thiếu questionId.");
        }
        return questions.findById(questionId)
                .orElseThrow(() -> QbankException.notFound("QUESTION_NOT_FOUND",
                        "Không tìm thấy câu hỏi " + questionId));
    }

    private Topic resolveTopic(Long subjectId, Long topicId) {
        if (topicId == null) {
            return null;
        }
        return topics.findBySubjectIdAndTopicId(subjectId, topicId)
                .orElseThrow(() -> QbankException.badRequest("TOPIC_NOT_IN_SUBJECT",
                        "Chủ đề " + topicId + " không thuộc môn " + subjectId));
    }

    private Map<Long, Subject> loadSubjects(List<Question> list) {
        List<Long> ids = list.stream().map(Question::getSubjectId).filter(Objects::nonNull).distinct().toList();
        return subjects.findAllById(ids).stream()
                .collect(Collectors.toMap(Subject::getSubjectId, Function.identity(), (a, b) -> a));
    }

    private Map<Long, Topic> loadTopics(List<Question> list) {
        List<Long> ids = list.stream().map(Question::getTopicId).filter(Objects::nonNull).distinct().toList();
        return topics.findAllById(ids).stream()
                .collect(Collectors.toMap(Topic::getTopicId, Function.identity(), (a, b) -> a));
    }

    private static QuestionListItem toListItem(Question q, Subject subject, Topic topic) {
        return toListItem(q, subject, topic, false);
    }

    private static QuestionListItem toListItem(Question q, Subject subject, Topic topic,
                                               boolean includeOptions) {
        return new QuestionListItem(
                q.getQuestionId(),
                q.getSubjectId(),
                subject == null ? null : subject.getSubjectName(),
                q.getTopicId(),
                topic == null ? null : topic.getTopicName(),
                q.getTitle(),
                preview(q.getStem(), STEM_PREVIEW_LENGTH),
                q.getValidationStatus() == null ? null : q.getValidationStatus().dbValue(),
                q.getStatus() == null ? null : q.getStatus().dbValue(),
                q.getDifficulty(),
                q.getSource() == null ? null : q.getSource().dbValue(),
                q.getUpdatedAt(),
                q.getVersion(),
                includeOptions ? q.getStem() : null,
                includeOptions ? List.of(q.getOptionA(), q.getOptionB(), q.getOptionC(), q.getOptionD()) : null,
                includeOptions && q.getCorrectLabel() != null ? q.getCorrectLabel().dbValue() : null,
                includeOptions ? q.getGeneralFeedback() : null,
                includeOptions && q.getContentFormat() != null ? q.getContentFormat().dbValue() : null);
    }

    private static QuestionDetail toDetail(Question q, Subject subject, Topic topic, int revNo, long pending) {
        return new QuestionDetail(
                q.getQuestionId(),
                q.getSubjectId(),
                subject == null ? null : subject.getSubjectName(),
                q.getTopicId(),
                topic == null ? null : topic.getTopicName(),
                q.getTitle(),
                q.getStem(),
                q.getOptionA(),
                q.getOptionB(),
                q.getOptionC(),
                q.getOptionD(),
                q.getCorrectLabel() == null ? null : q.getCorrectLabel().dbValue(),
                q.getContentFormat() == null ? null : q.getContentFormat().dbValue(),
                q.getGeneralFeedback(),
                q.getDifficulty(),
                q.getStatus() == null ? null : q.getStatus().dbValue(),
                q.getValidationStatus() == null ? null : q.getValidationStatus().dbValue(),
                q.getLastValidatedAt(),
                q.getSource() == null ? null : q.getSource().dbValue(),
                q.getContentHash(),
                q.getVersion(),
                q.getCreatedAt(),
                q.getUpdatedAt(),
                revNo,
                pending);
    }

    static QuestionStatusD parseQuestionStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return QuestionStatusD.fromDbValue(raw.trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw QbankException.badRequest("INVALID_STATUS", "status phải là active hoặc archived.");
        }
    }

    static ValidationStatusD parseValidationStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ValidationStatusD.fromDbValue(raw.trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw QbankException.badRequest("INVALID_VALIDATION_STATUS",
                    "validationStatus phải là not_checked/clean/has_issues/failed/stale.");
        }
    }

    static OptionLabelD parseCorrectLabel(String raw) {
        if (raw == null || raw.isBlank()) {
            throw QbankException.badRequest("CORRECT_LABEL_REQUIRED", "Thiếu correctLabel (A/B/C/D).");
        }
        try {
            return OptionLabelD.fromDbValue(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw QbankException.badRequest("INVALID_CORRECT_LABEL", "correctLabel phải là A, B, C hoặc D.");
        }
    }

    static ContentFormatD parseContentFormat(String raw) {
        if (raw == null || raw.isBlank()) {
            return ContentFormatD.PLAIN;
        }
        try {
            return ContentFormatD.fromDbValue(raw.trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw QbankException.badRequest("INVALID_CONTENT_FORMAT", "contentFormat phải là plain hoặc html.");
        }
    }

    private static void validateDifficulty(Integer difficulty) {
        if (difficulty != null && (difficulty < 1 || difficulty > 5)) {
            throw QbankException.badRequest("INVALID_DIFFICULTY", "difficulty phải từ 1 đến 5.");
        }
    }

    private static String requireText(String value, String field) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw QbankException.badRequest("EMPTY_FIELD", field + " không được để trống.");
        }
        return trimmed;
    }

    /**
     * Chặn trùng tiêu đề khi <b>thêm mới</b> câu hỏi.
     *
     * <p>Quy tắc (theo góp ý nghiệp vụ): tiêu đề của câu <b>thêm mới</b> phải khác tiêu đề các câu
     * đã có trong môn, không tính khoảng trắng hai đầu và không phân biệt hoa/thường. Tiêu đề để
     * trống là hợp lệ (cột {@code title} cho phép NULL, giao diện hiển thị mã CHxxx thay thế) nên
     * bỏ qua kiểm tra.</p>
     *
     * <p><b>Chỉ gọi từ {@link #create}</b>, tuyệt đối không gọi khi cập nhật: xem chú thích trong
     * {@code update(...)} — chặn ở đó sẽ khoá vĩnh viễn các câu đã trùng tiêu đề từ trước.</p>
     *
     * @throws QbankException 409 TITLE_DUPLICATED kèm mã câu bị trùng để UI báo rõ
     */
    private void assertTitleAvailable(Long subjectId, String title, Long excludeQuestionId) {
        if (title == null || title.isBlank()) {
            return;
        }
        // -1 khi thêm mới (không có question_id âm), KHÔNG truyền null: "question_id <> NULL"
        // luôn UNKNOWN trong SQL nên query sẽ không trả dòng nào và việc chặn trùng vô hiệu.
        List<Question> clashes = questions.findDuplicateTitleInSubject(
                subjectId, title, excludeQuestionId == null ? -1L : excludeQuestionId,
                QuestionStatusD.ARCHIVED);
        if (clashes.isEmpty()) {
            return;
        }
        Question other = clashes.get(0);
        throw QbankException.conflict("TITLE_DUPLICATED",
                "Tiêu đề \"" + title + "\" đã được dùng cho câu hỏi #" + other.getQuestionId()
                        + " trong cùng môn học. Hãy đặt tiêu đề khác.");
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String preview(String text, int max) {
        if (text == null) {
            return null;
        }
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() <= max ? flat : flat.substring(0, max) + "…";
    }

    /** Dùng cho Dashboard: đếm nhanh theo trạng thái kiểm định (đã có repo). */
    @Transactional(readOnly = true)
    public List<ValidationStatusCount> validationCounts(Long subjectId) {
        return subjectId == null
                ? questions.countGroupByValidationStatus()
                : questions.countGroupByValidationStatus(subjectId);
    }
}
