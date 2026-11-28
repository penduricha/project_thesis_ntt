package com.example.be_web_admin.qbank;

import com.example.be_web_admin.controller.web.QuestionBankController;
import com.example.be_web_admin.dto.qbank.CreateQuestionRequest;
import com.example.be_web_admin.dto.qbank.QuestionDetail;
import com.example.be_web_admin.exception.qbank.QBankExceptionHandler;
import com.example.be_web_admin.service.qbank.AiCheckService;
import com.example.be_web_admin.service.qbank.QuestionImportService;
import com.example.be_web_admin.service.qbank.QuestionService;
import com.example.be_web_admin.service.qbank.UiStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** MVC binding/validation, independent of database. Method security is covered by the existing configuration. */
class QuestionImportControllerTest {
    private final QuestionService questions = mock(QuestionService.class);
    private MockMvc mvc;
    private static final String VALID = """
            {"subjectId":7,"topicId":42,"difficulty":2,"questions":[
              {"stem":"Question?","optionA":"A","optionB":"B","optionC":"C","optionD":"D","correctLabel":"B"}
            ]}
            """;

    @BeforeEach
    void setup() {
        QuestionBankController controller = new QuestionBankController(questions,
                new QuestionImportService(questions), mock(AiCheckService.class), mock(UiStateStore.class));
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new QBankExceptionHandler()).build();
    }

    @Test
    void validBatchUsesAuthenticatedPrincipalAndReturnsCreatedRows() throws Exception {
        QuestionDetail detail = new QuestionDetail(1L, 7L, "DB", 42L, "Basics", null,
                "Question?", "A", "B", "C", "D", "B", "plain", null, 2,
                "active", "not_checked", null, "manual", "hash", 1, null, null, 1, 0L);
        when(questions.create(any(CreateQuestionRequest.class), eq(13L))).thenReturn(detail);
        mvc.perform(post("/web/admin/questions/import").contentType(MediaType.APPLICATION_JSON)
                        .principal(UsernamePasswordAuthenticationToken.authenticated(13L, "", java.util.List.of()))
                        .content(VALID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(1))
                .andExpect(jsonPath("$.questions[0].questionId").value(1));
        verify(questions).create(any(CreateQuestionRequest.class), eq(13L));
    }

    @Test
    void invalidAnswerNeverInvokesCreate() throws Exception {
        mvc.perform(post("/web/admin/questions/import").contentType(MediaType.APPLICATION_JSON)
                        .principal(UsernamePasswordAuthenticationToken.authenticated(13L, "", java.util.List.of()))
                        .content(VALID.replace("\"correctLabel\":\"B\"", "\"correctLabel\":\"E\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(questions);
    }

    @Test
    void extraOptionIsRejectedBeforeAnyWrites() throws Exception {
        mvc.perform(post("/web/admin/questions/import").contentType(MediaType.APPLICATION_JSON)
                        .principal(UsernamePasswordAuthenticationToken.authenticated(13L, "", java.util.List.of()))
                        .content(VALID.replace("\"correctLabel\":\"B\"", "\"correctLabel\":\"B\",\"optionE\":\"extra\"")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(questions);
    }

    @Test
    void cannotSupplyAdminIdentityInBodyWithoutPrincipal() throws Exception {
        mvc.perform(post("/web/admin/questions/import").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(questions);
    }
}
