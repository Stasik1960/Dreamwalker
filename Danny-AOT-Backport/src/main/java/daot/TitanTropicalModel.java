package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class TitanTropicalModel extends PureTitanGeoModel<TitanTropicalEntity> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/tropical.geo.json");
   private static final Identifier ANIMATION = new Identifier("dannys-aot", "animations/tropical.animation.json");
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/entity/tropical.png");

   public Identifier getModelResource(TitanTropicalEntity animatable) {
      return MODEL;
   }

   public Identifier getTextureResource(TitanTropicalEntity animatable) {
      return TEXTURE;
   }

   public Identifier getAnimationResource(TitanTropicalEntity animatable) {
      return ANIMATION;
   }
}
