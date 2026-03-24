package dev.dimo.paperwebconsole.auth;

import java.time.Instant;

public record StoredAuthData(
    String passwordHash,
    String saltBase64,
    int iterations,
    String setupToken,
    long setupTokenExpiresAtEpochMillis
) {
    public static StoredAuthData empty() {
        return new StoredAuthData(null, null, 0, null, 0L);
    }

    public boolean isConfigured() {
        return passwordHash != null && saltBase64 != null && iterations > 0;
    }

    public boolean hasActiveSetupToken(Instant now) {
        return setupToken != null && setupTokenExpiresAtEpochMillis > now.toEpochMilli();
    }

    public StoredAuthData withPassword(PasswordHash passwordHash) {
        return new StoredAuthData(passwordHash.hashBase64(), passwordHash.saltBase64(), passwordHash.iterations(), null, 0L);
    }

    public StoredAuthData withSetupToken(String token, Instant expiresAt) {
        return new StoredAuthData(passwordHash, saltBase64, iterations, token, expiresAt.toEpochMilli());
    }
}
