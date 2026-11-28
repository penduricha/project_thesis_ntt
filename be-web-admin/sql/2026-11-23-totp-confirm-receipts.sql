-- PROPOSAL ONLY. Review and test on an isolated MariaDB instance; do not apply to production yet.
-- A receipt is not a recovery code and cannot be used without the matching mobile session.
-- Raw request IDs and six-digit OTPs must not be stored or logged.
CREATE TABLE ntt_totp_confirm_receipts (
    admin_id BIGINT NOT NULL,
    credential_id BIGINT NOT NULL,
    session_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_hash BINARY(32) NOT NULL,
    payload_mac BINARY(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    PRIMARY KEY (admin_id, request_hash),
    UNIQUE KEY uq_totp_receipt_credential (credential_id),
    CONSTRAINT fk_totp_receipt_admin FOREIGN KEY (admin_id) REFERENCES ntt_admins (admin_id),
    CONSTRAINT fk_totp_receipt_credential FOREIGN KEY (credential_id)
        REFERENCES ntt_totp_credentials (credential_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Retention/backup/restore and client upgrade policies must be reviewed before deployment.
-- Delete on credential disable prevents an old receipt from authorizing a restored local key.
