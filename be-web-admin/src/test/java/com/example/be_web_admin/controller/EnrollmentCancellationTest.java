package com.example.be_web_admin.controller;

import com.example.be_web_admin.controller.web.EnrollmentController;
import com.example.be_web_admin.security.jwt.user.AdminJwtTokenProvider;
import com.example.be_web_admin.security.AuthSessionStore;
import com.example.be_web_admin.security.AuthenticationRateLimiter;
import com.example.be_web_admin.service.redis.impl.AdminTokenServiceImpl;
import com.example.be_web_admin.service.redis.impl.EnrollmentServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class EnrollmentCancellationTest {
    @Test
    void staleOrLegacyCancellationDoesNotDeleteCurrentEnrollment() {
        var jwt = mock(AdminJwtTokenProvider.class);
        var tokens = mock(AdminTokenServiceImpl.class);
        var service = mock(EnrollmentServiceImpl.class);
        var request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("peer");
        when(request.getHeader(anyString())).thenReturn("ua");
        when(jwt.parseIdentity("token", "ACCESS")).thenReturn(new AuthSessionStore.Identity(7L, AuthSessionStore.Client.WEB, "a9c62262-bdd5-43a9-8a3b-f89bc0c3a870"));
        when(tokens.findAdminAccessTokenByJwtToken("token")).thenReturn(true);
        var controller = new EnrollmentController(service, jwt, tokens, mock(AuthenticationRateLimiter.class));
        assertEquals(HttpStatus.PRECONDITION_REQUIRED, controller.cancelPending("Bearer token", null, request).getStatusCode());
        verifyNoInteractions(service);
        assertEquals(HttpStatus.CONFLICT, controller.cancelPending("Bearer token", 41L, request).getStatusCode());
        when(service.cancelPending(eq(7L), eq(42L), any(), any())).thenReturn(true);
        assertEquals(HttpStatus.OK, controller.cancelPending("Bearer token", 42L, request).getStatusCode());
    }
}
