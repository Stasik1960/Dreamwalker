package dev.nightshift.skate.client.mixin;

import dev.nightshift.skate.SkateTrick;
import dev.nightshift.skate.SkateboardEntity;
import dev.nightshift.skate.client.SkatePose;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BipedEntityModel.class)
abstract class BipedEntityModelMixin {
    @Shadow @Final public ModelPart leftLeg;
    @Shadow @Final public ModelPart rightLeg;
    @Shadow @Final public ModelPart leftArm;
    @Shadow @Final public ModelPart rightArm;
    @Shadow @Final public ModelPart body;
    @Shadow @Final public ModelPart head;
    @Shadow @Final public ModelPart hat;
    @Unique private float nightShift$originalBodyRoll;
    @Unique private boolean nightShift$hasBoardPose;

    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("HEAD"))
    private void nightShift$standOnBoard(LivingEntity rider, float limbAngle, float limbDistance, float time,
                                        float headYaw, float headPitch, CallbackInfo ci) {
        if (nightShift$hasBoardPose) {
            body.roll = nightShift$originalBodyRoll;
            nightShift$hasBoardPose = false;
        }
        if (rider.getVehicle() instanceof SkateboardEntity) {
            ((BipedEntityModel<?>) (Object) this).riding = false;
        }
    }

    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void nightShift$skatingPose(LivingEntity rider, float limbAngle, float limbDistance, float time,
                                      float headYaw, float headPitch, CallbackInfo ci) {
        if (!(rider.getVehicle() instanceof SkateboardEntity board)) {
            return;
        }
        float speed = MathHelper.clamp(board.getRideSpeed() / (float) board.getSetup().topSpeed(), 0, 1);
        float steering = board.getSteeringLean();
        float grind = board.isGrinding() ? board.getGrindLean() : 0;
        float tickDelta = time - rider.age;
        float wall = board.getWallRideLean();
        float manual = board.getManualPitch() * MathHelper.RADIANS_PER_DEGREE;
        float facing = SkatePose.travelYaw(board, tickDelta) + SkatePose.stanceYaw(board);
        float lookYaw = MathHelper.lerpAngleDegrees(tickDelta, rider.prevHeadYaw, rider.headYaw);
        head.yaw = MathHelper.clamp(MathHelper.wrapDegrees(lookYaw - facing), -85, 85) * MathHelper.RADIANS_PER_DEGREE;
        hat.copyTransform(head);

        int pushTicks = (int) board.getWorld().getTime() - board.getPushCycleStart();
        float pushProgress = MathHelper.clamp((pushTicks + tickDelta) / SkateboardEntity.PUSH_CYCLE_TICKS, 0, 1);
        float push = board.isPushing() ? MathHelper.sin(pushProgress * MathHelper.PI) : 0;
        boolean airborne = board.isAirborne();
        SkateTrick trick = board.getTrick();
        float ollie = MathHelper.sin(SkatePose.ollieProgress(board, tickDelta) * MathHelper.PI);
        float crouch = board.isCrouching() ? 1.0f : ollie * 0.34f;
        float trickTime = trick == SkateTrick.NONE ? 0.0f : SkatePose.trickProgress(board, tickDelta);
        float trickPulse = MathHelper.sin(trickTime * MathHelper.PI);
        float grab = trick.isGrab() ? SkatePose.grabAmount(trickTime) : 0.0f;

        leftLeg.pitch = MathHelper.lerp(crouch, -0.10f, -0.38f) - push * 0.12f;
        rightLeg.pitch = MathHelper.lerp(crouch, 0.16f, -0.45f) + push * 0.88f;
        leftLeg.yaw = board.isGrinding() ? -0.43f : -0.25f;
        rightLeg.yaw = board.isGrinding() ? 0.43f : 0.23f;
        leftLeg.roll = steering * 0.06f - wall * 0.12f;
        rightLeg.roll = -steering * 0.06f - wall * 0.12f;
        leftLeg.pitch -= manual * 0.55f;
        rightLeg.pitch -= manual * 0.55f;
        if (airborne) {
            float tuck = 0.08f + 0.18f * ollie + 0.16f * trickPulse + 0.22f * grab;
            leftLeg.pitch -= tuck;
            rightLeg.pitch -= tuck * 0.75f;
            leftLeg.yaw -= 0.10f * trickPulse;
            rightLeg.yaw += 0.10f * trickPulse;
            if (trick == SkateTrick.NOSEGRAB) leftLeg.pitch -= 0.26f * grab;
            if (trick == SkateTrick.TAILGRAB) rightLeg.pitch -= 0.30f * grab;
        }

        body.pitch = MathHelper.lerp(crouch, 0.06f + speed * 0.05f, 0.34f) + push * 0.10f;
        // Vanilla shares each model instance between players and does not reset body roll.
        nightShift$originalBodyRoll = body.roll;
        nightShift$hasBoardPose = true;
        body.roll = -steering * 0.14f + grind * 0.10f;
        body.yaw = board.isGrinding() ? -0.08f * grind : steering * 0.04f;
        body.pitch += manual * 0.65f + grab * 0.46f + board.getRampPitch() * MathHelper.RADIANS_PER_DEGREE * 0.20f;
        body.roll -= wall * 0.15f;

        float balance = Math.max(Math.abs(steering), Math.abs(grind));
        float armSpread = 0.24f + speed * 0.12f + balance * 1.02f;
        if (board.isGrinding()) {
            armSpread = Math.max(armSpread, 1.10f);
        }
        armSpread += Math.abs(wall) * 0.49f + (airborne ? 0.28f : 0.0f);
        leftArm.roll = -armSpread;
        rightArm.roll = armSpread;
        leftArm.pitch = -0.12f - steering * 0.24f - grind * 0.16f;
        rightArm.pitch = -0.12f + steering * 0.24f + grind * 0.16f;
        leftArm.yaw = 0.08f + steering * 0.12f;
        rightArm.yaw = -0.08f + steering * 0.12f;
        if (airborne) {
            switch (trick) {
                case INDY, TAILGRAB -> {
                    boolean tail = trick == SkateTrick.TAILGRAB;
                    rightArm.roll = MathHelper.lerp(grab, rightArm.roll, -0.36f);
                    rightArm.pitch = MathHelper.lerp(grab, rightArm.pitch, tail ? 0.31f : -0.44f);
                    rightArm.yaw -= 0.27f * grab;
                    body.yaw -= 0.31f * grab;
                    leftArm.roll -= 0.35f * grab;
                }
                case MELON, NOSEGRAB -> {
                    boolean nose = trick == SkateTrick.NOSEGRAB;
                    leftArm.roll = MathHelper.lerp(grab, leftArm.roll, 0.33f);
                    leftArm.pitch = MathHelper.lerp(grab, leftArm.pitch, nose ? -0.91f : -0.31f);
                    leftArm.yaw += 0.26f * grab;
                    body.yaw += 0.32f * grab;
                    rightArm.roll += 0.36f * grab;
                }
                default -> {
                    leftArm.pitch -= 0.18f * trickPulse;
                    rightArm.pitch -= 0.13f * trickPulse;
                }
            }
        }
    }
}
