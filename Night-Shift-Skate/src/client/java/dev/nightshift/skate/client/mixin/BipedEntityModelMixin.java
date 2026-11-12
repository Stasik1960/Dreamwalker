package dev.nightshift.skate.client.mixin;

import dev.nightshift.skate.SkateboardEntity;
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
        float speed = MathHelper.clamp(board.getRideSpeed() / (float) SkateboardEntity.MAX_SPEED, 0, 1);
        float steering = board.getSteeringLean();
        float grind = board.isGrinding() ? board.getGrindLean() : 0;
        int pushTicks = (int) board.getWorld().getTime() - board.getPushCycleStart();
        float pushProgress = MathHelper.clamp((pushTicks + time - rider.age) / SkateboardEntity.PUSH_CYCLE_TICKS, 0, 1);
        float push = board.isPushing() ? MathHelper.sin(pushProgress * MathHelper.PI) : 0;
        float crouch = board.isCrouching() ? 1.0f : 0.0f;

        leftLeg.pitch = MathHelper.lerp(crouch, -0.10f, -0.38f) - push * 0.12f;
        rightLeg.pitch = MathHelper.lerp(crouch, 0.16f, -0.45f) + push * 0.88f;
        leftLeg.yaw = board.isGrinding() ? -0.43f : -0.25f;
        rightLeg.yaw = board.isGrinding() ? 0.43f : 0.23f;
        leftLeg.roll = -steering * 0.06f;
        rightLeg.roll = -steering * 0.06f;

        body.pitch = MathHelper.lerp(crouch, 0.06f + speed * 0.05f, 0.34f) + push * 0.10f;
        // Vanilla shares each model instance between players and does not reset body roll.
        nightShift$originalBodyRoll = body.roll;
        nightShift$hasBoardPose = true;
        body.roll = -steering * 0.14f + grind * 0.10f;
        body.yaw = board.isGrinding() ? -0.08f * grind : steering * 0.04f;

        float balance = Math.max(Math.abs(steering), Math.abs(grind));
        float armSpread = 0.24f + speed * 0.12f + balance * 1.02f;
        if (board.isGrinding()) {
            armSpread = Math.max(armSpread, 1.10f);
        }
        leftArm.roll = -armSpread;
        rightArm.roll = armSpread;
        leftArm.pitch = -0.12f - steering * 0.24f - grind * 0.16f;
        rightArm.pitch = -0.12f + steering * 0.24f + grind * 0.16f;
        leftArm.yaw = 0.08f + steering * 0.12f;
        rightArm.yaw = -0.08f + steering * 0.12f;
    }
}
