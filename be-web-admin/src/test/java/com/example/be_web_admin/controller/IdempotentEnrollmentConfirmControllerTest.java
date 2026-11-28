package com.example.be_web_admin.controller;

import com.example.be_web_admin.controller.mobile.IdempotentEnrollmentConfirmController;
import com.example.be_web_admin.security.AuthSessionStore;
import com.example.be_web_admin.security.jwt.user.AdminJwtTokenProvider;
import com.example.be_web_admin.service.redis.impl.AdminTokenServiceImpl;
import com.example.be_web_admin.service.redis.impl.IdempotentEnrollmentConfirmService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdempotentEnrollmentConfirmControllerTest {
    @Test
    void gatedByDefaultAndRequiresMobileSessionAndCompleteRequest() {
        var gate = IdempotentEnrollmentConfirmController.class.getAnnotation(ConditionalOnProperty.class);
        assertNotNull(gate);
        assertFalse(gate.matchIfMissing());
        var service = mock(IdempotentEnrollmentConfirmService.class);
        var jwt = mock(AdminJwtTokenProvider.class);
        var tokens = mock(AdminTokenServiceImpl.class);
        var http = mock(HttpServletRequest.class);
        var controller = new IdempotentEnrollmentConfirmController(service, jwt, tokens);
        String sid = "a9c62262-bdd5-43a9-8a3b-f89bc0c3a870";
        when(jwt.parseIdentity("web", "ACCESS")).thenReturn(new AuthSessionStore.Identity(7L, AuthSessionStore.Client.WEB, sid));
        assertEquals(HttpStatus.UNAUTHORIZED, controller.confirm("Bearer web", Map.of(), http).getStatusCode());
        when(jwt.parseIdentity("mobile", "ACCESS")).thenReturn(new AuthSessionStore.Identity(7L, AuthSessionStore.Client.MOBILE, sid));
        when(tokens.findAdminAccessTokenByJwtToken("mobile")).thenReturn(true);
        assertEquals(HttpStatus.BAD_REQUEST, controller.confirm("Bearer mobile", Map.of("code", "123456"), http).getStatusCode());
        verifyNoInteractions(service);
        String id = "6b".repeat(32);
        var request = Map.<String, Object>of("code", "123456", "credentialId", 42L, "requestId", id);
        when(service.confirm(any(), eq(42L), eq(id), eq("123456"), any(), any()))
                .thenReturn(IdempotentEnrollmentConfirmService.Result.CREATED, IdempotentEnrollmentConfirmService.Result.REPLAYED);
        var first = controller.confirm("Bearer mobile", request, http);
        var retry = controller.confirm("Bearer mobile", request, http);
        assertEquals(HttpStatus.OK, first.getStatusCode());
        assertEquals(first.getBody(), retry.getBody());
        assertEquals("no-store", first.getHeaders().getCacheControl());
    }
}
