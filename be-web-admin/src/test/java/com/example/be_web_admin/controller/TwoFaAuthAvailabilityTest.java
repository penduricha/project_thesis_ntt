package com.example.be_web_admin.controller;

import com.example.be_web_admin.controller.mobile.EnrollmentConfirmControllerMobile;
import com.example.be_web_admin.controller.mobile.TotpDisableControllerMobile;
import com.example.be_web_admin.controller.web.EnrollmentController;
import com.example.be_web_admin.controller.web.TwoFaEventControllerWeb;
import com.example.be_web_admin.security.jwt.user.AdminJwtTokenProvider;
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
        when(jwt.parseAccessTokenAdminId("token")).thenReturn(1L);
        when(tokens.findAdminAccessTokenByJwtToken("token")).thenThrow(new IllegalStateException("offline"));
        EnrollmentConfirmControllerMobile confirm = new EnrollmentConfirmControllerMobile(
                mock(EnrollmentConfirmServiceImpl.class), jwt, tokens);
        TotpDisableControllerMobile disable = new TotpDisableControllerMobile(
                mock(TotpDisableServiceImpl.class), jwt, tokens);
        EnrollmentController web = new EnrollmentController(mock(EnrollmentServiceImpl.class), jwt, tokens);
        TwoFaEventControllerWeb events = new TwoFaEventControllerWeb(mock(TwoFaEventServiceImpl.class), jwt, tokens);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(ResponseStatusException.class,
                () -> confirm.pending("Bearer token")).getStatusCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(ResponseStatusException.class,
                () -> disable.disable("Bearer token", null, null)).getStatusCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(ResponseStatusException.class,
                () -> web.getStatus("Bearer token")).getStatusCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(ResponseStatusException.class,
                () -> events.events("Bearer token")).getStatusCode());
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
