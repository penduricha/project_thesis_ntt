-- PROPOSAL ONLY: review and test on an isolated MariaDB database before use.
-- This is not applied automatically. It creates no database and stores no TOTP secret.
-- Every activation and reconciliation must use the admin row lock before touching this table.
CREATE TABLE ntt_totp_enrollment_fences (
    admin_id BIGINT NOT NULL,
    generation CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    state VARCHAR(12) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    credential_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (admin_id, generation),
    CONSTRAINT fk_totp_enrollment_fence_admin FOREIGN KEY (admin_id) REFERENCES ntt_admins (admin_id)
) ENGINE=InnoDB;

-- Do not prune fences or roll back this table while an old worker, Redis snapshot,
-- replica, backup or queued request can replay a generation. Define and test a
-- retention/restore policy first; dropping the table removes the replay fence.
