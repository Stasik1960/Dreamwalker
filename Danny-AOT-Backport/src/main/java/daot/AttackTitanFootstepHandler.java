package daot;

import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundCategory;
import software.bernie.geckolib.core.animation.AnimationController;

@Environment(EnvType.CLIENT)
public final class AttackTitanFootstepHandler {
   private AttackTitanFootstepHandler() {
   }

   public static void install(AnimationController<AttackTitanEntity> controller, AttackTitanEntity entity) {
      controller.setCustomInstructionKeyframeHandler(event -> {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.player != null && mc.world != null) {
            AttackTitanEntity titan = (AttackTitanEntity)event.getAnimatable();
            UUID shifter = titan.getShifterUUID();
            if (shifter != null && shifter.equals(mc.player.getUuid())) {
               String instructions = event.getKeyframeData().getInstructions();
               if (instructions != null) {
                  if (instructions.contains("left") || instructions.contains("right")) {
                     boolean isRunning = titan.isSprinting() && !titan.isArmed();
                     float shakeIntensity = isRunning ? 0.9F : 0.75F;
                     CameraShakeHandler.triggerStompImpulse(shakeIntensity);
                     float volume = isRunning ? 8.0F : 5.3F;
                     if (titan.isInSneakingPose()) {
                        volume *= 0.5F;
                     }

                     float pitch = 0.5F + (float)Math.random() * 0.1F;
                     mc.world.playSound(titan.getX(), titan.getY(), titan.getZ(), ModSounds.TITAN_STOMP, SoundCategory.HOSTILE, volume, pitch, false);
                  }
               }
            }
         }
      });
   }
}
