package com.example.be_web_admin.service.redis;

import com.example.be_web_admin.model.jpa.Admin;
import com.example.be_web_admin.repository.jpa.AdminRepository;
import com.example.be_web_admin.security.EnrollmentFenceStore;
import com.example.be_web_admin.security.EnrollmentGenerationStore;
import com.example.be_web_admin.security.private_key.CryptoService;
import com.example.be_web_admin.service.redis.impl.EnrollSessionServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/** Mock tests only; this does not establish MariaDB schema compatibility. */
class EnrollSessionPreflightTest {
    @Test
    void missingFenceDoesNotGenerateOrExposeSecretOrCreateRedisGeneration() {
        var redis = mock(EnrollmentGenerationStore.class);
        var fences = mock(EnrollmentFenceStore.class);
        var admins = mock(AdminRepository.class);
        var crypto = mock(CryptoService.class);
        when(admins.findAdminByAdminId(7L)).thenReturn(new Admin());
        doThrow(new DataAccessResourceFailureException("not available")).when(fences).requireAvailable();
        var service = new EnrollSessionServiceImpl(redis, fences, admins, crypto);
        assertThrows(DataAccessResourceFailureException.class, () -> service.startEnrollment(7L, 30));
        verifyNoInteractions(redis, crypto);
    }
}
