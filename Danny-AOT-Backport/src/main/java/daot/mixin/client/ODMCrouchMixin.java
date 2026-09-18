package daot.mixin.client;

import daot.AttackTitanEntity;
import daot.BeastTitanEntity;
import daot.DannysAot;
import daot.FemaleTitanEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(Entity.class)
public abstract class ODMCrouchMixin {
   @Inject(method = "setPose", at = @At("HEAD"), cancellable = true)
   private void preventAirborneCrouchPoseSet(EntityPose pose, CallbackInfo ci) {
      if (pose == EntityPose.CROUCHING && (Object)this instanceof ClientPlayerEntity player && daot$shouldBlockCrouch(player)) {
         ci.cancel();
      }
   }

   @Inject(method = "getPose", at = @At("RETURN"), cancellable = true)
   private void overrideCrouchPoseGet(CallbackInfoReturnable<EntityPose> cir) {
      if (cir.getReturnValue() == EntityPose.CROUCHING && (Object)this instanceof ClientPlayerEntity player && daot$shouldBlockCrouch(player)) {
         cir.setReturnValue(EntityPose.STANDING);
      }
   }

   private static boolean daot$shouldBlockCrouch(ClientPlayerEntity player) {
      if (!player.isOnGround() && DannysAot.isODMGear(player.getEquippedStack(EquipmentSlot.LEGS).getItem())) {
         return true;
      } else {
         Entity vehicle = player.getVehicle();
         if (vehicle instanceof AttackTitanEntity at) {
            return at.isGrabbing();
         } else if (vehicle instanceof FemaleTitanEntity ft) {
            return ft.isGrabbing();
         } else {
            return vehicle instanceof BeastTitanEntity bt ? bt.getGrabbedEntityId() != -1 : false;
         }
      }
   }
}

