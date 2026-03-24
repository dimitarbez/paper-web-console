package dev.dimo.paperwebconsole.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LoginRateLimiterTest {
    @Test
    void blocksAfterTooManyRecentFailures() {
        LoginRateLimiter limiter = new LoginRateLimiter(Duration.ofMinutes(10), 3);
        Instant now = Instant.parse("2026-03-24T12:00:00Z");

        limiter.recordFailure("127.0.0.1", now);
        limiter.recordFailure("127.0.0.1", now.plusSeconds(5));
        assertFalse(limiter.isBlocked("127.0.0.1", now.plusSeconds(6)));

        limiter.recordFailure("127.0.0.1", now.plusSeconds(10));
        assertTrue(limiter.isBlocked("127.0.0.1", now.plusSeconds(10)));
        assertFalse(limiter.isBlocked("127.0.0.1", now.plus(Duration.ofMinutes(11))));
    }
}
