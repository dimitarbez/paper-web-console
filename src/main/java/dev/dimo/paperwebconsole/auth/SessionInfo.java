package dev.dimo.paperwebconsole.auth;

public record SessionInfo(String token, String ip, long createdAtEpochMillis, long expiresAtEpochMillis) {
}
