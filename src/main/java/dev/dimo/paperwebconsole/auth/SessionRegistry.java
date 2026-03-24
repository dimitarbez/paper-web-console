package dev.dimo.paperwebconsole.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionRegistry {
    private final Duration sessionLifetime;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();

    public SessionRegistry(Duration sessionLifetime) {
        this.sessionLifetime = sessionLifetime;
    }

    public SessionInfo create(String ip, Instant now) {
        pruneExpired(now);
        String token = generateToken();
        SessionInfo session = new SessionInfo(token, ip, now.toEpochMilli(), now.plus(sessionLifetime).toEpochMilli());
        sessions.put(token, session);
        return session;
    }

    public SessionInfo getValid(String token, String ip, Instant now) {
        if (token == null) {
            return null;
        }

        SessionInfo session = sessions.get(token);
        if (session == null) {
            return null;
        }

        if (session.expiresAtEpochMillis() <= now.toEpochMilli() || !Objects.equals(session.ip(), ip)) {
            sessions.remove(token);
            return null;
        }

        return session;
    }

    public void invalidate(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    public void clear() {
        sessions.clear();
    }

    public int count(Instant now) {
        pruneExpired(now);
        return sessions.size();
    }

    private void pruneExpired(Instant now) {
        long currentTime = now.toEpochMilli();
        sessions.entrySet().removeIf(entry -> entry.getValue().expiresAtEpochMillis() <= currentTime);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
