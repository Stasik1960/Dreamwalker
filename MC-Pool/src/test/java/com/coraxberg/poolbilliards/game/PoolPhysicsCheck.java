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

        var corner = PoolTableGeometry.POCKETS.get(0);
        double inwardX = 450 - corner.x(), inwardY = 245 - corner.y();
        double inwardLength = Math.hypot(inwardX, inwardY);
        inwardX /= inwardLength;
        inwardY /= inwardLength;
        PoolBall sinking = new PoolBall(1, corner.x() + inwardX * 60, corner.y() + inwardY * 60);
        sinking.vx = -inwardX * 20;
        sinking.vy = -inwardY * 20;
        PoolGameState pocket = emptyGame(sinking);
        boolean pocketSound = false;
        for (int i = 0; i < 12 && !sinking.pocketed; i++) {
            pocket.tickPhysics();
            pocketSound |= pocket.drainPhysicsSounds().stream()
                    .anyMatch(s -> s.type() == PoolGameState.SoundType.POCKET);
        }
        check(sinking.pocketed, "corner pocket should capture a ball");
        check(pocketSound, "pocket should emit a sound");

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
        checkModelPockets();
        System.out.println("Physics checks passed: center, glance, cushion, pocket, symmetry, rack, sound cap, stop");
    }

    private static void checkModelPockets() {
        check(PoolTableGeometry.POCKETS.size() == 6, "model must have six pockets");
        int index = 0;
        for (var pocket : PoolTableGeometry.POCKETS) {
            for (double speed : new double[]{8, 30, 58.5}) {
                checkPocketShot(pocket, speed, 0, "straight pocket " + index);
            }
            // Approach the same opening off-axis, without clipping its jaw.
            checkPocketShot(pocket, 30, PoolGameState.BALL_R * 0.65, "angled pocket " + index);
            checkPocketMiss(pocket, index);
            index++;
        }
        // Capture begins shortly after the centre crosses the visible opening,
        // rather than after the trailing edge of the whole ball fits through.
        var side = PoolTableGeometry.POCKETS.stream().filter(p -> !Double.isNaN(p.innerY()) && p.y() < 0).findFirst().orElseThrow();
        double captureEdge = Math.min(side.innerY(),
                side.y() + side.ry() - PoolGameState.BALL_R * 0.35);
        check(!side.captures(side.x(), captureEdge + 0.01), "do not pocket a centre still supported by the cloth");
        check(side.captures(side.x(), captureEdge - 0.01), "capture when support is lost inside visible edge");
        PoolBall miss = new PoolBall(1, 505, 65);
        miss.vy = -30;
        var game = emptyGame(miss);
        for (int tick = 0; tick < 5; tick++) game.tickPhysics();
        check(!miss.pocketed && miss.vy > 0, "shot beside middle mouth must rebound");
        // Sweep angles across a middle mouth and ensure no ball tunnels out,
        // gets stuck in the rail, or gains energy on the stepped curved jaws.
        for (int offset = -70; offset <= 70; offset += 7) {
            for (int horizontal = -25; horizontal <= 25; horizontal += 25) {
                PoolBall ball = new PoolBall(1, 450 + offset, 70);
                ball.vx = horizontal; ball.vy = -45;
                var sweep = emptyGame(ball);
                double energy = kineticEnergy(sweep);
                for (int tick = 0; tick < 200 && !ball.pocketed && sweep.areBallsMoving(); tick++) {
                    sweep.tickPhysics();
                    double next = kineticEnergy(sweep);
                    check(next <= energy + 1e-6, "jaw must not add energy");
                    energy = next;
                    check(ball.pocketed || (ball.x > -65 && ball.x < 965 && ball.y > -65 && ball.y < 555),
                            "ball escaped model: " + ball.x + ", " + ball.y);
                }
            }
        }
        System.out.println("Model pocket checks passed: 18 straight shots, 6 angled shots, 6 lip misses, support edge, 63 jaw trajectories.");
    }

    private static void checkPocketShot(PoolTableGeometry.Pocket pocket, double speed,
                                        double lateralOffset, String label) {
        double ix = 450 - pocket.x(), iy = 245 - pocket.y();
        double length = Math.hypot(ix, iy);
        ix /= length;
        iy /= length;
        double px = -iy, py = ix;
        PoolBall ball = new PoolBall(1, pocket.x() + ix * 100 + px * lateralOffset,
                pocket.y() + iy * 100 + py * lateralOffset);
        double tx = pocket.x() - ball.x, ty = pocket.y() - ball.y;
        double targetLength = Math.hypot(tx, ty);
        ball.vx = tx / targetLength * speed;
        ball.vy = ty / targetLength * speed;
        var game = emptyGame(ball);
        for (int tick = 0; tick < 40 && !ball.pocketed && game.areBallsMoving(); tick++) game.tickPhysics();
        check(ball.pocketed, label + " must be captured at " + speed + ": " + pocket);
    }

    private static void checkPocketMiss(PoolTableGeometry.Pocket pocket, int index) {
        double ix = 450 - pocket.x(), iy = 245 - pocket.y();
        double length = Math.hypot(ix, iy);
        ix /= length;
        iy /= length;
        double px = -iy, py = ix;
        double missOffset = Math.max(pocket.rx(), pocket.ry()) + PoolGameState.BALL_R * 0.75;
        PoolBall ball = new PoolBall(1, pocket.x() + ix * 100 + px * missOffset,
                pocket.y() + iy * 100 + py * missOffset);
        ball.vx = -ix * 30;
        ball.vy = -iy * 30;
        var game = emptyGame(ball);
        boolean hitCushion = false;
        for (int tick = 0; tick < 30 && game.areBallsMoving(); tick++) {
            game.tickPhysics();
            hitCushion |= game.drainPhysicsSounds().stream()
                    .anyMatch(sound -> sound.type() == PoolGameState.SoundType.CUSHION);
        }
        check(!ball.pocketed, "shot outside lip must not be pocketed: " + index);
        check(hitCushion, "shot outside lip should contact a jaw/rail: " + index);
    }
}
