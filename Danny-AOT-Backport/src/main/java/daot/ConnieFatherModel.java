package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class ConnieFatherModel extends PureTitanGeoModel<ConnieFatherEntity> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/conniefather.geo.json");
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/entity/conniefather.png");
   private static final Identifier ANIMATION = new Identifier("dannys-aot", "animations/connie_father.animation.json");

   public Identifier getModelResource(ConnieFatherEntity animatable) {
      return MODEL;
   }

   public Identifier getTextureResource(ConnieFatherEntity animatable) {
      return TEXTURE;
   }

   public Identifier getAnimationResource(ConnieFatherEntity animatable) {
      return ANIMATION;
   }
}
