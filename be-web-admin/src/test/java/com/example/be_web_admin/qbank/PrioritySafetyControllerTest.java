package com.example.be_web_admin.qbank;

import com.example.be_web_admin.controller.web.QuestionBankController;
import com.example.be_web_admin.exception.qbank.QbankException;
import com.example.be_web_admin.exception.qbank.QbankExceptionHandler;
import com.example.be_web_admin.service.qbank.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PrioritySafetyControllerTest {
    @Test
    void draftFailureReturns503RatherThanSuccessOrNoContent() throws Exception {
        UiStateStore state = mock(UiStateStore.class);
        QuestionBankController controller = new QuestionBankController(mock(QuestionService.class),
                mock(QuestionImportService.class), mock(AiCheckService.class), state);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new QbankExceptionHandler()).build();
        var principal = UsernamePasswordAuthenticationToken.authenticated(13L, "", List.of());
        doThrow(QbankException.serviceUnavailable("DRAFT_UNAVAILABLE", "unavailable"))
                .when(state).saveDraft(eq(13L), anyString(), any());
        when(state.readDraft(eq(13L), anyString()))
                .thenThrow(QbankException.serviceUnavailable("DRAFT_UNAVAILABLE", "unavailable"));
        mvc.perform(put("/web/admin/questions/draft").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"stem\":\"question\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("DRAFT_UNAVAILABLE"));
        mvc.perform(get("/web/admin/questions/draft").principal(principal))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("DRAFT_UNAVAILABLE"));
    }

    @Test
    void suggestionRequestUsesAuthenticatedIdentity() throws Exception {
        QuestionService questions = mock(QuestionService.class);
        QuestionBankController controller = new QuestionBankController(questions,
                mock(QuestionImportService.class), mock(AiCheckService.class), mock(UiStateStore.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new QbankExceptionHandler()).build();
        when(questions.latestSuggestions(10L, 13L)).thenReturn(List.of());
        mvc.perform(get("/web/admin/questions/10/ai-suggestions")
                        .principal(UsernamePasswordAuthenticationToken.authenticated(13L, "", List.of())))
                .andExpect(status().isOk());
        verify(questions).latestSuggestions(10L, 13L);
    }
}
