package daot.mixin;

import daot.DefeatedCarryTracker;
import daot.HandcuffsTracker;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public class HandcuffedPlayerMixin {
   private Entity dannysaot_carriedByVehicle = null;

   @Inject(method = "tick", at = @At("HEAD"))
   private void freezeInputIfCuffed(CallbackInfo ci) {
      ServerPlayerEntity self = (ServerPlayerEntity)(Object)this;
      boolean cuffed = HandcuffsTracker.isCuffed(self.getUuid());
      boolean defeated = DefeatedCarryTracker.isDefeated(self.getUuid());
      if (cuffed) {
         self.forwardSpeed = 0.0F;
         self.sidewaysSpeed = 0.0F;
         Vec3d vel = self.getVelocity();
         self.setVelocity(0.0, Math.min(vel.y, 0.0), 0.0);
      }

      if (!cuffed && !defeated) {
         this.dannysaot_carriedByVehicle = null;
      } else if (self.getCommandTags().contains("dannysaot_allow_dismount")) {
         this.dannysaot_carriedByVehicle = null;
         self.removeScoreboardTag("dannysaot_allow_dismount");
      } else if (self.hasVehicle() && self.getVehicle() instanceof PlayerEntity) {
         this.dannysaot_carriedByVehicle = self.getVehicle();
      } else if (this.dannysaot_carriedByVehicle != null && !self.hasVehicle() && this.dannysaot_carriedByVehicle.isAlive()) {
         self.startRiding(this.dannysaot_carriedByVehicle, true);
      } else if (!self.hasVehicle()) {
         this.dannysaot_carriedByVehicle = null;
      }
   }

   @Inject(method = "tick", at = @At("TAIL"))
   private void freezeVelocityIfCuffed(CallbackInfo ci) {
      ServerPlayerEntity self = (ServerPlayerEntity)(Object)this;
      if (HandcuffsTracker.isCuffed(self.getUuid())) {
         Vec3d vel = self.getVelocity();
         self.setVelocity(0.0, Math.min(vel.y, 0.0), 0.0);
      }
   }

   @Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;", at = @At("HEAD"), cancellable = true)
   private void blockDropIfCuffed(ItemStack stack, boolean throwRandomly, boolean retainOwnership, CallbackInfoReturnable<ItemEntity> cir) {
      ServerPlayerEntity self = (ServerPlayerEntity)(Object)this;
      if (HandcuffsTracker.isCuffed(self.getUuid())) {
         cir.setReturnValue(null);
      }
   }

   @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
   private void blockAttackIfCuffed(Entity target, CallbackInfo ci) {
      ServerPlayerEntity self = (ServerPlayerEntity)(Object)this;
      if (HandcuffsTracker.isCuffed(self.getUuid())) {
         ci.cancel();
      }
   }
}

