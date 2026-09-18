package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class TitanModel extends PureTitanGeoModel<TitanEntity> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/puretitan.geo.json");
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/entity/puretitan.png");
   private static final Identifier ANIMATION = new Identifier("dannys-aot", "animations/puretitan.animation.json");

   public Identifier getModelResource(TitanEntity animatable) {
      return MODEL;
   }

   public Identifier getTextureResource(TitanEntity animatable) {
      return TEXTURE;
   }

   public Identifier getAnimationResource(TitanEntity animatable) {
      return ANIMATION;
   }
}
