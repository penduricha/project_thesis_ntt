package com.example.be_web_admin.security;

import com.example.be_web_admin.model.jpa.Admin;
import com.example.be_web_admin.model.jpa.TotpCredential;
import com.example.be_web_admin.repository.jpa.AdminRepository;
import com.example.be_web_admin.repository.jpa.TotpCredentialRepository;
import com.example.be_web_admin.security.recovery.RecoveryCodeStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RecoveryCodeStoreTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AdminRepository admins = mock(AdminRepository.class);
    private final TotpCredentialRepository credentials = mock(TotpCredentialRepository.class);
    private final AuthenticationRateLimiter limiter = mock(AuthenticationRateLimiter.class);
    private final RecoveryCodeStore store = new RecoveryCodeStore(jdbc, admins, credentials, limiter);

    private void activeCredential() {
        Admin admin = new Admin();
        admin.setTotpEnabled(true);
        TotpCredential c = new TotpCredential();
        c.setCredentialId(42L);
        when(admins.findForTotpUpdate(7L)).thenReturn(admin);
        when(credentials.findFirstByAdmin_AdminIdAndConfirmedTrueOrderByConfirmedAtDesc(7L))
                .thenReturn(Optional.of(c));
    }

    @Test
    void prepareStoresOnlyHighEntropyCodeHashesAndDoesNotRetireOldSet() throws Exception {
        activeCredential();
        var set = store.prepare(7L, 42L);
        assertEquals(10, set.codes().size());
        assertEquals(10, new HashSet<>(set.codes()).size());
        assertFalse(set.toString().contains(set.codes().get(0)));
        ArgumentCaptor<byte[]> hashes = ArgumentCaptor.forClass(byte[].class);
        verify(jdbc, times(10)).update(startsWith("INSERT INTO ntt_totp_recovery_codes"), eq(set.setId()), hashes.capture());
        for (int i = 0; i < set.codes().size(); i++) {
            assertTrue(set.codes().get(i).matches("[A-Za-z0-9_-]{27}"));
            assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(set.codes().get(i).getBytes(StandardCharsets.US_ASCII)), hashes.getAllValues().get(i));
        }
        verify(jdbc, never()).update(startsWith("UPDATE ntt_totp_recovery_sets"), any(Object[].class));
    }

    @Test
    void recoveryAttemptsFailClosedWhenRateLimited() {
        when(limiter.reserve(eq("recovery-consume"), eq(7L), anyInt(), any())).thenReturn(30L);
        assertThrows(RecoveryCodeStore.RecoveryRateLimitException.class,
                () -> store.consume(7L, 42L, "A".repeat(27)));
        verifyNoInteractions(jdbc, admins, credentials);
    }

    @Test
    void consumeUsesConditionalSingleUseUpdateAndRejectsBadInput() {
        activeCredential();
        String code = "A".repeat(27);
        when(jdbc.queryForObject(contains("SELECT COUNT(*)"), eq(Integer.class), eq(7L))).thenReturn(1);
        when(jdbc.update(startsWith("UPDATE ntt_totp_recovery_codes"), any(), eq(7L), eq(42L), any(byte[].class)))
                .thenReturn(1, 0);
        assertFalse(store.consume(7L, 42L, "bad"));
        assertTrue(store.consume(7L, 42L, code));
        assertFalse(store.consume(7L, 42L, code));
        verify(jdbc, times(2)).update(contains("c.used_at IS NULL"), any(), eq(7L), eq(42L), any(byte[].class));
    }
}
