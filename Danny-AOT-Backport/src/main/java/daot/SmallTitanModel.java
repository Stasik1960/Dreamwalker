package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class SmallTitanModel extends PureTitanGeoModel<SmallTitanEntity> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/small_titan.geo.json");
   private static final Identifier ANIMATION = new Identifier("dannys-aot", "animations/small_titan.animation.json");
   private static final Identifier[] TEXTURES = new Identifier[]{
      new Identifier("dannys-aot", "textures/entity/small_titan.png"),
      new Identifier("dannys-aot", "textures/entity/small_titan_brown.png"),
      new Identifier("dannys-aot", "textures/entity/small_titan_blonde.png")
   };

   public Identifier getModelResource(SmallTitanEntity animatable) {
      return MODEL;
   }

   public Identifier getTextureResource(SmallTitanEntity animatable) {
      int variant = animatable.getTextureVariant();
      return variant >= 0 && variant < TEXTURES.length ? TEXTURES[variant] : TEXTURES[0];
   }

   public Identifier getAnimationResource(SmallTitanEntity animatable) {
      return ANIMATION;
   }
}
