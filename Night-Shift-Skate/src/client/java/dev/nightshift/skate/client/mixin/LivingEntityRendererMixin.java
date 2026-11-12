package dev.nightshift.skate.client.mixin;

import dev.nightshift.skate.SkateboardEntity;
import dev.nightshift.skate.client.SkatePose;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererMixin {
    @Inject(method = "setupTransforms", at = @At("TAIL"))
    private void nightShift$alignRider(LivingEntity rider, MatrixStack matrices, float animationProgress,
                                       float bodyYaw, float tickDelta, CallbackInfo ci) {
        if (!(rider.getVehicle() instanceof SkateboardEntity board) || !rider.isAlive()) {
            return;
        }
        float deckHeight = (float) board.getSetup().riderHeight();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(bodyYaw - SkatePose.travelYaw(board, tickDelta)));
        if (!board.isGrinding()) {
            SkatePose.wallTilt(matrices, board.getWallRideLean(), deckHeight);
            float manual = Math.abs(board.getManualPitch()) * MathHelper.RADIANS_PER_DEGREE;
            matrices.translate(0, MathHelper.sin(manual) * SkatePose.manualAxle(board.getSetup()), 0);
        }
        if (board.getTrick().isGrab()) {
            matrices.translate(0, -0.10f * SkatePose.grabAmount(SkatePose.trickProgress(board, tickDelta)), 0);
        }
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-SkatePose.stanceYaw(board)));
    }
}
