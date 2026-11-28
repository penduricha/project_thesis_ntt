package com.example.be_web_admin.controller;

import com.example.be_web_admin.controller.mobile.EnrollmentConfirmControllerMobile;
import com.example.be_web_admin.controller.mobile.TotpDisableControllerMobile;
import com.example.be_web_admin.controller.web.EnrollmentController;
import com.example.be_web_admin.controller.web.TwoFaEventControllerWeb;
import com.example.be_web_admin.security.jwt.user.AdminJwtTokenProvider;
import com.example.be_web_admin.security.AuthSessionStore;
import com.example.be_web_admin.security.AuthenticationRateLimiter;
import com.example.be_web_admin.service.redis.impl.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TwoFaAuthAvailabilityTest {
    @Test
    void secondAuthLookupOutageIs503Not401() {
        AdminJwtTokenProvider jwt = mock(AdminJwtTokenProvider.class);
        AdminTokenServiceImpl tokens = mock(AdminTokenServiceImpl.class);
        when(jwt.parseIdentity("mobile", "ACCESS")).thenReturn(new AuthSessionStore.Identity(1L, AuthSessionStore.Client.MOBILE, "a9c62262-bdd5-43a9-8a3b-f89bc0c3a870"));
        when(jwt.parseIdentity("web", "ACCESS")).thenReturn(new AuthSessionStore.Identity(1L, AuthSessionStore.Client.WEB, "a9c62262-bdd5-43a9-8a3b-f89bc0c3a870"));
        when(tokens.findAdminAccessTokenByJwtToken(anyString())).thenThrow(new IllegalStateException("offline"));
        EnrollmentConfirmControllerMobile confirm = new EnrollmentConfirmControllerMobile(
                mock(EnrollmentConfirmServiceImpl.class), jwt, tokens);
        TotpDisableControllerMobile disable = new TotpDisableControllerMobile(
                mock(TotpDisableServiceImpl.class), jwt, tokens);
        EnrollmentController web = new EnrollmentController(mock(EnrollmentServiceImpl.class), jwt, tokens, mock(AuthenticationRateLimiter.class));
        TwoFaEventControllerWeb events = new TwoFaEventControllerWeb(mock(TwoFaEventServiceImpl.class), jwt, tokens);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(ResponseStatusException.class,
                () -> confirm.pending("Bearer mobile")).getStatusCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(ResponseStatusException.class,
                () -> disable.disable("Bearer mobile", null, null)).getStatusCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(ResponseStatusException.class,
                () -> web.getStatus("Bearer web")).getStatusCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(ResponseStatusException.class,
                () -> events.events("Bearer web")).getStatusCode());
    }

    @Test
    void invalidTokenRemainsUnauthorized() {
        AdminJwtTokenProvider jwt = mock(AdminJwtTokenProvider.class);
        AdminTokenServiceImpl tokens = mock(AdminTokenServiceImpl.class);
        EnrollmentConfirmControllerMobile confirm = new EnrollmentConfirmControllerMobile(
                mock(EnrollmentConfirmServiceImpl.class), jwt, tokens);
        assertEquals(HttpStatus.UNAUTHORIZED, confirm.pending("Bearer invalid").getStatusCode());
        verifyNoInteractions(tokens);
    }
}
