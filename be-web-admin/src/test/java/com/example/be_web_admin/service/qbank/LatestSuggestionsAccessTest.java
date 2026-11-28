package com.example.be_web_admin.service.qbank;

import com.example.be_web_admin.enums.AccountStatus;
import com.example.be_web_admin.enums.Role;
import com.example.be_web_admin.enums.ai.TaskTypeD;
import com.example.be_web_admin.exception.qbank.QBankException;
import com.example.be_web_admin.model.jpa.Admin;
import com.example.be_web_admin.model.jpa.ai.AiTask;
import com.example.be_web_admin.model.jpa.qbank.Question;
import com.example.be_web_admin.repository.jpa.AdminRepository;
import com.example.be_web_admin.repository.jpa.ai.*;
import com.example.be_web_admin.repository.jpa.qbank.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LatestSuggestionsAccessTest {
    @Test
    void onlyOwnerOrActiveAdminCanReadLatestSuggestions() {
        AdminRepository admins = mock(AdminRepository.class);
        QuestionRepository questions = mock(QuestionRepository.class);
        AiTaskRepository tasks = mock(AiTaskRepository.class);
        AiSuggestionRepository suggestions = mock(AiSuggestionRepository.class);
        IssueTypeRepository types = mock(IssueTypeRepository.class);
        QuestionService service = new QuestionService(admins, questions, mock(QuestionRevisionRepository.class),
                mock(SubjectRepository.class), mock(TopicRepository.class), tasks, suggestions, types,
                mock(QuestionSnapshotFactory.class), mock(UiStateStore.class));
        AiTask task = new AiTask(); task.setTaskId("task"); task.setCreatedBy(1L);
        when(questions.findById(10L)).thenReturn(Optional.of(new Question()));
        when(tasks.findTopByQuestionIdAndTaskTypeOrderByCreatedAtDesc(10L, TaskTypeD.QUESTION_CHECK)).thenReturn(Optional.of(task));
        Admin owner = new Admin(); owner.setRole(Role.LECTURER);
        Admin other = new Admin(); other.setRole(Role.LECTURER);
        Admin admin = new Admin(); admin.setRole(Role.ADMIN);
        Admin disabled = new Admin(); disabled.setRole(Role.ADMIN); disabled.setAccountStatus(AccountStatus.DISABLED);
        when(admins.findAdminByAdminId(1L)).thenReturn(owner);
        when(admins.findAdminByAdminId(2L)).thenReturn(other);
        when(admins.findAdminByAdminId(3L)).thenReturn(admin);
        when(admins.findAdminByAdminId(4L)).thenReturn(disabled);
        when(types.findAll()).thenReturn(List.of());
        when(suggestions.findByTaskIdOrderBySeqNoAsc("task")).thenReturn(List.of());
        assertThrows(QBankException.class, () -> service.latestSuggestions(10L, 2L));
        assertThrows(QBankException.class, () -> service.latestSuggestions(10L, 4L));
        assertThrows(QBankException.class, () -> service.latestSuggestions(10L, null));
        verifyNoInteractions(suggestions);
        assertEquals(List.of(), service.latestSuggestions(10L, 1L));
        assertEquals(List.of(), service.latestSuggestions(10L, 3L));
        verify(suggestions, times(2)).findByTaskIdOrderBySeqNoAsc("task");
    }
}
