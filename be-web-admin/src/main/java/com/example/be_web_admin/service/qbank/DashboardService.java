package com.example.be_web_admin.service.qbank;

import com.example.be_web_admin.dto.qbank.ActivityItem;
import com.example.be_web_admin.dto.qbank.DashboardSummary;
import com.example.be_web_admin.enums.ai.SuggestionDecisionD;
import com.example.be_web_admin.enums.ai.TaskStatusD;
import com.example.be_web_admin.enums.qbank.QuestionStatusD;
import com.example.be_web_admin.repository.jpa.ai.AiSuggestionRepository;
import com.example.be_web_admin.repository.jpa.ai.AiTaskRepository;
import com.example.be_web_admin.repository.jpa.qbank.QuestionRepository;
import com.example.be_web_admin.repository.jpa.qbank.SubjectRepository;
import com.example.be_web_admin.repository.jpa.qbank.ValidationStatusCount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Số liệu Dashboard. Cache-aside 30 giây ở Redis: màn hình gọi 1 request thay vì 6 lần COUNT,
 * và mọi thao tác ghi đều chủ động xoá cache (UiStateStore.evictDashboard).
 */
@Service
public class DashboardService {

    private static final List<TaskStatusD> RUNNING = List.of(TaskStatusD.PENDING, TaskStatusD.RUNNING);
    private static final int DEFAULT_ACTIVITY_LIMIT = 10;

    private final QuestionRepository questions;
    private final SubjectRepository subjects;
    private final AiSuggestionRepository suggestions;
    private final AiTaskRepository tasks;
    private final UiStateStore uiState;

    public DashboardService(QuestionRepository questions,
                            SubjectRepository subjects,
                            AiSuggestionRepository suggestions,
                            AiTaskRepository tasks,
                            UiStateStore uiState) {
        this.questions = questions;
        this.subjects = subjects;
        this.suggestions = suggestions;
        this.tasks = tasks;
        this.uiState = uiState;
    }

    @Transactional(readOnly = true)
    public DashboardSummary summary(Long subjectId) {
        Optional<DashboardSummary> cached = uiState.readDashboard(subjectId);
        if (cached.isPresent()) {
            return cached.get();
        }
        DashboardSummary fresh = compute(subjectId);
        uiState.writeDashboard(subjectId, fresh);
        return fresh;
    }

    public List<ActivityItem> activity(Long adminId, Integer limit) {
        int size = limit == null ? DEFAULT_ACTIVITY_LIMIT : Math.min(Math.max(1, limit), 50);
        return uiState.readActivity(adminId, size);
    }

    private DashboardSummary compute(Long subjectId) {
        long active = subjectId == null
                ? questions.countByStatus(QuestionStatusD.ACTIVE)
                : questions.countBySubjectIdAndStatus(subjectId, QuestionStatusD.ACTIVE);
        long archived = subjectId == null
                ? questions.countByStatus(QuestionStatusD.ARCHIVED)
                : questions.countBySubjectIdAndStatus(subjectId, QuestionStatusD.ARCHIVED);

        List<ValidationStatusCount> counts = subjectId == null
                ? questions.countGroupByValidationStatus()
                : questions.countGroupByValidationStatus(subjectId);
        Map<String, Long> byValidation = new LinkedHashMap<>();
        for (ValidationStatusCount row : counts) {
            byValidation.put(row.getStatus().dbValue(), row.getTotal());
        }

        long updatedLast7Days = questions.countByStatusAndUpdatedAtGreaterThanEqual(
                QuestionStatusD.ACTIVE, Instant.now().minus(7, ChronoUnit.DAYS));
        long pending = subjectId == null
                ? suggestions.countByDecision(SuggestionDecisionD.PENDING)
                : suggestions.countByDecisionForSubject(subjectId, SuggestionDecisionD.PENDING);

        return new DashboardSummary(
                uiState.scope(subjectId),
                active,
                archived,
                byValidation,
                updatedLast7Days,
                pending,
                tasks.countByStatusIn(RUNNING),
                subjects.countByIsActiveTrue(),
                Instant.now(),
                false);
    }
}
