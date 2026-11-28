package com.example.be_web_admin.controller;

import com.example.be_web_admin.controller.web.EnrollSessionControllerWeb;
import com.example.be_web_admin.controller.web.EnrollmentController;
import com.example.be_web_admin.security.jwt.user.AdminJwtTokenProvider;
import com.example.be_web_admin.security.AuthSessionStore;
import com.example.be_web_admin.security.AuthenticationRateLimiter;
import com.example.be_web_admin.service.redis.impl.AdminTokenServiceImpl;
import com.example.be_web_admin.service.redis.impl.EnrollSessionServiceImpl;
import com.example.be_web_admin.service.redis.impl.EnrollmentServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class EnrollmentGenerationTest {
    @Test
    void legacyRequestsCannotActivateOrCancelAnotherGeneration() {
        var jwt = mock(AdminJwtTokenProvider.class);
        var tokens = mock(AdminTokenServiceImpl.class);
        var sessions = mock(EnrollSessionServiceImpl.class);
        var enrollment = mock(EnrollmentServiceImpl.class);
        var request = mock(HttpServletRequest.class);
        when(jwt.parseIdentity("token", "ACCESS")).thenReturn(new AuthSessionStore.Identity(7L, AuthSessionStore.Client.WEB, "a9c62262-bdd5-43a9-8a3b-f89bc0c3a870"));
        when(tokens.findAdminAccessTokenByJwtToken("token")).thenReturn(true);
        var sessionsController = new EnrollSessionControllerWeb(sessions, jwt, tokens);
        var enrollmentController = new EnrollmentController(enrollment, jwt, tokens, mock(AuthenticationRateLimiter.class));
        assertEquals(HttpStatus.PRECONDITION_REQUIRED,
                sessionsController.cancelEnrollment("Bearer token", null).getStatusCode());
        assertEquals(HttpStatus.PRECONDITION_REQUIRED,
                enrollmentController.activateEnrollment("Bearer token", Map.of("periodSeconds", 30), request).getStatusCode());
        verifyNoInteractions(sessions, enrollment);
        String generation = "a9c62262-bdd5-43a9-8a3b-f89bc0c3a870";
        when(sessions.cancelEnrollment(7L, generation)).thenReturn(true);
        assertEquals(HttpStatus.OK, sessionsController.cancelEnrollment("Bearer token", generation).getStatusCode());
        verify(sessions).cancelEnrollment(7L, generation);
    }
}
