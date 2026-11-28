package com.example.be_web_admin.service.redis.impl;

import com.example.be_web_admin.enums.AuthEvent;
import com.example.be_web_admin.model.jpa.Admin;
import com.example.be_web_admin.model.jpa.TotpCredential;
import com.example.be_web_admin.security.EnrollmentGenerationStore;
import com.example.be_web_admin.security.EnrollmentFenceStore;
import com.example.be_web_admin.repository.jpa.AdminRepository;
import com.example.be_web_admin.repository.jpa.TotpCredentialRepository;
import com.example.be_web_admin.security.private_key.CryptoService;
import com.example.be_web_admin.service.jpa.impl.AuthAuditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Giai đoạn 2 của cài 2FA (web bấm "Kích hoạt"): đọc khóa chờ ở Redis, ghi MariaDB trong 1 transaction,
 * commit xong mới xóa key Redis và ghi audit. Admin chỉ thực sự bật 2FA sau khi app mobile xác nhận
 * (xem EnrollmentConfirmServiceImpl).
 */
@Slf4j
@Service
public class EnrollmentServiceImpl {

    /** cột secret_cipher là varbinary(64). */
    private static final int MAX_CIPHER_LENGTH = 64;

    private final EnrollmentGenerationStore generationStore;
    private final EnrollmentFenceStore fenceStore;
    private final AdminRepository adminRepository;
    private final TotpCredentialRepository totpCredentialRepository;
    private final AuthAuditService auditService;
    private final EnrollmentConfirmServiceImpl confirmService;
    private final TransactionTemplate transactionTemplate;
    private final TwoFaEventServiceImpl eventService;

    public EnrollmentServiceImpl(EnrollmentGenerationStore generationStore,
                                 EnrollmentFenceStore fenceStore,
                                 AdminRepository adminRepository,
                                 TotpCredentialRepository totpCredentialRepository,
                                 AuthAuditService auditService,
                                 EnrollmentConfirmServiceImpl confirmService,
                                 TransactionTemplate transactionTemplate,
                                 TwoFaEventServiceImpl eventService) {
        this.generationStore = generationStore;
        this.fenceStore = fenceStore;
        this.adminRepository = adminRepository;
        this.totpCredentialRepository = totpCredentialRepository;
        this.auditService = auditService;
        this.confirmService = confirmService;
        this.transactionTemplate = transactionTemplate;
        this.eventService = eventService;
    }

    public record ActivationResult(Long adminId, Long credentialId, String algorithm, int digits,
                                   int periodSeconds, long expiresInSeconds) {
    }

    /** Trạng thái 2FA cho trang thông tin admin. */
    public record TwoFaStatus(boolean totpEnabled, boolean pending, long expiresInSeconds, Integer periodSeconds, Long credentialId) {
    }

    public static class EnrollmentExpiredException extends RuntimeException {
        public EnrollmentExpiredException() {
            super("Enrollment session expired or not found.");
        }
    }

    public static class AdminNotFoundException extends RuntimeException {
        public AdminNotFoundException(Long adminId) {
            super("Admin not found: " + adminId);
        }
    }

    // ------------------------------------------------------------------ kích hoạt

