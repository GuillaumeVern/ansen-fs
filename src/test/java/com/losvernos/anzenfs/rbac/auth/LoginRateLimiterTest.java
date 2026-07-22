package com.losvernos.anzenfs.rbac.auth;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRateLimiterTest {

    @Test
    void allowsAttemptsUnderTheThreshold() {
        LoginRateLimiter limiter = new LoginRateLimiter(3, Duration.ofMinutes(1));

        limiter.recordFailedAttempt("alice");
        limiter.recordFailedAttempt("alice");

        assertThat(limiter.isBlocked("alice")).isFalse();
    }

    @Test
    void blocksOnceTheThresholdIsReached() {
        LoginRateLimiter limiter = new LoginRateLimiter(3, Duration.ofMinutes(1));

        limiter.recordFailedAttempt("alice");
        limiter.recordFailedAttempt("alice");
        limiter.recordFailedAttempt("alice");

        assertThat(limiter.isBlocked("alice")).isTrue();
    }

    @Test
    void tracksAttemptsPerUsernameIndependently() {
        LoginRateLimiter limiter = new LoginRateLimiter(2, Duration.ofMinutes(1));

        limiter.recordFailedAttempt("alice");
        limiter.recordFailedAttempt("alice");
        limiter.recordFailedAttempt("bob");

        assertThat(limiter.isBlocked("alice")).isTrue();
        assertThat(limiter.isBlocked("bob")).isFalse();
    }

    @Test
    void successfulLoginResetsTheCounter() {
        LoginRateLimiter limiter = new LoginRateLimiter(2, Duration.ofMinutes(1));

        limiter.recordFailedAttempt("alice");
        limiter.recordFailedAttempt("alice");
        limiter.recordSuccessfulLogin("alice");

        assertThat(limiter.isBlocked("alice")).isFalse();
    }

    @Test
    void expiredAttemptsAreNotCountedTowardsTheThreshold() throws InterruptedException {
        LoginRateLimiter limiter = new LoginRateLimiter(2, Duration.ofMillis(50));

        limiter.recordFailedAttempt("alice");
        limiter.recordFailedAttempt("alice");
        assertThat(limiter.isBlocked("alice")).isTrue();

        Thread.sleep(100);

        assertThat(limiter.isBlocked("alice")).isFalse();
    }

    @Test
    void usernameMatchingIsCaseInsensitive() {
        LoginRateLimiter limiter = new LoginRateLimiter(2, Duration.ofMinutes(1));

        limiter.recordFailedAttempt("Alice");
        limiter.recordFailedAttempt("ALICE");

        assertThat(limiter.isBlocked("alice")).isTrue();
    }
}
