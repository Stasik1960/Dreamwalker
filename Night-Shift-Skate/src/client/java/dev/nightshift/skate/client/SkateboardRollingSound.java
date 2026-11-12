package dev.nightshift.skate.client;

import dev.nightshift.skate.NightShiftSkate;
import dev.nightshift.skate.SkateboardEntity;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.MathHelper;

/** Smoothly follows a skateboard and fades the real wheel recording in and out. */
final class SkateboardRollingSound extends MovingSoundInstance {
    private static final float START_SPEED = 0.045f;
    private final SkateboardEntity board;
    private float envelope;

    SkateboardRollingSound(SkateboardEntity board) {
        super(NightShiftSkate.SKATE_ROLL, SoundCategory.PLAYERS, SoundInstance.createRandom());
        this.board = board;
        repeat = true;
        repeatDelay = 0;
        attenuationType = SoundInstance.AttenuationType.LINEAR;
        // Vanilla discards new sources with zero volume before ticking them.
        volume = 0.01f;
        pitch = pitchFor(board.getRideSpeed());
        followBoard();
    }

    static boolean shouldStart(SkateboardEntity board) {
        return isRolling(board) && board.getRideSpeed() >= START_SPEED;
    }

    @Override
    public void tick() {
        if (board.isRemoved()) {
            finish();
            return;
        }
        followBoard();
        boolean rolling = isRolling(board) && board.getRideSpeed() >= START_SPEED;
        envelope = MathHelper.clamp(envelope + (rolling ? 0.16f : -0.23f), 0.0f, 1.0f);

        float speed = board.getRideSpeed();
        float speedAmount = MathHelper.clamp(speed / (float) SkateboardEntity.MAX_SPEED, 0.0f, 1.25f);
        float targetVolume = 0.14f + 0.60f * MathHelper.sqrt(speedAmount);
        volume = envelope * targetVolume;
        pitch = MathHelper.lerp(0.13f, pitch, pitchFor(speed));
        if (!rolling && envelope == 0.0f) {
            setDone();
        }
    }

    void finish() {
        setDone();
    }

    private void followBoard() {
        x = board.getX();
        y = board.getY() + 0.12;
        z = board.getZ();
    }

    private static boolean isRolling(SkateboardEntity board) {
        return !board.isGrinding() && !board.isTouchingWater() && board.isOnGround();
    }

    private static float pitchFor(float speed) {
        float amount = MathHelper.clamp(speed / (float) SkateboardEntity.MAX_SPEED, 0.0f, 1.25f);
        return 0.76f + 0.36f * MathHelper.sqrt(amount);
    }
}
