package com.example.be_web_admin.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Durable replay fence for a Redis enrollment generation. Every caller MUST first lock
 * the corresponding admin row in the same DB transaction. Fences are never auto-pruned.
 */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class EnrollmentFenceStore {
    public enum State { CANCELLED, COMMITTED }
    public record Fence(State state, Long credentialId) {}
    private final JdbcTemplate jdbc;

    public EnrollmentFenceStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Probe before taking a Redis claim; a missing migration must not wedge enrollment. */
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public void requireAvailable() {
        jdbc.query("SELECT admin_id, generation, state, credential_id FROM ntt_totp_enrollment_fences WHERE 1 = 0",
                rs -> { });
    }

    /** A locking read avoids stale snapshots at MariaDB REPEATABLE READ. */
    public Fence find(Long adminId, String generation) {
        List<Fence> rows = jdbc.query("SELECT state, credential_id FROM ntt_totp_enrollment_fences " +
                "WHERE admin_id = ? AND generation = ? FOR UPDATE", (rs, n) -> {
            State state = State.valueOf(rs.getString("state"));
            long id = rs.getLong("credential_id");
            Long credential = rs.wasNull() ? null : id;
            if ((state == State.COMMITTED && (credential == null || credential <= 0)) ||
                    (state == State.CANCELLED && credential != null)) {
                throw new IllegalStateException("Invalid enrollment fence");
            }
            return new Fence(state, credential);
        }, adminId, generation);
        if (rows.size() > 1) throw new IllegalStateException("Duplicate enrollment fence");
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void cancel(Long adminId, String generation) {
        insert(adminId, generation, State.CANCELLED, null);
    }

    public void committed(Long adminId, String generation, Long credentialId) {
        if (credentialId == null || credentialId <= 0) throw new IllegalStateException("Credential ID required");
        insert(adminId, generation, State.COMMITTED, credentialId);
    }

    private void insert(Long adminId, String generation, State state, Long credentialId) {
        if (adminId == null || adminId <= 0 || !EnrollmentGenerationStore.validGeneration(generation)) {
            throw new IllegalArgumentException("Invalid enrollment fence identity");
        }
        if (jdbc.update("INSERT INTO ntt_totp_enrollment_fences " +
                "(admin_id, generation, state, credential_id, created_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP(6))",
                adminId, generation, state.name(), credentialId) != 1) {
            throw new IllegalStateException("Cannot write enrollment fence");
        }
    }
}
