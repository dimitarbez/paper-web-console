package dev.dimo.paperwebconsole.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SessionRegistryTest {
    @Test
    void expiresSessionsAndBindsThemToIpAddress() {
        SessionRegistry registry = new SessionRegistry(Duration.ofHours(1));
        Instant now = Instant.parse("2026-03-24T12:00:00Z");
        SessionInfo session = registry.create("127.0.0.1", now);

        assertNotNull(registry.getValid(session.token(), "127.0.0.1", now.plusSeconds(1)));
        assertNull(registry.getValid(session.token(), "127.0.0.2", now.plusSeconds(1)));
        assertEquals(0, registry.count(now.plus(Duration.ofHours(2))));
    }
}
