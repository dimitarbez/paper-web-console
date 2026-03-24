package dev.dimo.paperwebconsole.auth;

public record AuthResult(boolean success, int statusCode, String message, SessionInfo session) {
}
