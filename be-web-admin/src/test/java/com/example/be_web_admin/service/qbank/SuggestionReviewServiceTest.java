package com.example.be_web_admin.service.qbank;

import com.example.be_web_admin.dto.qbank.SuggestionDecisionRequest;
import com.example.be_web_admin.enums.Role;
import com.example.be_web_admin.enums.ai.SuggestionDecisionD;
import com.example.be_web_admin.exception.qbank.QbankException;
import com.example.be_web_admin.model.jpa.Admin;
import com.example.be_web_admin.model.jpa.ai.AiSuggestion;
import com.example.be_web_admin.model.jpa.ai.AiTask;
import com.example.be_web_admin.model.jpa.qbank.Question;
import com.example.be_web_admin.model.jpa.qbank.QuestionRevision;
import com.example.be_web_admin.repository.jpa.AdminRepository;
import com.example.be_web_admin.repository.jpa.ai.AiSuggestionRepository;
import com.example.be_web_admin.repository.jpa.ai.AiTaskRepository;
import com.example.be_web_admin.repository.jpa.qbank.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SuggestionReviewServiceTest {
    @Test
    void replacesOnlyTheQuotedFragment() {
        assertEquals("Đây là câu hỏi.", SuggestionReviewService.replaceQuotedText(
                "Đây la câu hỏi.", "la", 4, 6, "là"));
        assertEquals("😀 x mới x", SuggestionReviewService.replaceQuotedText(
                "😀 x cũ x", "cũ", 99, 101, "mới"));
    }

    @Test
    void rejectsMissingOrAmbiguousQuote() {
        assertThrows(QbankException.class, () -> SuggestionReviewService.replaceQuotedText("a a", "a", null, null, "b"));
        assertThrows(QbankException.class, () -> SuggestionReviewService.replaceQuotedText("abc", null, null, null, "b"));
        assertThrows(QbankException.class, () -> SuggestionReviewService.replaceQuotedText("abc", "xyz", 0, 3, "b"));
        assertEquals("a b", SuggestionReviewService.replaceQuotedText("a a", "a", 2, 3, "b"));
    }

    @Test
    void deniesReviewByOtherLecturerBeforeLockOrWrite() {
        AiSuggestionRepository suggestions = mock(AiSuggestionRepository.class);
        AiTaskRepository tasks = mock(AiTaskRepository.class);
        QuestionRepository questions = mock(QuestionRepository.class);
        AdminRepository admins = mock(AdminRepository.class);
        AiTask task = new AiTask(); task.setCreatedBy(1L); task.setQuestionId(10L);
        Admin admin = new Admin(); admin.setRole(Role.LECTURER);
        when(suggestions.findTaskIdBySuggestionId(3L)).thenReturn(Optional.of("task"));
        when(tasks.findById("task")).thenReturn(Optional.of(task));
        when(admins.findAdminByAdminId(2L)).thenReturn(admin);
        SuggestionReviewService service = service(suggestions, tasks, questions, mock(QuestionRevisionRepository.class), admins);
        QbankException ex = assertThrows(QbankException.class, () -> service.decide(3L, new SuggestionDecisionRequest("rejected", null, null, null), 2L));
        assertEquals("SUGGESTION_NOT_FOUND", ex.getCode());
        verifyNoInteractions(questions);
        verify(suggestions, never()).saveAndFlush(any());
    }

    @Test
    void staleOutcomeIsTheOnlyExceptionExemptFromRollback() throws Exception {
        Transactional tx = SuggestionReviewService.class.getMethod("decide", Long.class, SuggestionDecisionRequest.class, Long.class)
                .getAnnotation(Transactional.class);
        assertArrayEquals(new Class<?>[]{SuggestionReviewService.StaleSuggestionException.class}, tx.noRollbackFor());
        AiSuggestionRepository suggestions = mock(AiSuggestionRepository.class);
        AiTaskRepository tasks = mock(AiTaskRepository.class);
        QuestionRepository questions = mock(QuestionRepository.class);
        QuestionRevisionRepository revisions = mock(QuestionRevisionRepository.class);
        AdminRepository admins = mock(AdminRepository.class);
        AiTask task = new AiTask(); task.setCreatedBy(1L); task.setQuestionId(10L); task.setQuestionRevisionId(11L);
        Admin admin = new Admin(); admin.setRole(Role.LECTURER);
        Question question = new Question(); question.setQuestionId(10L);
        AiSuggestion suggestion = new AiSuggestion(); suggestion.setTaskId("task");
        QuestionRevision old = new QuestionRevision(); old.setQuestionId(10L); old.setRevNo(1);
        QuestionRevision latest = new QuestionRevision(); latest.setQuestionId(10L); latest.setRevNo(2);
        when(suggestions.findTaskIdBySuggestionId(3L)).thenReturn(Optional.of("task"));
        when(tasks.findById("task")).thenReturn(Optional.of(task));
        when(admins.findAdminByAdminId(1L)).thenReturn(admin);
        when(questions.findForReview(10L)).thenReturn(Optional.of(question));
        when(suggestions.findForReview(3L)).thenReturn(Optional.of(suggestion));
        when(revisions.findFirstByQuestionIdOrderByRevNoDesc(10L)).thenReturn(Optional.of(latest));
        when(revisions.findById(11L)).thenReturn(Optional.of(old));
        SuggestionReviewService service = service(suggestions, tasks, questions, revisions, admins);
        assertThrows(SuggestionReviewService.StaleSuggestionException.class,
                () -> service.decide(3L, new SuggestionDecisionRequest("accepted", null, null, null), 1L));
        assertEquals(SuggestionDecisionD.STALE, suggestion.getDecision());
        verify(suggestions).saveAndFlush(suggestion);
        verify(questions, never()).saveAndFlush(any());
    }

    private SuggestionReviewService service(AiSuggestionRepository s, AiTaskRepository t, QuestionRepository q,
                                             QuestionRevisionRepository r, AdminRepository a) {
        return new SuggestionReviewService(s, t, q, r, mock(SubjectRepository.class), mock(TopicRepository.class),
                mock(QuestionSnapshotFactory.class), mock(QuestionService.class), mock(UiStateStore.class), a);
    }
}
