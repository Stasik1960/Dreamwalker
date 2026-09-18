package daot.mixin.client;

import daot.GeassClientState;
import daot.network.MindControlActionPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import daot.compat.network.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(MinecraftClient.class)
public class MindControlInteractionMixin {
   private int mindControlUseDelay = 0;
   private boolean mcClientMining = false;

   private HitResult doFreshPick(MinecraftClient mc) {
      Entity cam = mc.getCameraEntity();
      if (cam != null && cam.getWorld() != null) {
         float pt = mc.getTickDelta();
         Vec3d eye = cam.getCameraPosVec(pt);
         Vec3d look = cam.getRotationVec(pt);
         double blockReach = 4.5;
         double entityReach = 3.0;
         Vec3d blockEnd = eye.add(look.multiply(blockReach));
         BlockHitResult blockHit = cam.getWorld().raycast(new RaycastContext(eye, blockEnd, ShapeType.OUTLINE, FluidHandling.NONE, cam));
         double blockDistSq = blockHit.getType() != Type.MISS ? eye.squaredDistanceTo(blockHit.getPos()) : Double.MAX_VALUE;
         Vec3d entityEnd = eye.add(look.multiply(entityReach));
         Box searchBox = cam.getBoundingBox().stretch(look.multiply(entityReach)).expand(1.0);
         int targetId = GeassClientState.mindControlTargetEntityId;
         EntityHitResult entityHit = ProjectileUtil.raycast(
            cam,
            eye,
            entityEnd,
            searchBox,
            e -> !e.isSpectator() && e.canHit() && e != mc.player && (targetId == -1 || e.getId() != targetId),
            entityReach * entityReach
         );
         if (entityHit != null) {
            double entityDistSq = eye.squaredDistanceTo(entityHit.getPos());
            if (entityDistSq < blockDistSq) {
               return entityHit;
            }
         }

         return blockHit;
      } else {
         return null;
      }
   }

   @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
   private void handleAttack(CallbackInfoReturnable<Boolean> cir) {
      if (GeassClientState.isMindControlTarget) {
         cir.setReturnValue(false);
      } else {
         if (GeassClientState.isMindControlController) {
            MinecraftClient mc = (MinecraftClient)(Object)this;
            HitResult hit = mc.crosshairTarget;
            if (hit != null && hit.getType() == Type.ENTITY) {
               EntityHitResult ehr = (EntityHitResult)hit;
               int targetId = GeassClientState.mindControlTargetEntityId;
               if (targetId == -1 || ehr.getEntity().getId() != targetId) {
                  ClientPlayNetworking.send(new MindControlActionPayload(1, ehr.getEntity().getId(), 0L, 0, 0));
               }

               if (mc.player != null) {
                  mc.player.swingHand(Hand.MAIN_HAND);
               }

               cir.setReturnValue(true);
               return;
            }
         }
      }
   }

   @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
   private void handleContinueAttack(boolean holding, CallbackInfo ci) {
      if (GeassClientState.isMindControlTarget) {
         ci.cancel();
      }
   }

   @Inject(method = "handleInputEvents", at = @At("HEAD"))
   private void blockTargetKeybinds(CallbackInfo ci) {
      if (GeassClientState.isMindControlTarget) {
         MinecraftClient mc = (MinecraftClient)(Object)this;

         while (mc.options.inventoryKey.wasPressed()) {
         }

         while (mc.options.dropKey.wasPressed()) {
         }

         while (mc.options.swapHandsKey.wasPressed()) {
         }

         for (KeyBinding key : mc.options.hotbarKeys) {
            while (key.wasPressed()) {
            }
         }
      }
   }

   @Inject(method = "tick", at = @At("HEAD"))
   private void tickMindControl(CallbackInfo ci) {
      if (this.mindControlUseDelay > 0) {
         this.mindControlUseDelay--;
      }
   }

   @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
   private void handleUseItem(CallbackInfo ci) {
      if (GeassClientState.isMindControlTarget) {
         ci.cancel();
      } else {
         if (GeassClientState.isMindControlController) {
            MinecraftClient mc = (MinecraftClient)(Object)this;
            HitResult hit = mc.crosshairTarget;
            if (hit != null && hit.getType() == Type.ENTITY) {
               EntityHitResult ehr = (EntityHitResult)hit;
               int targetId = GeassClientState.mindControlTargetEntityId;
               if (targetId == -1 || ehr.getEntity().getId() != targetId) {
                  ClientPlayNetworking.send(new MindControlActionPayload(4, ehr.getEntity().getId(), 0L, 0, 0));
               }

               ci.cancel();
               return;
            }
         }
      }
   }
}

