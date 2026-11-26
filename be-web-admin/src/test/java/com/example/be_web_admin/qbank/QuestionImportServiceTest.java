package com.example.be_web_admin.qbank;

import com.example.be_web_admin.dto.qbank.CreateQuestionRequest;
import com.example.be_web_admin.dto.qbank.ImportQuestionsRequest;
import com.example.be_web_admin.dto.qbank.ImportQuestionsResult;
import com.example.be_web_admin.dto.qbank.QuestionDetail;
import com.example.be_web_admin.exception.qbank.QbankException;
import com.example.be_web_admin.service.qbank.QuestionImportService;
import com.example.be_web_admin.service.qbank.QuestionService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QuestionImportServiceTest {
    private final QuestionService questions = mock(QuestionService.class);
    private final QuestionImportService service = new QuestionImportService(questions);
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private ImportQuestionsRequest.Row row(String title) {
        return new ImportQuestionsRequest.Row(title, "Câu hỏi?", "A1", "B1", "C1", "D1", "B", "Giải thích");
    }

    private ImportQuestionsRequest request(List<ImportQuestionsRequest.Row> rows) {
        return new ImportQuestionsRequest(7L, 42L, 2, rows);
    }

    @Test
    void createsViaExistingServiceForSelectedSubjectAndTopic() {
        QuestionDetail detail = mock(QuestionDetail.class);
        when(questions.create(any(), eq(13L))).thenReturn(detail);
        ImportQuestionsResult result = service.importQuestions(request(List.of(row("First"))), 13L);
        assertEquals(1, result.saved());
        assertNull(result.failedAt());
        assertEquals(List.of(detail), result.questions());
        ArgumentCaptor<CreateQuestionRequest> sent = ArgumentCaptor.forClass(CreateQuestionRequest.class);
        verify(questions).create(sent.capture(), eq(13L));
        assertEquals(7L, sent.getValue().subjectId());
        assertEquals(42L, sent.getValue().topicId());
        assertEquals("B", sent.getValue().correctLabel());
        assertEquals("plain", sent.getValue().contentFormat());
        assertEquals(2, sent.getValue().difficulty());
    }

    @Test
    void validatesAllRowsAndRejectsDuplicateTitlesBeforeWrites() {
        assertFalse(validator.validate(request(List.of(new ImportQuestionsRequest.Row(
                "", "", "A", "B", "C", "D", "E", "")))).isEmpty());
        assertFalse(validator.validate(request(Collections.nCopies(201, row("")))).isEmpty());
        assertFalse(validator.validate(new ImportQuestionsRequest(7L, 42L, 2,
                Collections.singletonList(null))).isEmpty());
        QbankException e = assertThrows(QbankException.class, () -> service.importQuestions(
                request(List.of(row(" Duplicate  name "), row("duplicate name"))), 13L));
        assertEquals("IMPORT_DUPLICATE_TITLE", e.getCode());
        verifyNoInteractions(questions);
    }

    @Test
    void stopsOnConflictAndReportsConfirmedRowsOnly() {
        QuestionDetail detail = mock(QuestionDetail.class);
        when(questions.create(any(), eq(13L))).thenReturn(detail)
                .thenThrow(QbankException.conflict("TITLE_DUPLICATED", "duplicate"));
        ImportQuestionsResult result = service.importQuestions(request(List.of(row("One"), row("Two"), row("Three"))), 13L);
        assertEquals(1, result.saved());
        assertEquals(2, result.failedAt());
        assertEquals(409, result.status());
        assertEquals("TITLE_DUPLICATED", result.errorCode());
        assertFalse(result.uncertain());
        assertEquals(List.of(detail), result.questions());
        verify(questions, times(2)).create(any(), eq(13L));
    }

    @Test
    void stopsAndMarksUnknownOutcomeOnUnexpectedFailure() {
        when(questions.create(any(), eq(13L))).thenThrow(new IllegalStateException("database unavailable"));
        ImportQuestionsResult result = service.importQuestions(request(List.of(row("One"), row("Two"))), 13L);
        assertEquals(0, result.saved());
        assertEquals(1, result.failedAt());
        assertTrue(result.uncertain());
        assertNull(result.status());
        verify(questions, times(1)).create(any(), eq(13L));
    }
}
