package com.coraxberg.poolbilliards.game;

/** Run with {@code gradle physicsCheck}. No client or test framework is needed. */
public final class PoolPhysicsCheck {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static PoolGameState emptyGame(PoolBall... balls) {
        PoolGameState game = new PoolGameState();
        game.balls.clear();
        for (PoolBall ball : balls) game.balls.add(ball);
        return game;
    }

    private static double kineticEnergy(PoolGameState game) {
        double energy = 0;
        for (PoolBall ball : game.balls) {
            if (!ball.pocketed) energy += ball.vx * ball.vx + ball.vy * ball.vy;
        }
        return energy;
    }

    public static void main(String[] args) {
        PoolBall cue = new PoolBall(0, 100, 245);
        PoolBall target = new PoolBall(1, 145, 245);
        cue.vx = 30;
        PoolGameState center = emptyGame(cue, target);
        for (int i = 0; i < 4; i++) center.tickPhysics();
        check(target.vx > 20, "central hit should pass most momentum to target");
        check(Math.abs(cue.vy) < 0.01 && Math.abs(target.vy) < 0.01, "central hit should stay straight");
        check(target.vx > cue.vx * 4, "cue should slow after central hit");

        PoolBall glancingCue = new PoolBall(0, 100, 245);
        PoolBall glancingTarget = new PoolBall(1, 145, 260);
        glancingCue.vx = 30;
        PoolGameState glance = emptyGame(glancingCue, glancingTarget);
        for (int i = 0; i < 4; i++) glance.tickPhysics();
        check(glancingTarget.vx > 8 && glancingTarget.vy > 2, "glancing target should deflect");
        check(glancingCue.vy < -2, "glancing cue should deflect oppositely");
        check(Math.abs(glancingCue.vy + glancingTarget.vy) < 0.2, "lateral momentum should balance");

        PoolBall rebound = new PoolBall(1, PoolGameState.TABLE_W - 37, 245);
        rebound.vx = 36;
        rebound.vy = 8;
        PoolGameState rail = emptyGame(rebound);
        rail.tickPhysics();
        check(rebound.vx < -20, "rail should reverse normal velocity");
        check(rebound.vy > 5, "rail should retain grazing motion");
        check(rail.drainPhysicsSounds().stream().anyMatch(s -> s.type() == PoolGameState.SoundType.CUSHION),
                "rail should emit a sound");

        PoolBall sinking = new PoolBall(1, 40, 40);
        sinking.vx = -8;
        sinking.vy = -8;
        PoolGameState pocket = emptyGame(sinking);
        for (int i = 0; i < 3 && !sinking.pocketed; i++) pocket.tickPhysics();
        check(sinking.pocketed, "corner pocket should capture a ball");
        check(pocket.drainPhysicsSounds().stream().anyMatch(s -> s.type() == PoolGameState.SoundType.POCKET),
                "pocket should emit a sound");

        PoolBall symmetricCue = new PoolBall(0, 100, 245);
        PoolBall left = new PoolBall(1, 145, 231);
        PoolBall right = new PoolBall(2, 145, 259);
        symmetricCue.vx = 30;
        PoolGameState symmetric = emptyGame(symmetricCue, left, right);
        for (int i = 0; i < 4; i++) symmetric.tickPhysics();
        check(Math.abs(left.vx - right.vx) < 2.0, "symmetric targets should receive similar forward speed");
        check(Math.abs(left.vy + right.vy) < 2.5,
                "symmetric targets should spread in opposite directions: " + left.vx + ", " + left.vy + " vs " + right.vx + ", " + right.vy);

        PoolGameState breakGame = new PoolGameState();
        breakGame.getBall(0).vx = 58.5;
        double previousEnergy = kineticEnergy(breakGame);
        long movingObjects = 0;
        for (int i = 0; i < 16; i++) {
            breakGame.tickPhysics();
            double energy = kineticEnergy(breakGame);
            check(energy <= previousEnergy + 1e-6, "collisions must not add energy at tick " + i);
            previousEnergy = energy;
            check(breakGame.drainPhysicsSounds().size() <= PoolGameState.SoundType.values().length,
                    "a dense rack should emit at most one sound of each type per tick");
        }
        movingObjects = breakGame.balls.stream()
                .filter(b -> b.id != 0 && !b.pocketed && Math.hypot(b.vx, b.vy) > 0.5)
                .count();
        check(movingObjects >= 7, "break should distribute momentum through the rack");

        PoolBall stopping = new PoolBall(1, 450, 245);
        stopping.vx = 2;
        PoolGameState rolling = emptyGame(stopping);
        int ticks = 0;
        while (rolling.areBallsMoving() && ticks++ < 200) rolling.tickPhysics();
        check(!rolling.areBallsMoving(), "slow ball should stop in bounded time");
        System.out.println("Physics checks passed: center, glance, cushion, pocket, symmetry, rack, sound cap, stop");
    }
}
