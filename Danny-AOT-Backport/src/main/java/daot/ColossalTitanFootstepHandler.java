package daot;

import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundCategory;
import software.bernie.geckolib.core.animation.AnimationController;

@Environment(EnvType.CLIENT)
public final class ColossalTitanFootstepHandler {
   private ColossalTitanFootstepHandler() {
   }

   public static void install(AnimationController<ColossalTitanEntity> controller, ColossalTitanEntity entity) {
      controller.setCustomInstructionKeyframeHandler(event -> {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.player != null && mc.world != null) {
            ColossalTitanEntity titan = (ColossalTitanEntity)event.getAnimatable();
            UUID shifter = titan.getShifterUUID();
            if (shifter != null && shifter.equals(mc.player.getUuid())) {
               String instructions = event.getKeyframeData().getInstructions();
               if (instructions != null) {
                  if (instructions.contains("left") || instructions.contains("right")) {
                     CameraShakeHandler.triggerColossalStompBurst(0.6F);
                     float pitch = 0.6F + (float)Math.random() * 0.05F;
                     mc.world.playSound(titan.getX(), titan.getY(), titan.getZ(), ModSounds.TITAN_STOMP, SoundCategory.HOSTILE, 15.0F, pitch, false);
                  }
               }
            }
         }
      });
   }
}
