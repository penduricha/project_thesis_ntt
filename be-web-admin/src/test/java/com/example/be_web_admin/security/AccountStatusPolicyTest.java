package com.example.be_web_admin.security;

import com.example.be_web_admin.enums.AccountStatus;
import com.example.be_web_admin.model.jpa.Admin;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AccountStatusPolicyTest {
    @Test
    void blocksLockedAndDisabledButKeepsLegacyAccountsUsable() {
        Admin admin = new Admin();
        for (AccountStatus status : new AccountStatus[]{AccountStatus.ACTIVE, AccountStatus.PENDING, null}) {
            admin.setAccountStatus(status);
            assertTrue(admin.isLoginAllowed());
        }
        for (AccountStatus status : new AccountStatus[]{AccountStatus.LOCKED, AccountStatus.DISABLED}) {
            admin.setAccountStatus(status);
            assertFalse(admin.isLoginAllowed());
        }
    }
}
