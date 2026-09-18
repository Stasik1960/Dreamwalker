package daot.mixin.client;

import daot.BiteFirstPersonRenderer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(PlayerEntityRenderer.class)
public abstract class BitePlayerVisibilityMixin {
   @Inject(method = "setModelPose", at = @At("RETURN"))
   private void dannysaot_applyBiteArmsOnly(AbstractClientPlayerEntity player, CallbackInfo ci) {
      if (BiteFirstPersonRenderer.isActive()) {
         PlayerEntityRenderer self = (PlayerEntityRenderer)(Object)this;
         PlayerEntityModel<AbstractClientPlayerEntity> m = self.getModel();
         m.head.visible = false;
         m.hat.visible = false;
         m.body.visible = false;
         m.rightLeg.visible = false;
         m.leftLeg.visible = false;
         m.jacket.visible = false;
         m.rightPants.visible = false;
         m.leftPants.visible = false;
         m.rightArm.visible = true;
         m.leftArm.visible = true;
         m.rightSleeve.visible = true;
         m.leftSleeve.visible = true;
      }
   }
}