    /**
     * @param periodOverride chu kỳ admin chọn trong modal lúc bấm "Kích hoạt" (15/30/45/60);
     *                       null thì dùng chu kỳ đã lưu cùng khóa ở Redis. Phải khớp chu kỳ app Authenticator dùng.
     */
    public ActivationResult activate(Long adminId, String generation, Integer periodOverride, String ip, String userAgent) {
        requireOwnTransaction();
        if (!EnrollmentGenerationStore.validGeneration(generation)) throw new IllegalArgumentException("Invalid generation");
        if (periodOverride != null && !EnrollSessionServiceImpl.isPeriodAllowed(periodOverride)) {
            throw new IllegalArgumentException("Unsupported period: " + periodOverride);
        }
        fenceStore.requireAvailable();
        EnrollmentGenerationStore.Claim claim = generationStore.claim(adminId, generation, periodOverride);
        if (claim == null) throw new EnrollmentExpiredException();
        EnrollmentGenerationStore.Pending pending = claim.pending();
        CryptoService.Sealed sealed = CryptoService.Sealed.fromBase64(pending.secretCipher(), pending.keyVersion());
        if (sealed.cipher().length > MAX_CIPHER_LENGTH) {
            throw new IllegalStateException("Unexpected secret cipher length");
        }
        // The secret was shown with this period; never substitute another period at activation.
        int period = pending.periodSeconds();
        // Any failure or uncertain commit retains the claim. Reconciliation installs a
        // durable fence before releasing it, including when no DB write happened yet.
        ActivationResult result = transactionTemplate.execute(status -> persist(adminId, generation, sealed, period));
        if (result == null) throw new IllegalStateException("Activation transaction returned no result");
        // Never unlock on a failed/ambiguous commit. Cleanup failure keeps the claim fail-closed.
        generationStore.finish(adminId, generation, claim.token());
        confirmService.resetAttempts(adminId);
        auditService.log(adminId, AuthEvent.TOTP_ENROLL_ACTIVATED, true, ip, userAgent,
                "period=" + period + "s");
        // Tab web khác của cùng admin cũng chuyển sang "Đang chờ xác nhận"
        eventService.publish(adminId, TwoFaEventServiceImpl.PENDING);
        return result;
    }

    private ActivationResult persist(Long adminId, String generation, CryptoService.Sealed sealed, int period) {
        Admin admin = adminRepository.findForTotpUpdate(adminId);
        if (admin == null) {
            throw new AdminNotFoundException(adminId);
        }
        // A reconciler may have fenced this worker while it was paused before acquiring
        // the DB lock. Never write or replace a credential for any terminal generation.
        if (fenceStore.find(adminId, generation) != null) throw new EnrollmentExpiredException();
        // Đã bật 2FA thì không được cài đè: sẽ để lại 2 credential. Muốn cài lại phải tắt 2FA trước.
        if (!Boolean.FALSE.equals(admin.getTotpEnabled()) || totpCredentialRepository
                .findFirstByAdmin_AdminIdAndConfirmedTrueOrderByConfirmedAtDesc(adminId).isPresent()) {
            throw new TotpAlreadyEnabledException();
        }

        Optional<TotpCredential> existing = totpCredentialRepository
                .findFirstByAdmin_AdminIdAndConfirmedFalseOrderByCreatedAtDesc(adminId);
        if (existing.isPresent() && (existing.get().getCreatedAt() == null ||
                existing.get().getCreatedAt().plus(confirmService.getConfirmWindow()).isAfter(Instant.now()))) {
            throw new EnrollmentGenerationStore.BusyException();
        }
        totpCredentialRepository.deletePendingByAdminId(adminId);

        TotpCredential credential = new TotpCredential();
        credential.setAdmin(admin);
        credential.setKdfSalt(sealed.salt());
        credential.setIv(sealed.iv());
        credential.setSecretCipher(sealed.cipher());
        credential.setKeyVersion(sealed.keyVersion()); // đúng phiên bản đã mã hóa, không gán cứng 1
        credential.setAlgorithm("HmacSHA256");
        credential.setDigits((byte) 6);
        credential.setPeriodSeconds((short) period);
        credential.setConfirmed(false);
        credential.setConfirmedAt(null);
        credential = totpCredentialRepository.save(credential);
        // In the SAME database transaction as the credential, never an after-commit callback.
        fenceStore.committed(adminId, generation, credential.getCredentialId());

        // totp_enabled CHƯA bật: chỉ bật khi mobile gửi đúng mã đầu tiên.
        admin.setUpdatedAt(Instant.now());
        adminRepository.save(admin);

        return new ActivationResult(adminId, credential.getCredentialId(), credential.getAlgorithm(),
                credential.getDigits(), period, confirmService.getConfirmWindow().getSeconds());
    }

    public record ReconcileResult(String outcome, boolean claimReleased) {}

