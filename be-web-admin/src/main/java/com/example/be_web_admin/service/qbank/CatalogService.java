package com.example.be_web_admin.service.qbank;

import com.example.be_web_admin.dto.qbank.IssueTypeView;
import com.example.be_web_admin.dto.qbank.SubjectView;
import com.example.be_web_admin.dto.qbank.SubjectCreateRequest;
import com.example.be_web_admin.dto.qbank.SubjectUpdateRequest;
import com.example.be_web_admin.dto.qbank.SubjectArchiveRequest;
import com.example.be_web_admin.exception.qbank.QBankException;
import com.example.be_web_admin.model.jpa.qbank.Subject;
import com.example.be_web_admin.model.jpa.qbank.Topic;
import com.example.be_web_admin.dto.qbank.TopicView;
import com.example.be_web_admin.repository.jpa.ai.IssueTypeRepository;
import com.example.be_web_admin.repository.jpa.qbank.SubjectRepository;
import com.example.be_web_admin.repository.jpa.qbank.TopicRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Danh mục cho các bộ lọc của giao diện: môn, chủ đề, loại lỗi. */
@Service
public class CatalogService {

    private final SubjectRepository subjects;
    private final TopicRepository topics;
    private final IssueTypeRepository issueTypes;
    private final UiStateStore uiState;
    private final EntityManager entityManager;

    public CatalogService(SubjectRepository subjects,
                          TopicRepository topics,
                          IssueTypeRepository issueTypes,
                          UiStateStore uiState,
                          EntityManager entityManager) {
        this.subjects = subjects;
        this.topics = topics;
        this.issueTypes = issueTypes;
        this.uiState = uiState;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<SubjectView> subjects(boolean includeInactive) {
        return (includeInactive ? subjects.findAllByOrderBySubjectNameAsc()
                : subjects.findByIsActiveTrueOrderBySubjectNameAsc())
                .stream()
                .map(CatalogService::toView)
                .toList();
    }

    // Restrict identifiers without restricting Vietnamese or other Unicode names.
    private static final Pattern CODE = Pattern.compile("[A-Z]{3}[0-9]{4,6}");
    private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{M}\\p{N}][\\p{L}\\p{M}\\p{N} .,\u0027’()&+/#:;–—-]*");

    @Transactional(readOnly = true)
    public SubjectView subject(Long id) {
        return toView(subjects.findById(id).orElseThrow(() -> notFound(id)));
    }

    @Transactional
    public SubjectView createSubject(SubjectCreateRequest request, Long adminId) {
        String code = code(request.subjectCode());
        String name = name(request.subjectName(), 200, "SUBJECT_NAME_INVALID");
        String description = description(request.description());
        String firstTopic = name(request.firstTopic(), 150, "TOPIC_NAME_INVALID");
        if (subjects.existsBySubjectCodeIgnoreCase(code)) throw duplicateCode();
        Subject subject = new Subject();
        subject.setSubjectCode(code);
        subject.setSubjectName(name);
        subject.setDescription(description);
        subject.setIsActive(request.active());
        subject.setCreatedBy(adminId);
        subject = subjects.saveAndFlush(subject);
        Topic topic = new Topic();
        topic.setSubjectId(subject.getSubjectId());
        topic.setTopicName(firstTopic);
        topic.setSortOrder(0);
        topics.saveAndFlush(topic);
        entityManager.refresh(subject);
        evictAfterCommit(subject.getSubjectId());
        return toView(subject);
    }

    @Transactional
    public SubjectView updateSubject(Long id, SubjectUpdateRequest request) {
        Subject subject = lockSubject(id);
        assertCurrent(subject, request.expectedUpdatedAt());
        String code = code(request.subjectCode());
        String name = name(request.subjectName(), 200, "SUBJECT_NAME_INVALID");
        String description = description(request.description());
        if (subjects.existsBySubjectCodeIgnoreCaseAndSubjectIdNot(code, id)) throw duplicateCode();
        subject.setSubjectCode(code);
        subject.setSubjectName(name);
        subject.setDescription(description);
        subject.setIsActive(request.active());
        Subject saved = subjects.saveAndFlush(subject);
        entityManager.refresh(saved);
        evictAfterCommit(id);
        return toView(saved);
    }

    @Transactional
    public SubjectView archiveSubject(Long id, SubjectArchiveRequest request) {
        Subject subject = lockSubject(id);
        assertCurrent(subject, request.expectedUpdatedAt());
        subject.setIsActive(false);
        Subject saved = subjects.saveAndFlush(subject);
        entityManager.refresh(saved);
        evictAfterCommit(id);
        return toView(saved);
    }

    private void evictAfterCommit(Long subjectId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { uiState.evictDashboard(subjectId); }
            });
        }
    }

    private Subject lockSubject(Long id) {
        return subjects.findForQuestionCreate(id).orElseThrow(() -> notFound(id));
    }

    private static void assertCurrent(Subject subject, Instant expected) {
        if (expected == null || !Objects.equals(micros(expected), micros(subject.getUpdatedAt()))) {
            throw QBankException.conflict("SUBJECT_VERSION_CONFLICT", "Môn học đã thay đổi. Hãy tải lại trước khi sửa.");
        }
    }

    private static QBankException notFound(Long id) {
        return QBankException.notFound("SUBJECT_NOT_FOUND", "Không tìm thấy môn học " + id);
    }

    private static QBankException duplicateCode() {
        return QBankException.conflict("SUBJECT_CODE_DUPLICATED", "Mã môn học đã tồn tại.");
    }

    private static String code(String raw) {
        String value = raw == null ? "" : raw;
        if (!CODE.matcher(value).matches()) {
            throw QBankException.badRequest("SUBJECT_CODE_INVALID", "Mã học phần phải gồm 3 chữ cái in hoa và 4–6 chữ số, ví dụ INT3202.");
        }
        return value;
    }

    private static String name(String raw, int max, String errorCode) {
        String value = raw == null ? "" : raw.strip().replaceAll(" +", " ");
        if (value.length() > max || !NAME.matcher(value).matches()) {
            throw QBankException.badRequest(errorCode, "Tên không hợp lệ hoặc vượt quá " + max + " ký tự.");
        }
        return value;
    }

    private static String description(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.strip();
        if (value.length() > 500 || value.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')) {
            throw QBankException.badRequest("SUBJECT_DESCRIPTION_INVALID", "Mô tả không hợp lệ hoặc vượt quá 500 ký tự.");
        }
        return value;
    }

    private static Instant micros(Instant value) {
        return value == null ? null : value.truncatedTo(ChronoUnit.MICROS);
    }

    private static SubjectView toView(Subject subject) {
        return new SubjectView(subject.getSubjectId(), subject.getSubjectCode(), subject.getSubjectName(),
                subject.getDescription(), Boolean.TRUE.equals(subject.getIsActive()), micros(subject.getUpdatedAt()));
    }

    @Transactional(readOnly = true)
    public List<TopicView> topics(Long subjectId) {
        return topics.findBySubjectIdOrderBySortOrderAscTopicNameAsc(subjectId).stream()
                .map(t -> new TopicView(t.getTopicId(), t.getSubjectId(), t.getParentId(),
                        t.getTopicName(), t.getSortOrder()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<IssueTypeView> issueTypes() {
        return issueTypes.findAllByOrderBySortOrderAsc().stream()
                .map(it -> new IssueTypeView(it.getIssueType(), it.getLabelVi(),
                        Boolean.TRUE.equals(it.getIsEvaluated()), it.getSortOrder()))
                .toList();
    }
}
