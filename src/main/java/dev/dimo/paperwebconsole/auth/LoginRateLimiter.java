package dev.dimo.paperwebconsole.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class LoginRateLimiter {
    private final Duration window;
    private final int maxAttempts;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public LoginRateLimiter(Duration window, int maxAttempts) {
        this.window = window;
        this.maxAttempts = maxAttempts;
    }

    public boolean isBlocked(String key, Instant now) {
        Deque<Instant> deque = failures.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        prune(deque, now);
        if (deque.isEmpty()) {
            failures.remove(key);
            return false;
        }
        return deque.size() >= maxAttempts;
    }

    public void recordFailure(String key, Instant now) {
        Deque<Instant> deque = failures.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        prune(deque, now);
        deque.addLast(now);
    }

    public void recordSuccess(String key) {
        failures.remove(key);
    }

    public void clear() {
        failures.clear();
    }

    private void prune(Deque<Instant> deque, Instant now) {
        Instant threshold = now.minus(window);
        while (!deque.isEmpty() && deque.peekFirst().isBefore(threshold)) {
            deque.removeFirst();
        }
    }
}
