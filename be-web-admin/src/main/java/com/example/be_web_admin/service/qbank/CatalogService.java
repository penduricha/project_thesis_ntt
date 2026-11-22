package com.example.be_web_admin.service.qbank;

import com.example.be_web_admin.dto.qbank.IssueTypeView;
import com.example.be_web_admin.dto.qbank.SubjectView;
import com.example.be_web_admin.dto.qbank.TopicView;
import com.example.be_web_admin.repository.jpa.ai.IssueTypeRepository;
import com.example.be_web_admin.repository.jpa.qbank.SubjectRepository;
import com.example.be_web_admin.repository.jpa.qbank.TopicRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Danh mục cho các bộ lọc của giao diện: môn, chủ đề, loại lỗi. */
@Service
public class CatalogService {

    private final SubjectRepository subjects;
    private final TopicRepository topics;
    private final IssueTypeRepository issueTypes;

    public CatalogService(SubjectRepository subjects,
                          TopicRepository topics,
                          IssueTypeRepository issueTypes) {
        this.subjects = subjects;
        this.topics = topics;
        this.issueTypes = issueTypes;
    }

    @Transactional(readOnly = true)
    public List<SubjectView> subjects(boolean includeInactive) {
        return (includeInactive ? subjects.findAllByOrderBySubjectNameAsc()
                : subjects.findByIsActiveTrueOrderBySubjectNameAsc())
                .stream()
                .map(s -> new SubjectView(s.getSubjectId(), s.getSubjectCode(), s.getSubjectName(),
                        s.getDescription(), Boolean.TRUE.equals(s.getIsActive())))
                .toList();
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
