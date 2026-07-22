package com.losvernos.anzenfs.rbac.auth;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginRateLimiter {

  private final int maxAttempts;
  private final Duration window;
  private final ConcurrentHashMap<String, Deque<Instant>> failedAttemptsByUsername = new ConcurrentHashMap<>();

  public LoginRateLimiter() {
    this(5, Duration.ofMinutes(1));
  }

  LoginRateLimiter(int maxAttempts, Duration window) {
    this.maxAttempts = maxAttempts;
    this.window = window;
  }

  public boolean isBlocked(String username) {
    Deque<Instant> attempts = failedAttemptsByUsername.get(key(username));
    if (attempts == null) {
      return false;
    }
    synchronized (attempts) {
      pruneExpired(attempts);
      return attempts.size() >= maxAttempts;
    }
  }

  public void recordFailedAttempt(String username) {
    Deque<Instant> attempts = failedAttemptsByUsername.computeIfAbsent(key(username), k -> new ArrayDeque<>());
    synchronized (attempts) {
      pruneExpired(attempts);
      attempts.addLast(Instant.now());
    }
  }

  public void recordSuccessfulLogin(String username) {
    failedAttemptsByUsername.remove(key(username));
  }

  private void pruneExpired(Deque<Instant> attempts) {
    Instant cutoff = Instant.now().minus(window);
    while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) {
      attempts.removeFirst();
    }
  }

  private String key(String username) {
    return username == null ? "" : username.toLowerCase();
  }
}
