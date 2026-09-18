package daot.mixin;

import daot.ArmoredTitanEntity;
import daot.AttackTitanEntity;
import daot.BeastTitanEntity;
import daot.ColossalTitanEntity;
import daot.FemaleTitanEntity;
import daot.WarhammerTitanEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class TitanVehicleMovementMixin {
   @Shadow
   public ServerPlayerEntity player;
   @Shadow
   private double updatedRiddenX;
   @Shadow
   private double updatedRiddenY;
   @Shadow
   private double updatedRiddenZ;
   @Shadow
   private double lastTickRiddenX;
   @Shadow
   private double lastTickRiddenY;
   @Shadow
   private double lastTickRiddenZ;

   @Inject(method = "onVehicleMove", at = @At("HEAD"), cancellable = true)
   private void dannysaot$handleTitanVehicle(VehicleMoveC2SPacket packet, CallbackInfo ci) {
      if (this.player.getServerWorld().getServer().isOnThread()) {
         Entity rootVehicle = this.player.getRootVehicle();
         if (rootVehicle != this.player) {
            if (rootVehicle.getControllingPassenger() == this.player) {
               if (isTitanShifter(rootVehicle)) {
                  double px = packet.getX();
                  double py = packet.getY();
                  double pz = packet.getZ();
                  float pyaw = packet.getYaw();
                  float ppitch = packet.getPitch();
                  if (!Double.isNaN(px)
                     && !Double.isNaN(py)
                     && !Double.isNaN(pz)
                     && Double.isFinite(px)
                     && Double.isFinite(py)
                     && Double.isFinite(pz)
                     && !Float.isNaN(pyaw)
                     && !Float.isNaN(ppitch)) {
                     double x = MathHelper.clamp(px, -3.0E7, 3.0E7);
                     double y = MathHelper.clamp(py, -2.0E7, 2.0E7);
                     double z = MathHelper.clamp(pz, -3.0E7, 3.0E7);
                     float yRot = MathHelper.wrapDegrees(pyaw);
                     float xRot = MathHelper.wrapDegrees(ppitch);
                     double oldY = rootVehicle.getY();
                     rootVehicle.updatePositionAndAngles(x, y, z, yRot, xRot);
                     rootVehicle.velocityDirty = true;
                     Box bb = rootVehicle.getBoundingBox();
                     boolean onGround = !rootVehicle.getWorld().isSpaceEmpty(rootVehicle, bb.offset(0.0, -0.04, 0.0));
                     rootVehicle.setOnGround(onGround);
                     double yDelta = y - oldY;
                     if (rootVehicle instanceof LivingEntity le) {
                        if (onGround) {
                           if (le.fallDistance > 0.0F) {
                              le.handleFallDamage(le.fallDistance, 1.0F, le.getDamageSources().fall());
                              le.onLanding();
                           }
                        } else if (yDelta < 0.0) {
                           le.fallDistance -= (float)yDelta;
                        }
                     }

                     this.updatedRiddenX = x;
                     this.updatedRiddenY = y;
                     this.updatedRiddenZ = z;
                     this.lastTickRiddenX = x;
                     this.lastTickRiddenY = y;
                     this.lastTickRiddenZ = z;
                     this.player.getServerWorld().getChunkManager().updatePosition(this.player);
                     ci.cancel();
                  }
               }
            }
         }
      }
   }

   private static boolean isTitanShifter(Entity entity) {
      return entity instanceof AttackTitanEntity
         || entity instanceof ArmoredTitanEntity
         || entity instanceof ColossalTitanEntity
         || entity instanceof FemaleTitanEntity
         || entity instanceof BeastTitanEntity
         || entity instanceof WarhammerTitanEntity;
   }
}
