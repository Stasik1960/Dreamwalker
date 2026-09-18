package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class FritzTitanModel extends PureTitanGeoModel<FritzTitanEntity> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/titanfritz.geo.json");
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/entity/titanfritz.png");
   private static final Identifier ANIMATION = new Identifier("dannys-aot", "animations/titanfritz.animation.json");

   public Identifier getModelResource(FritzTitanEntity animatable) {
      return MODEL;
   }

   public Identifier getTextureResource(FritzTitanEntity animatable) {
      return TEXTURE;
   }

   public Identifier getAnimationResource(FritzTitanEntity animatable) {
      return ANIMATION;
   }
}