    /**
     * Resolve a claimed generation without guessing whether the DB transaction committed.
     * The admin lock serializes this decision against the activation worker. If no commit
     * marker exists, a CANCELLED fence prevents a delayed worker from writing later.
     * A Redis compare-and-delete runs only after this DB transaction returns successfully.
     */
    public ReconcileResult reconcileClaim(Long adminId, String generation, String ip, String userAgent) {
        requireOwnTransaction();
        if (!EnrollmentGenerationStore.validGeneration(generation)) throw new IllegalArgumentException("Invalid generation");
        EnrollmentFenceStore.State state = transactionTemplate.execute(status -> {
            Admin admin = adminRepository.findForTotpUpdate(adminId);
            if (admin == null) throw new AdminNotFoundException(adminId);
            EnrollmentFenceStore.Fence fence = fenceStore.find(adminId, generation);
            if (fence != null) return fence.state();
            fenceStore.cancel(adminId, generation);
            return EnrollmentFenceStore.State.CANCELLED;
        });
        if (state == null) throw new IllegalStateException("Reconciliation transaction returned no result");
        auditService.log(adminId, AuthEvent.TOTP_ENROLL_RECONCILED, true, ip, userAgent, "outcome=" + state.name());
        // A claim may have been created, finished or lost while the DB lock was held.
        // Redis cleanup is always generation-scoped and is only attempted after fencing.
        EnrollmentGenerationStore.ClaimedGeneration claim = generationStore.claimed(adminId, generation);
        boolean released;
        if (claim != null) {
            released = generationStore.finishIfOwned(adminId, generation, claim.token());
        } else {
            try {
                released = generationStore.cancel(adminId, generation);
            } catch (EnrollmentGenerationStore.BusyException race) {
                claim = generationStore.claimed(adminId, generation);
                released = claim != null && generationStore.finishIfOwned(adminId, generation, claim.token());
            }
        }
        return new ReconcileResult(state.name(), released);
    }

    // ------------------------------------------------------------------ hủy phần chờ xác nhận

    /** "Hủy và cài lại": xóa credential chưa xác nhận của lần cài dở. Không chạm 2FA đã bật. */
    public boolean cancelPending(Long adminId, Long credentialId, String ip, String userAgent) {
        if (credentialId == null || credentialId <= 0) throw new IllegalArgumentException("Credential ID required");
        Boolean deleted = transactionTemplate.execute(status -> {
            Admin admin = adminRepository.findForTotpUpdate(adminId);
            if (admin == null) throw new AdminNotFoundException(adminId);
            return totpCredentialRepository.deletePendingGeneration(adminId, credentialId) == 1;
        });
        if (!Boolean.TRUE.equals(deleted)) return false;
        // Do not delete admin-wide Redis state here: it may belong to a later enrollment.
        // Each confirmation counter and Redis enrollment expires independently.
        auditService.log(adminId, AuthEvent.TOTP_ENROLL_CANCELLED, true, ip, userAgent, null);
        eventService.publish(adminId, TwoFaEventServiceImpl.NOT_ENABLED);
        return true;
    }

    // ------------------------------------------------------------------ trạng thái

    public TwoFaStatus getStatus(Long adminId) {
        Admin admin = adminRepository.findAdminByAdminId(adminId);
        if (admin == null) {
            throw new AdminNotFoundException(adminId);
        }
        boolean enabled = Boolean.TRUE.equals(admin.getTotpEnabled());

        Optional<TotpCredential> pending = totpCredentialRepository
                .findFirstByAdmin_AdminIdAndConfirmedFalseOrderByCreatedAtDesc(adminId);
        if (enabled || pending.isEmpty()) {
            return new TwoFaStatus(enabled, false, 0, null, null);
        }
        TotpCredential c = pending.get();
        long left = Duration.between(Instant.now(),
                c.getCreatedAt().plus(confirmService.getConfirmWindow())).getSeconds();
        if (left <= 0) {
            return new TwoFaStatus(false, false, 0, null, null); // đã quá hạn: coi như chưa có
        }
        return new TwoFaStatus(false, true, left, (int) c.getPeriodSeconds(), c.getCredentialId());
    }

    /** Do not release a Redis claim before an enclosing DB transaction actually commits. */
    private void requireOwnTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive() ||
                transactionTemplate.getPropagationBehavior() != TransactionDefinition.PROPAGATION_REQUIRED) {
            throw new IllegalStateException("Enrollment requires its own database transaction");
        }
    }
}
