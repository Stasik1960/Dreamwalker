package com.coraxberg.rpchat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Main-thread token bucket for unsigned custom chat submissions. */
final class RpChatSubmissionLimiter {
    private final double burst;
    private final double refillPerSecond;
    private final Map<UUID, Rate> rates = new HashMap<>();

    RpChatSubmissionLimiter(double burst, double refillPerSecond) {
        this.burst = burst;
        this.refillPerSecond = refillPerSecond;
    }

    boolean tryAcquire(UUID playerId, long nowNanos) {
        Rate rate = rates.computeIfAbsent(playerId, ignored -> new Rate(burst, nowNanos));
        double elapsedSeconds = Math.max(0L, nowNanos - rate.lastRefillNanos) / 1_000_000_000.0;
        rate.tokens = Math.min(burst, rate.tokens + elapsedSeconds * refillPerSecond);
        rate.lastRefillNanos = nowNanos;
        if (rate.tokens < 1.0) return false;
        rate.tokens -= 1.0;
        return true;
    }

    void remove(UUID playerId) {
        rates.remove(playerId);
    }

    int trackedPlayers() {
        return rates.size();
    }

    private static final class Rate {
        private double tokens;
        private long lastRefillNanos;

        private Rate(double tokens, long lastRefillNanos) {
            this.tokens = tokens;
            this.lastRefillNanos = lastRefillNanos;
        }
    }
}
