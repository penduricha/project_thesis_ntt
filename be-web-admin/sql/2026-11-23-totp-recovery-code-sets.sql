-- Staged recovery-code storage; REVIEW AND TEST ON A SEPARATE DATABASE FIRST.
-- This migration is not run automatically by the application. No database is created here.
-- Back up and validate existing admin/credential column types before applying.
CREATE TABLE IF NOT EXISTS ntt_totp_recovery_sets (
    set_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    admin_id BIGINT NOT NULL,
    credential_id BIGINT NOT NULL,
    state VARCHAR(12) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    PRIMARY KEY (set_id),
    KEY idx_recovery_sets_admin (admin_id, state, credential_id),
    CONSTRAINT fk_recovery_sets_admin FOREIGN KEY (admin_id) REFERENCES ntt_admins (admin_id),
    CONSTRAINT fk_recovery_sets_credential FOREIGN KEY (credential_id)
        REFERENCES ntt_totp_credentials (credential_id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS ntt_totp_recovery_codes (
    set_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    code_hash BINARY(32) NOT NULL,
    used_at DATETIME(6) NULL,
    PRIMARY KEY (set_id, code_hash),
    CONSTRAINT fk_recovery_codes_set FOREIGN KEY (set_id)
        REFERENCES ntt_totp_recovery_sets (set_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Rollback requires explicit incident review: dropping these tables destroys all recovery codes.
