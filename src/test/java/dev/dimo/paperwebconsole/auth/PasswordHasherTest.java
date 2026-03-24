package dev.dimo.paperwebconsole.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {
    @Test
    void verifiesTheOriginalPassword() {
        PasswordHasher hasher = new PasswordHasher();
        PasswordHash hash = hasher.hash("correct horse battery staple");
        StoredAuthData storedAuthData = new StoredAuthData(hash.hashBase64(), hash.saltBase64(), hash.iterations(), null, 0L);

        assertTrue(hasher.verify("correct horse battery staple", storedAuthData));
        assertFalse(hasher.verify("wrong password", storedAuthData));
    }
}
