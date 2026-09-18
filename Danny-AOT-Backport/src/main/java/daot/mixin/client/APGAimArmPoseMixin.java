package daot.mixin.client;

import daot.BladeAnimationHandler;
import daot.DannysAot;
import daot.RemoteAPGAimTracker;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(value = PlayerEntityModel.class, priority = 2002)
public abstract class APGAimArmPoseMixin {
   @Inject(method = "setAngles(Lnet/minecraft/entity/Entity;FFFFF)V", at = @At("RETURN"))
   private void dannysaot_applyAPGAimArm(
      Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci
   ) {
      if (entity instanceof PlayerEntity player) {
         if (player.getEquippedStack(EquipmentSlot.LEGS).getItem() == DannysAot.ODM_APG) {
            boolean mainIsApg = player.getMainHandStack().getItem() == DannysAot.APG_GUN;
            boolean offIsApg = player.getOffHandStack().getItem() == DannysAot.APG_GUN;
            if (mainIsApg || offIsApg) {
               MinecraftClient mc = MinecraftClient.getInstance();
               float partialTick = mc.getTickDelta();
               float mainAim = 0.0F;
               float offAim = 0.0F;
               if (player == mc.player) {
                  if (mainIsApg) {
                     mainAim = BladeAnimationHandler.getAimAnimation(Hand.MAIN_HAND, partialTick);
                  }

                  if (offIsApg) {
                     offAim = BladeAnimationHandler.getAimAnimation(Hand.OFF_HAND, partialTick);
                  }
               } else {
                  int id = player.getId();
                  if (mainIsApg) {
                     mainAim = RemoteAPGAimTracker.getAimAnimation(id, true, partialTick);
                  }

                  if (offIsApg) {
                     offAim = RemoteAPGAimTracker.getAimAnimation(id, false, partialTick);
                  }
               }

               if (!(mainAim < 0.001F) || !(offAim < 0.001F)) {
                  boolean mainIsRight = player.getMainArm() == Arm.RIGHT;
                  float rightAim = mainIsRight ? mainAim : offAim;
                  float leftAim = mainIsRight ? offAim : mainAim;
                  BipedEntityModel<?> self = (BipedEntityModel<?>)(Object)this;
                  float pitchRad = headPitch * (float) (Math.PI / 180.0);
                  float targetX = (float) (-Math.PI / 2) + pitchRad;
                  if (rightAim > 0.001F) {
                     self.rightArm.pitch = lerp(self.rightArm.pitch, targetX, rightAim);
                     self.rightArm.yaw = lerp(self.rightArm.yaw, -0.15F, rightAim);
                     self.rightArm.roll = lerp(self.rightArm.roll, 0.0F, rightAim);
                  }

                  if (leftAim > 0.001F) {
                     self.leftArm.pitch = lerp(self.leftArm.pitch, targetX, leftAim);
                     self.leftArm.yaw = lerp(self.leftArm.yaw, 0.15F, leftAim);
                     self.leftArm.roll = lerp(self.leftArm.roll, 0.0F, leftAim);
                  }

                  PlayerEntityModel<?> pm = (PlayerEntityModel<?>)(Object)this;
                  if (rightAim > 0.001F) {
                     pm.rightSleeve.copyTransform(pm.rightArm);
                  }

                  if (leftAim > 0.001F) {
                     pm.leftSleeve.copyTransform(pm.leftArm);
                  }
               }
            }
         }
      }
   }

   private static float lerp(float a, float b, float t) {
      return a + (b - a) * t;
   }
}

