package com.example.be_web_admin.service.redis.impl;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Unit tests only. A live HTTP/SSE and Redis integration test is still required. */
class TwoFaEventSessionTest {
    @Test
    void revokedStreamIsRemovedBeforePublishingWhileOtherSessionRemains() {
        var service = new TwoFaEventServiceImpl();
        var firstAllowed = new AtomicBoolean(true);
        var firstChecks = new AtomicInteger();
        var secondChecks = new AtomicInteger();
        service.subscribe(7L, () -> { firstChecks.incrementAndGet(); return firstAllowed.get(); });
        service.subscribe(7L, () -> { secondChecks.incrementAndGet(); return true; });
        firstAllowed.set(false);
        service.publish(7L, TwoFaEventServiceImpl.ENABLED);
        service.publish(7L, TwoFaEventServiceImpl.NOT_ENABLED);
        assertEquals(2, firstChecks.get()); // initial ready + revoked check, then removed
        assertEquals(3, secondChecks.get());
    }

    @Test
    void validationOutageClosesStreamOnHeartbeat() {
        var service = new TwoFaEventServiceImpl();
        var checks = new AtomicInteger();
        service.subscribe(7L, () -> {
            if (checks.incrementAndGet() > 1) throw new IllegalStateException("store unavailable");
            return true;
        });
        service.sendHeartbeat();
        service.publish(7L, TwoFaEventServiceImpl.PENDING);
        assertEquals(2, checks.get());
    }
}
