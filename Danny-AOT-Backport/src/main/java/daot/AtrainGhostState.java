package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class AtrainGhostState {
   public static float currentAlpha = 1.0F;

   private AtrainGhostState() {
   }
}
