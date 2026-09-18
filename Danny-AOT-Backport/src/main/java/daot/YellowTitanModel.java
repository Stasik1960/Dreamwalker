package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class YellowTitanModel extends PureTitanGeoModel<YellowTitanEntity> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/yellowtitan.geo.json");
   private static final Identifier ANIMATION = new Identifier("dannys-aot", "animations/yellowtitan.animation.json");
   private static final Identifier[] TEXTURES = new Identifier[]{
      new Identifier("dannys-aot", "textures/entity/yellowtitan.png"), new Identifier("dannys-aot", "textures/entity/yellowtitan2.png")
   };

   public Identifier getModelResource(YellowTitanEntity animatable) {
      return MODEL;
   }

   public Identifier getTextureResource(YellowTitanEntity animatable) {
      int variant = animatable.getTextureVariant();
      return variant >= 0 && variant < TEXTURES.length ? TEXTURES[variant] : TEXTURES[0];
   }

   public Identifier getAnimationResource(YellowTitanEntity animatable) {
      return ANIMATION;
   }
}
