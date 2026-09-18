package daot.mixin.client;

import daot.HomelanderFlightHandler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(value = PlayerEntityModel.class, priority = 2002)
public abstract class HomelanderHeadPitchClampMixin {
   private static final float MAX_HEAD_PITCH_RAD = (float)Math.toRadians(40.0);

   @Inject(method = "setAngles(Lnet/minecraft/entity/Entity;FFFFF)V", at = @At("RETURN"))
   private void daot$clampHomelanderHeadPitch(
      Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci
   ) {
      if (entity instanceof PlayerEntity player) {
         if (HomelanderFlightHandler.isFlying(player.getUuid())) {
            BipedEntityModel<?> self = (BipedEntityModel<?>)(Object)this;
            self.head.pitch = MathHelper.clamp(self.head.pitch, -MAX_HEAD_PITCH_RAD, MAX_HEAD_PITCH_RAD);
         }
      }
   }
}

