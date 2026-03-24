package dev.dimo.paperwebconsole.auth;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordHasher {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA512";
    private static final int KEY_LENGTH_BITS = 256;
    private static final int DEFAULT_ITERATIONS = 210_000;
    private static final int SALT_SIZE_BYTES = 16;

    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordHash hash(String password) {
        byte[] salt = new byte[SALT_SIZE_BYTES];
        secureRandom.nextBytes(salt);
        return hash(password, salt, DEFAULT_ITERATIONS);
    }

    public boolean verify(String password, StoredAuthData storedAuthData) {
        if (password == null || storedAuthData == null || !storedAuthData.isConfigured()) {
            return false;
        }

        try {
            byte[] expectedHash = Base64.getDecoder().decode(storedAuthData.passwordHash());
            byte[] salt = Base64.getDecoder().decode(storedAuthData.saltBase64());
            PasswordHash candidate = hash(password, salt, storedAuthData.iterations());
            byte[] actualHash = Base64.getDecoder().decode(candidate.hashBase64());
            return MessageDigest.isEqual(expectedHash, actualHash);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    PasswordHash hash(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return new PasswordHash(
                Base64.getEncoder().encodeToString(hash),
                Base64.getEncoder().encodeToString(salt),
                iterations
            );
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("PBKDF2 is not available in this runtime.", exception);
        } finally {
            spec.clearPassword();
        }
    }
}
