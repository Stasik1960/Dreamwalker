package dev.nightwatch.carriage.client.mixin;

import dev.nightwatch.carriage.CarriageEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityModel.class)
abstract class PlayerEntityModelMixin<T extends LivingEntity> extends BipedEntityModel<T> {
    private PlayerEntityModelMixin(ModelPart root) { super(root); }

    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("RETURN"))
    private void nightwatchcarriage$pose(T player, float limbAngle, float limbDistance,
                                        float age, float headYaw, float headPitch, CallbackInfo ci) {
        if (!(player.getVehicle() instanceof CarriageEntity carriage) ||
            carriage.seatOf(player) != CarriageEntity.Seat.PULLER) return;
        float partial = MathHelper.clamp(age - player.age, 0f, 1f);
        float phase = carriage.visualWheel(partial) * MathHelper.RADIANS_PER_DEGREE * 4f;
        float stride = MathHelper.cos(phase) * MathHelper.clamp(carriage.speed() / 4.2f, 0f, 1f) * .94f;
        leftLeg.setAngles(stride, 0f, 0f);
        rightLeg.setAngles(-stride, 0f, 0f);
        leftArm.setAngles(-1.22f, -.025f, -.03f);
        rightArm.setAngles(-1.22f, .025f, .03f);
        head.yaw = MathHelper.clamp(head.yaw, -.86f, .86f);
        @SuppressWarnings("unchecked")
        PlayerEntityModel<T> model = (PlayerEntityModel<T>) (Object) this;
        model.leftPants.copyTransform(leftLeg);
        model.rightPants.copyTransform(rightLeg);
        model.leftSleeve.copyTransform(leftArm);
        model.rightSleeve.copyTransform(rightArm);
        hat.copyTransform(head);
    }
}
