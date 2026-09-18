package daot.mixin.client;

import daot.SadTitanEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(PlayerEntityModel.class)
public class SadTitanRidePoseMixin {
   @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
   private void dannysaot$undoSitPoseForSadTitan(
      LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci
   ) {
      if (entity.getVehicle() instanceof SadTitanEntity sad && sad.isRideable) {
         PlayerEntityModel<?> model = (PlayerEntityModel<?>)(Object)this;
         model.rightLeg.pitch = 0.0F;
         model.rightLeg.yaw = 0.0F;
         model.leftLeg.pitch = 0.0F;
         model.leftLeg.yaw = 0.0F;
         model.rightPants.pitch = 0.0F;
         model.rightPants.yaw = 0.0F;
         model.leftPants.pitch = 0.0F;
         model.leftPants.yaw = 0.0F;
      }
   }
}

