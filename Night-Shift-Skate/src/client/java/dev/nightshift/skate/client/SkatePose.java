package dev.nightshift.skate.client;

import dev.nightshift.skate.BoardSetup;
import dev.nightshift.skate.BoardStyle;
import dev.nightshift.skate.SkateboardEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

public final class SkatePose {
    private SkatePose() {}

    public static float trickProgress(SkateboardEntity board, float tickDelta) {
        return progress(board, board.getTrickStartAge(), tickDelta, board.getTrickDuration());
    }

    public static float ollieProgress(SkateboardEntity board, float tickDelta) {
        if (!board.isAirborne() || board.getOllieStartAge() == Integer.MIN_VALUE) {
            return 1.0f;
        }
        return progress(board, board.getOllieStartAge(), tickDelta, 9);
    }

    private static float progress(SkateboardEntity board, int start, float tickDelta, int duration) {
        int elapsed = (int) board.getWorld().getTime() - start;
        return MathHelper.clamp((elapsed + tickDelta) / duration, 0.0f, 1.0f);
    }

    public static float grabAmount(float progress) {
        return smoothStep(progress / 0.34f) * smoothStep((1.0f - progress) / 0.30f);
    }

    public static float smoothStep(float value) {
        float t = MathHelper.clamp(value, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    public static float travelYaw(SkateboardEntity board, float tickDelta) {
        return MathHelper.lerpAngleDegrees(tickDelta, board.prevYaw, board.getYaw());
    }

    public static float stanceYaw(SkateboardEntity board) {
        return board.isGrinding() ? -63.0f : 27.0f;
    }

    public static float manualAxle(BoardSetup setup) {
        BoardStyle preset = setup.matchingPreset();
        return preset == BoardStyle.IRON_VESPERS || preset == BoardStyle.GRAVE_PROCESSION
                ? 0.32f : (float) BoardSetup.WHEELBASE_HALF;
    }

    public static void wallTilt(MatrixStack matrices, float lean, float deckHeight) {
        if (lean == 0.0f) return;
        float roll = lean * 48.0f;
        float radians = roll * MathHelper.RADIANS_PER_DEGREE;
        matrices.translate(lean * 0.28f - MathHelper.sin(radians) * deckHeight,
                (MathHelper.cos(radians) - 1.0f) * deckHeight, 0);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll));
    }
}
