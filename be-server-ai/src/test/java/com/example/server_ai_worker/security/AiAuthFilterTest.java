package com.example.server_ai_worker.security;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import com.example.server_ai_worker.config.AiProperties;

class AiAuthFilterTest {
    private static final String SECRET = "TestOnlySecretKeyForAiWorkerJwt_AtLeast32BytesLong_2026";
    private static final String INTERNAL = "test-only-internal-token";

    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @Test void userJwtIsNeverGrantedWorkerAccessEvenIfSignatureIsValid() throws Exception {
        AiProperties props = new AiProperties();
        props.getSecurity().setJwtSecret(SECRET);
        props.getSecurity().setInternalToken(INTERNAL);
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"sub\":\"1\",\"token_type\":\"ACCESS\",\"exp\":" + Instant.now().plusSeconds(300).getEpochSecond() + "}").getBytes(StandardCharsets.UTF_8));
        String input = header + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String jwt = input + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(input.getBytes(StandardCharsets.US_ASCII)));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwt);
        new AiAuthFilter(props).doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test void onlyCorrectInternalCredentialAuthenticates() throws Exception {
        AiProperties props = new AiProperties(); props.getSecurity().setInternalToken(INTERNAL);
        MockHttpServletRequest wrong = new MockHttpServletRequest(); wrong.addHeader("X-Internal-Token", "incorrect");
        new AiAuthFilter(props).doFilter(wrong, new MockHttpServletResponse(), new MockFilterChain());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        MockHttpServletRequest request = new MockHttpServletRequest(); request.addHeader("X-Internal-Token", INTERNAL);
        new AiAuthFilter(props).doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertTrue(authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_INTERNAL")));
        assertFalse(authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test void guardRequiresInternalCredentialOutsideDevEvenWhenJwtIsConfigured() {
        AiProperties props = new AiProperties(); props.getSecurity().setJwtSecret(SECRET);
        MockEnvironment prod = new MockEnvironment(); prod.setActiveProfiles("prod");
        AiSecurityGuard guard = new AiSecurityGuard(props, prod);
        assertThrows(IllegalStateException.class, guard::validateOnStartup);
        props.getSecurity().setInternalToken(INTERNAL);
        guard.validateOnStartup();
    }
}
