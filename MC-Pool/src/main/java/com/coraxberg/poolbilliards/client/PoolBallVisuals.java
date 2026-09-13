package com.coraxberg.poolbilliards.client;

import com.coraxberg.poolbilliards.game.PoolBall;

import java.util.HashMap;
import java.util.Map;

/** Smooths server snapshots for both the in-world balls and the game screen. */
public final class PoolBallVisuals {
    public record Position(double x, double y) {}

    private static final class Track {
        PoolBall snapshot;
        long snapshotNanos;
        double x;
        double y;

        Track(PoolBall ball, long now) {
            snapshot = ball;
            snapshotNanos = now;
            x = ball.x;
            y = ball.y;
        }
    }

    private final Map<Integer, Track> tracks = new HashMap<>();
    private long frameNanos;
    private double frameSeconds;

    public void beginFrame() {
        long now = System.nanoTime();
        frameSeconds = frameNanos == 0 ? 0 : Math.min(0.1, Math.max(0, (now - frameNanos) / 1_000_000_000.0));
        frameNanos = now;
    }

    public Position sample(PoolBall ball) {
        Track track = tracks.computeIfAbsent(ball.id, id -> new Track(ball, frameNanos));
        if (track.snapshot != ball) {
            track.snapshot = ball;
            track.snapshotNanos = frameNanos;
        }
        // A packet is sent once per server tick. Predict no further than one
        // tick and blend corrections rather than jumping to each new snapshot.
        double ticksSincePacket = Math.min(1.0, Math.max(0, (frameNanos - track.snapshotNanos) / 50_000_000.0));
        double targetX = ball.x + ball.vx * ticksSincePacket;
        double targetY = ball.y + ball.vy * ticksSincePacket;
        if (Math.hypot(targetX - track.x, targetY - track.y) > 120) {
            track.x = targetX;
            track.y = targetY;
        } else {
            double blend = 1.0 - Math.exp(-30.0 * frameSeconds);
            track.x += (targetX - track.x) * blend;
            track.y += (targetY - track.y) * blend;
        }
        return new Position(track.x, track.y);
    }
}
