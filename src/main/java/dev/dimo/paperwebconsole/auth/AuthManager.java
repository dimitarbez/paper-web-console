package dev.dimo.paperwebconsole.auth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.locks.ReentrantLock;

public final class AuthManager {
    private static final int MIN_PASSWORD_LENGTH = 12;

    private final Path authFile;
    private final PasswordHasher passwordHasher;
    private final SessionRegistry sessionRegistry;
    private final LoginRateLimiter loginRateLimiter;
    private final Duration setupTokenTtl;
    private final Clock clock;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private final ReentrantLock lock = new ReentrantLock();
    private final SecureRandom secureRandom = new SecureRandom();

    private StoredAuthData storedAuthData;

    private AuthManager(
        Path authFile,
        PasswordHasher passwordHasher,
        SessionRegistry sessionRegistry,
        LoginRateLimiter loginRateLimiter,
        Duration setupTokenTtl,
        Clock clock,
        StoredAuthData storedAuthData
    ) {
        this.authFile = authFile;
        this.passwordHasher = passwordHasher;
        this.sessionRegistry = sessionRegistry;
        this.loginRateLimiter = loginRateLimiter;
        this.setupTokenTtl = setupTokenTtl;
        this.clock = clock;
        this.storedAuthData = storedAuthData;
    }

    public static AuthManager load(
        Path authFile,
        PasswordHasher passwordHasher,
        Duration sessionLifetime,
        Duration setupTokenTtl,
        Duration loginRateLimitWindow,
        int loginRateLimitMaxAttempts,
        Clock clock
    ) throws IOException {
        Files.createDirectories(authFile.getParent());

        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        StoredAuthData stored = StoredAuthData.empty();
        if (Files.exists(authFile)) {
            try (Reader reader = Files.newBufferedReader(authFile, StandardCharsets.UTF_8)) {
                StoredAuthData loaded = gson.fromJson(reader, StoredAuthData.class);
                if (loaded != null) {
                    stored = loaded;
                }
            }
        }

        AuthManager authManager = new AuthManager(
            authFile,
            passwordHasher,
            new SessionRegistry(sessionLifetime),
            new LoginRateLimiter(loginRateLimitWindow, loginRateLimitMaxAttempts),
            setupTokenTtl,
            clock,
            stored
        );
        authManager.ensureSetupToken();
        return authManager;
    }

    public boolean isConfigured() {
        lock.lock();
        try {
            return storedAuthData.isConfigured();
        } finally {
            lock.unlock();
        }
    }

    public String currentSetupToken() {
        lock.lock();
        try {
            return storedAuthData.setupToken();
        } finally {
            lock.unlock();
        }
    }

    public int activeSessions() {
        return sessionRegistry.count(Instant.now(clock));
    }

    public AuthResult login(String ip, String password) {
        Instant now = Instant.now(clock);
        if (loginRateLimiter.isBlocked(ip, now)) {
            return new AuthResult(false, 429, "Too many failed login attempts. Try again later.", null);
        }

        lock.lock();
        try {
            if (!storedAuthData.isConfigured()) {
                return new AuthResult(false, 409, "Initial setup has not been completed yet.", null);
            }

            if (!passwordHasher.verify(password, storedAuthData)) {
                loginRateLimiter.recordFailure(ip, now);
                if (loginRateLimiter.isBlocked(ip, now)) {
                    return new AuthResult(false, 429, "Too many failed login attempts. Try again later.", null);
                }
                return new AuthResult(false, 401, "Invalid password.", null);
            }

            loginRateLimiter.recordSuccess(ip);
            SessionInfo session = sessionRegistry.create(ip, now);
            return new AuthResult(true, 200, "Login successful.", session);
        } finally {
            lock.unlock();
        }
    }

    public AuthResult completeSetup(String token, String password, String ip) throws IOException {
        Instant now = Instant.now(clock);
        lock.lock();
        try {
            if (storedAuthData.isConfigured()) {
                return new AuthResult(false, 409, "Initial setup has already been completed.", null);
            }

            if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
                return new AuthResult(false, 400, "Password must be at least " + MIN_PASSWORD_LENGTH + " characters long.", null);
            }

            if (!storedAuthData.hasActiveSetupToken(now) || !tokensEqual(token, storedAuthData.setupToken())) {
                return new AuthResult(false, 403, "The setup token is invalid or has expired.", null);
            }

            storedAuthData = storedAuthData.withPassword(passwordHasher.hash(password));
            save();

            SessionInfo session = sessionRegistry.create(ip, now);
            return new AuthResult(true, 200, "Setup complete.", session);
        } finally {
            lock.unlock();
        }
    }

    public SessionInfo authenticateSession(String token, String ip) {
        return sessionRegistry.getValid(token, ip, Instant.now(clock));
    }

    public void logout(String token) {
        sessionRegistry.invalidate(token);
    }

    public void resetAuth() throws IOException {
        lock.lock();
        try {
            storedAuthData = StoredAuthData.empty();
            sessionRegistry.clear();
            loginRateLimiter.clear();
            ensureSetupToken();
        } finally {
            lock.unlock();
        }
    }

    private void ensureSetupToken() throws IOException {
        Instant now = Instant.now(clock);
        if (storedAuthData.isConfigured() || storedAuthData.hasActiveSetupToken(now)) {
            return;
        }

        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        storedAuthData = storedAuthData.withSetupToken(token, now.plus(setupTokenTtl));
        save();
    }

    private void save() throws IOException {
        try (Writer writer = Files.newBufferedWriter(authFile, StandardCharsets.UTF_8)) {
            gson.toJson(storedAuthData, writer);
        }
    }

    private boolean tokensEqual(String provided, String expected) {
        if (provided == null || expected == null) {
            return false;
        }
        return MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }
}
