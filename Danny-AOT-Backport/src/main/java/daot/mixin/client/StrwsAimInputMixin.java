package daot.mixin.client;

import daot.FadingLoopSound;
import daot.ModSounds;
import daot.StrwsAimClientState;
import daot.StrwsBlockEntity;
import daot.network.StrwsAimPayload;
import daot.network.StrwsControlPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import daot.compat.network.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(KeyboardInput.class)
public class StrwsAimInputMixin {
   private static final float AIM_SPEED = 2.5F;
   private static final float MAX_PITCH = 60.0F;
   private static final float MIN_PITCH = -60.0F;
   private static final float AIM_SOUND_VOLUME = 1.0F;
   private static final float AIM_SOUND_PITCH = 1.0F;
   private static final float AIM_FADE_IN = 0.5F;
   private static final float AIM_FADE_OUT = 0.5F;
   private static final double AIM_SOUND_RANGE = 32.0;
   private static FadingLoopSound aimSound = null;

   @Inject(method = "tick", at = @At("TAIL"))
   private void strwsAimInputs(boolean isSneaking, float sneakSpeedModifier, CallbackInfo ci) {
      if (!StrwsAimClientState.isAiming()) {
         stopAimSound();
      } else {
         MinecraftClient mc = MinecraftClient.getInstance();
         ClientPlayerEntity player = mc.player;
         if (player != null && mc.world != null) {
            BlockPos pos = StrwsAimClientState.controlledPos;
            if (mc.world.getBlockEntity(pos) instanceof StrwsBlockEntity be) {
               KeyboardInput self = (KeyboardInput)(Object)this;
               if (self.sneaking) {
                  stopAimSound();
                  ClientPlayNetworking.send(new StrwsControlPayload(pos, false));
                  StrwsAimClientState.stop();
                  this.freeze(self, player);
               } else {
                  float yaw = be.getAimYaw();
                  float pitch = be.getAimPitch();
                  if (self.pressingLeft) {
                     yaw += 2.5F;
                  }

                  if (self.pressingRight) {
                     yaw -= 2.5F;
                  }

                  if (self.pressingForward) {
                     pitch += 2.5F;
                  }

                  if (self.pressingBack) {
                     pitch -= 2.5F;
                  }

                  yaw = MathHelper.wrapDegrees(yaw);
                  pitch = MathHelper.clamp(pitch, -60.0F, 60.0F);
                  boolean turning = self.pressingLeft || self.pressingRight || self.pressingForward || self.pressingBack;
                  if (turning) {
                     startAimSound(pos);
                  } else {
                     stopAimSound();
                  }

                  be.setAim(yaw, pitch);
                  ClientPlayNetworking.send(new StrwsAimPayload(pos, yaw, pitch));
                  this.freeze(self, player);
               }
            } else {
               stopAimSound();
               StrwsAimClientState.stop();
            }
         } else {
            stopAimSound();
         }
      }
   }

   private static void startAimSound(BlockPos pos) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (aimSound != null && mc.getSoundManager().isPlaying(aimSound)) {
         aimSound.cancelFadeOut();
      } else {
         aimSound = new FadingLoopSound(
            ModSounds.STRWS_AIM, SoundCategory.BLOCKS, 1.0F, 1.0F, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0.5F, 0.5F, 32.0
         );
         mc.getSoundManager().play(aimSound);
      }
   }

   private static void stopAimSound() {
      if (aimSound != null) {
         if (MinecraftClient.getInstance().getSoundManager().isPlaying(aimSound)) {
            aimSound.fadeOut();
         } else {
            aimSound = null;
         }
      }
   }

   private void freeze(KeyboardInput self, ClientPlayerEntity player) {
      self.pressingForward = false;
      self.pressingBack = false;
      self.pressingLeft = false;
      self.pressingRight = false;
      self.movementForward = 0.0F;
      self.movementSideways = 0.0F;
      self.jumping = false;
      Vec3d v = player.getVelocity();
      player.setVelocity(0.0, Math.min(v.y, 0.0), 0.0);
   }
}

