package com.example.be_web_admin.controller;

import com.example.be_web_admin.controller.mobile.EnrollmentConfirmControllerMobile;
import com.example.be_web_admin.security.jwt.user.AdminJwtTokenProvider;
import com.example.be_web_admin.security.AuthSessionStore;
import com.example.be_web_admin.service.redis.impl.AdminTokenServiceImpl;
import com.example.be_web_admin.service.redis.impl.EnrollmentConfirmServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class EnrollmentReceiptTest {
    @Test
    void receiptRequiresAuthenticationAndProofForExactCredential() {
        var jwt = mock(AdminJwtTokenProvider.class);
        var tokens = mock(AdminTokenServiceImpl.class);
        var service = mock(EnrollmentConfirmServiceImpl.class);
        var controller = new EnrollmentConfirmControllerMobile(service, jwt, tokens);
        assertEquals(HttpStatus.UNAUTHORIZED, controller.receipt(null, 42L, Map.of("code", "123456")).getStatusCode());
        verifyNoInteractions(service);
        when(jwt.parseIdentity("token", "ACCESS")).thenReturn(new AuthSessionStore.Identity(7L, AuthSessionStore.Client.MOBILE, "a9c62262-bdd5-43a9-8a3b-f89bc0c3a870"));
        when(tokens.findAdminAccessTokenByJwtToken("token")).thenReturn(true);
        assertEquals(HttpStatus.PRECONDITION_REQUIRED,
                controller.confirm("Bearer token", Map.of("code", "123456"), mock(HttpServletRequest.class)).getStatusCode());
        verifyNoInteractions(service);
        assertEquals(HttpStatus.BAD_REQUEST, controller.receipt("Bearer token", 42L, Map.of("code", "oops")).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, controller.receipt("Bearer token", 42L, Map.of("code", "123456")).getStatusCode());
        when(service.verifyConfirmedGeneration(7L, 42L, "123456")).thenReturn(true);
        assertEquals(HttpStatus.OK, controller.receipt("Bearer token", 42L, Map.of("code", "123456")).getStatusCode());
        verify(service, times(2)).verifyConfirmedGeneration(7L, 42L, "123456");
    }
}
