package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class FemaleTitanModel extends GeoModel<FemaleTitanEntity> {
   private static final Identifier TEX_BASE = new Identifier("dannys-aot", "textures/entity/femaletitan.png");
   private static final Identifier TEX_HARDENED_NAPE = new Identifier("dannys-aot", "textures/entity/femaletitan_hardened_nape.png");
   private static final Identifier TEX_HARDENED_FEET = new Identifier("dannys-aot", "textures/entity/femaletitan_hardened_feet.png");
   private static final Identifier TEX_HARDENED_ARMS = new Identifier("dannys-aot", "textures/entity/femaletitan_hardened_arms.png");

   public static Identifier textureFor(int hardeningMode) {
      return switch (hardeningMode) {
         case 1 -> TEX_HARDENED_NAPE;
         case 2 -> TEX_HARDENED_FEET;
         case 3 -> TEX_HARDENED_ARMS;
         default -> TEX_BASE;
      };
   }

   public Identifier getModelResource(FemaleTitanEntity animatable) {
      return new Identifier("dannys-aot", "geo/femaletitan.geo.json");
   }

   public Identifier getTextureResource(FemaleTitanEntity animatable) {
      return textureFor(animatable.getDisplayedHardeningMode());
   }

   public Identifier getAnimationResource(FemaleTitanEntity animatable) {
      return new Identifier("dannys-aot", "animations/femaletitan.animation.json");
   }
}
