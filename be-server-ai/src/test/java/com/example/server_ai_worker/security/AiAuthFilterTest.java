package com.example.server_ai_worker.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.example.server_ai_worker.config.AiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

class AiAuthFilterTest {

    private static final String TEST_SECRET = "TestOnlySecretKeyForAiWorkerJwt_AtLeast32BytesLong_2026";
    private AiAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new AiAuthFilter(new AiProperties(), new ObjectMapper());
    }

    @Test
    void verifiesValidAccessJwtAndRejectsRefreshOrExpiredJwt() throws Exception {
        long futureExp = Instant.now().plusSeconds(300).getEpochSecond();
        String accessJwt = signHs256(
                "{\"alg\":\"HS256\",\"typ\":\"JWT\"}",
                "{\"sub\":\"2500021775\",\"token_type\":\"ACCESS\",\"exp\":" + futureExp + "}",
                TEST_SECRET);
        assertEquals(2500021775L, filter.verifyAccessJwt(accessJwt, TEST_SECRET));

        String refreshJwt = signHs256(
                "{\"alg\":\"HS256\",\"typ\":\"JWT\"}",
                "{\"sub\":\"2500021775\",\"token_type\":\"REFRESH\",\"exp\":" + futureExp + "}",
                TEST_SECRET);
        assertNull(filter.verifyAccessJwt(refreshJwt, TEST_SECRET));

        long pastExp = Instant.now().minusSeconds(60).getEpochSecond();
        String expiredJwt = signHs256(
                "{\"alg\":\"HS256\",\"typ\":\"JWT\"}",
                "{\"sub\":\"2500021775\",\"token_type\":\"ACCESS\",\"exp\":" + pastExp + "}",
                TEST_SECRET);
        assertNull(filter.verifyAccessJwt(expiredJwt, TEST_SECRET));
    }

    @Test
    void rejectsTamperedJwt() throws Exception {
        long futureExp = Instant.now().plusSeconds(300).getEpochSecond();
        String jwt = signHs256(
                "{\"alg\":\"HS256\",\"typ\":\"JWT\"}",
                "{\"sub\":\"2500021775\",\"token_type\":\"ACCESS\",\"exp\":" + futureExp + "}",
                TEST_SECRET);
        String tampered = jwt.substring(0, jwt.length() - 4) + "AAAA";
        assertNull(filter.verifyAccessJwt(tampered, TEST_SECRET));
    }

    @Test
    void securityGuardRequiresCredentialsOutsideDevProfile() {
        AiProperties props = new AiProperties();
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");
        AiSecurityGuard guard = new AiSecurityGuard(props, prodEnv);
        assertThrows(IllegalStateException.class, guard::validateOnStartup);

        props.getSecurity().setInternalToken("internal-token-123456");
        guard.validateOnStartup();
    }

    private static String signHs256(String headerJson, String payloadJson, String secret) throws Exception {
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        String signingInput = enc.encodeToString(headerJson.getBytes(StandardCharsets.UTF_8))
                + "."
                + enc.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String sig = enc.encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII)));
        return signingInput + "." + sig;
    }
}
