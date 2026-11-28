package com.example.be_web_admin.controller;

import com.example.be_web_admin.controller.mobile.EnrollmentConfirmControllerMobile;
import com.example.be_web_admin.controller.mobile.TotpDisableControllerMobile;
import com.example.be_web_admin.controller.web.EnrollmentController;
import com.example.be_web_admin.controller.web.EnrollSessionControllerWeb;
import com.example.be_web_admin.controller.web.TwoFaEventControllerWeb;
import com.example.be_web_admin.security.AuthSessionStore;
import com.example.be_web_admin.security.AuthenticationRateLimiter;
import com.example.be_web_admin.security.jwt.user.AdminJwtTokenProvider;
import com.example.be_web_admin.service.redis.impl.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class TwoFaClientSeparationTest {
    @Test
    void unavailableEnrollmentSchemaReturns503WithoutASecret() {
        var jwt = mock(AdminJwtTokenProvider.class);
        var tokens = mock(AdminTokenServiceImpl.class);
        var service = mock(EnrollSessionServiceImpl.class);
        var id = new AuthSessionStore.Identity(7L, AuthSessionStore.Client.WEB, "a9c62262-bdd5-43a9-8a3b-f89bc0c3a870");
        when(jwt.parseIdentity("web", "ACCESS")).thenReturn(id);
        when(tokens.findAdminAccessTokenByJwtToken("web")).thenReturn(true);
        when(service.startEnrollment(7L, null)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("unavailable"));
        var result = new EnrollSessionControllerWeb(service, jwt, tokens).startEnrollment("Bearer web", null);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, result.getStatusCode());
        assertEquals(false, ((Map<?, ?>) result.getBody()).get("success"));
        assertEquals(null, ((Map<?, ?>) result.getBody()).get("data"));
    }

    @Test
    void eventStreamValidatorRechecksTheOriginalTokenAndSession() {
        var jwt = mock(AdminJwtTokenProvider.class);
        var tokens = mock(AdminTokenServiceImpl.class);
        var events = mock(TwoFaEventServiceImpl.class);
        String sid = "a9c62262-bdd5-43a9-8a3b-f89bc0c3a870";
        var id = new AuthSessionStore.Identity(7L, AuthSessionStore.Client.WEB, sid);
        when(jwt.parseIdentity("web", "ACCESS")).thenReturn(id);
        when(tokens.findAdminAccessTokenByJwtToken("web")).thenReturn(true, true, false);
        var controller = new TwoFaEventControllerWeb(events, jwt, tokens);
        controller.events("Bearer web");
        var validator = org.mockito.ArgumentCaptor.forClass(java.util.function.BooleanSupplier.class);
        verify(events).subscribe(eq(7L), validator.capture());
        assertEquals(true, validator.getValue().getAsBoolean());
        assertEquals(false, validator.getValue().getAsBoolean());
    }

    @Test
    void accessTokenFromAnotherClientCannotReachTwoFaOperations() {
        var jwt = mock(AdminJwtTokenProvider.class);
        var tokens = mock(AdminTokenServiceImpl.class);
        var mobileService = mock(EnrollmentConfirmServiceImpl.class);
        var disableService = mock(TotpDisableServiceImpl.class);
        var webService = mock(EnrollmentServiceImpl.class);
        var startService = mock(EnrollSessionServiceImpl.class);
        var eventService = mock(TwoFaEventServiceImpl.class);
        String session = "a9c62262-bdd5-43a9-8a3b-f89bc0c3a870";
        when(jwt.parseIdentity("web", "ACCESS")).thenReturn(new AuthSessionStore.Identity(7L, AuthSessionStore.Client.WEB, session));
        when(jwt.parseIdentity("mobile", "ACCESS")).thenReturn(new AuthSessionStore.Identity(7L, AuthSessionStore.Client.MOBILE, session));
        var mobile = new EnrollmentConfirmControllerMobile(mobileService, jwt, tokens);
        var disable = new TotpDisableControllerMobile(disableService, jwt, tokens);
        var web = new EnrollmentController(webService, jwt, tokens, mock(AuthenticationRateLimiter.class));
        var start = new EnrollSessionControllerWeb(startService, jwt, tokens);
        var events = new TwoFaEventControllerWeb(eventService, jwt, tokens);
        assertEquals(HttpStatus.UNAUTHORIZED, mobile.pending("Bearer web").getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, mobile.receipt("Bearer web", 1L, Map.of("code", "123456")).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, disable.disable("Bearer web", null, null).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, web.getStatus("Bearer mobile").getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, start.startEnrollment("Bearer mobile", null).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, start.cancelEnrollment("Bearer mobile", session).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, events.events("Bearer mobile").getStatusCode());
        verifyNoInteractions(tokens, mobileService, disableService, webService, startService, eventService);
    }
}
