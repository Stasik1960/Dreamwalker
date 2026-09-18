package daot.mixin.client;

import daot.TestShifterCameraAnchor;
import daot.TestShifterTitanEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Camera.class)
public abstract class TestShifterFirstPersonCameraMixin {
   private static final double HEAD_FORWARD_OFFSET = 0.9;
   private static final double FALLBACK_Y_OFFSET = 4.9;

   @Shadow
   protected abstract void setPos(double var1, double var3, double var5);

   @Inject(method = "update", at = @At("TAIL"))
   private void pinCameraToTestTitanHead(BlockView level, Entity focusedEntity, boolean thirdPerson, boolean mirrored, float partialTick, CallbackInfo ci) {
      if (!thirdPerson) {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.player != null) {
            if (mc.player.getVehicle() instanceof TestShifterTitanEntity testTitan) {
               float yaw = MathHelper.lerpAngleDegrees(partialTick, testTitan.prevBodyYaw, testTitan.bodyYaw);
               double yawRad = Math.toRadians(yaw);
               double forwardX = -Math.sin(yawRad);
               double forwardZ = Math.cos(yawRad);
               Vec3d headPos = TestShifterCameraAnchor.get(testTitan.getId());
               double baseX;
               double baseY;
               double baseZ;
               if (headPos != null) {
                  baseX = headPos.x;
                  baseY = headPos.y;
                  baseZ = headPos.z;
               } else {
                  double tx = MathHelper.lerp((double)partialTick, testTitan.lastRenderX, testTitan.getX());
                  double ty = MathHelper.lerp((double)partialTick, testTitan.lastRenderY, testTitan.getY());
                  double tz = MathHelper.lerp((double)partialTick, testTitan.lastRenderZ, testTitan.getZ());
                  baseX = tx;
                  baseY = ty + 4.9;
                  baseZ = tz;
               }

               this.setPos(baseX + forwardX * 0.9, baseY, baseZ + forwardZ * 0.9);
            }
         }
      }
   }
}
