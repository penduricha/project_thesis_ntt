package com.example.be_web_admin.controller;

import com.example.be_web_admin.controller.web.EnrollmentController;
import com.example.be_web_admin.security.AuthSessionStore;
import com.example.be_web_admin.security.AuthenticationRateLimiter;
import com.example.be_web_admin.security.jwt.user.AdminJwtTokenProvider;
import com.example.be_web_admin.service.redis.impl.AdminTokenServiceImpl;
import com.example.be_web_admin.service.redis.impl.EnrollmentServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EnrollmentReconcileControllerTest {
    @Test
    void requiresWebSessionGenerationAndRateLimitBeforeReconciliation() {
        var jwt = mock(AdminJwtTokenProvider.class);
        var tokens = mock(AdminTokenServiceImpl.class);
        var limiter = mock(AuthenticationRateLimiter.class);
        var service = mock(EnrollmentServiceImpl.class);
        var controller = new EnrollmentController(service, jwt, tokens, limiter);
        var request = mock(HttpServletRequest.class);
        String gen = "a9c62262-bdd5-43a9-8a3b-f89bc0c3a870";
        when(jwt.parseIdentity("mobile", "ACCESS"))
                .thenReturn(new AuthSessionStore.Identity(7L, AuthSessionStore.Client.MOBILE, gen));
        assertEquals(HttpStatus.UNAUTHORIZED,
                controller.reconcileEnrollment("Bearer mobile", Map.of("generation", gen), request).getStatusCode());
        when(jwt.parseIdentity("web", "ACCESS"))
                .thenReturn(new AuthSessionStore.Identity(7L, AuthSessionStore.Client.WEB, gen));
        when(tokens.findAdminAccessTokenByJwtToken("web")).thenReturn(true);
        assertEquals(HttpStatus.PRECONDITION_REQUIRED,
                controller.reconcileEnrollment("Bearer web", Map.of(), request).getStatusCode());
        when(limiter.reserve(eq("enrollment-reconcile"), eq(7L), eq(10), any())).thenReturn(15L, 0L);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS,
                controller.reconcileEnrollment("Bearer web", Map.of("generation", gen), request).getStatusCode());
        verifyNoInteractions(service);
        when(service.reconcileClaim(eq(7L), eq(gen), any(), any())).thenReturn(new EnrollmentServiceImpl.ReconcileResult("CANCELLED", true));
        assertEquals(HttpStatus.OK,
                controller.reconcileEnrollment("Bearer web", Map.of("generation", gen), request).getStatusCode());
        verify(service).reconcileClaim(eq(7L), eq(gen), any(), any());
    }
}
