package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class CameraPanState {
   public static float horizontalPan = 0.0F;
   public static float verticalPan = 0.0F;
}
