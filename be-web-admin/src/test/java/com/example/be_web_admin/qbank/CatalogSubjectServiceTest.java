package com.example.be_web_admin.qbank;

import com.example.be_web_admin.controller.web.CatalogController;
import com.example.be_web_admin.controller.web.WebRoles;
import com.example.be_web_admin.dto.qbank.*;
import com.example.be_web_admin.exception.qbank.QbankException;
import com.example.be_web_admin.model.jpa.qbank.Subject;
import com.example.be_web_admin.model.jpa.qbank.Topic;
import com.example.be_web_admin.repository.jpa.ai.IssueTypeRepository;
import com.example.be_web_admin.repository.jpa.qbank.SubjectRepository;
import com.example.be_web_admin.repository.jpa.qbank.TopicRepository;
import com.example.be_web_admin.service.qbank.CatalogService;
import com.example.be_web_admin.service.qbank.UiStateStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CatalogSubjectServiceTest {
    private final SubjectRepository subjects = mock(SubjectRepository.class);
    private final TopicRepository topics = mock(TopicRepository.class);
    private final CatalogService service = new CatalogService(subjects, topics, mock(IssueTypeRepository.class), mock(UiStateStore.class), mock(jakarta.persistence.EntityManager.class));
    private static final Instant TIME = Instant.parse("2026-01-01T00:00:00.123456Z");

    @Test
    void createsNormalizedSubjectAndRootTopic() throws Exception {
        when(subjects.saveAndFlush(any(Subject.class))).thenAnswer(call -> {
            Subject s = call.getArgument(0); s.setSubjectId(12L); s.setUpdatedAt(TIME); return s;
        });
        SubjectView result = service.createSubject(new SubjectCreateRequest(" it-101 ", "Cơ sở dữ liệu", "Mô tả", true, "Chương 1: Tổng quan"), 7L);
        assertEquals("IT-101", result.subjectCode());
        ArgumentCaptor<Subject> subject = ArgumentCaptor.forClass(Subject.class);
        verify(subjects).saveAndFlush(subject.capture());
        assertEquals(7L, subject.getValue().getCreatedBy());
        ArgumentCaptor<Topic> topic = ArgumentCaptor.forClass(Topic.class);
        verify(topics).saveAndFlush(topic.capture());
        assertEquals(12L, topic.getValue().getSubjectId());
        assertNull(topic.getValue().getParentId());
        assertEquals("Chương 1: Tổng quan", topic.getValue().getTopicName());
        assertNotNull(CatalogService.class.getMethod("createSubject", SubjectCreateRequest.class, Long.class).getAnnotation(Transactional.class));
    }

    @Test
    void invalidCharactersAndDuplicateAreRejectedBeforeWriting() {
        QbankException badCode = assertThrows(QbankException.class, () -> service.createSubject(new SubjectCreateRequest("A@1", "Mạng máy tính", "", true, "Chương 1"), 7L));
        assertEquals("SUBJECT_CODE_INVALID", badCode.getCode());
        QbankException badName = assertThrows(QbankException.class, () -> service.createSubject(new SubjectCreateRequest("A1", "Môn <script>", "", true, "Chương 1"), 7L));
        assertEquals("SUBJECT_NAME_INVALID", badName.getCode());
        when(subjects.existsBySubjectCodeIgnoreCase("A1")).thenReturn(true);
        QbankException duplicate = assertThrows(QbankException.class, () -> service.createSubject(new SubjectCreateRequest("a1", "Môn học", "", true, "Chương 1"), 7L));
        assertEquals("SUBJECT_CODE_DUPLICATED", duplicate.getCode());
        verify(subjects, never()).saveAndFlush(any());
        verifyNoInteractions(topics);
    }

    @Test
    void staleUpdateDoesNotOverwriteNewerSubject() {
        Subject subject = new Subject(); subject.setSubjectId(12L); subject.setUpdatedAt(TIME);
        when(subjects.findForQuestionCreate(12L)).thenReturn(Optional.of(subject));
        SubjectUpdateRequest request = new SubjectUpdateRequest("X", "Tên mới", null, true, TIME.minusSeconds(1));
        QbankException error = assertThrows(QbankException.class, () -> service.updateSubject(12L, request));
        assertEquals("SUBJECT_VERSION_CONFLICT", error.getCode());
        verify(subjects, never()).saveAndFlush(any());
    }

    @Test
    void archiveKeepsSubjectAndReferences() {
        Subject subject = new Subject(); subject.setSubjectId(12L); subject.setUpdatedAt(TIME); subject.setIsActive(true);
        when(subjects.findForQuestionCreate(12L)).thenReturn(Optional.of(subject));
        when(subjects.saveAndFlush(any(Subject.class))).thenAnswer(call -> call.getArgument(0));
        SubjectView result = service.archiveSubject(12L, new SubjectArchiveRequest(TIME));
        assertFalse(result.active());
        verify(subjects, never()).delete(any());
        verifyNoInteractions(topics);
    }

    @Test
    void subjectWritesRequireAdmin() throws Exception {
        assertEquals(WebRoles.ADMIN, CatalogController.class.getMethod("createSubject", SubjectCreateRequest.class, org.springframework.security.core.Authentication.class).getAnnotation(PreAuthorize.class).value());
        assertEquals(WebRoles.ADMIN, CatalogController.class.getMethod("updateSubject", Long.class, SubjectUpdateRequest.class).getAnnotation(PreAuthorize.class).value());
        assertEquals(WebRoles.ADMIN, CatalogController.class.getMethod("archiveSubject", Long.class, SubjectArchiveRequest.class).getAnnotation(PreAuthorize.class).value());
    }
}
