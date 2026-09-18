package daot;

import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public final class CustomBossBarColors {
   private static final Map<String, Integer> COLORS = new HashMap<>();

   private CustomBossBarColors() {
   }

   public static int get(Text name) {
      if (name == null) {
         return -1;
      } else {
         Integer rgb = COLORS.get(name.getString());
         return rgb != null ? rgb : -1;
      }
   }

   static {
      COLORS.put("Cart Titan", 16119260);
   }
}
