package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class SmallTitan2Model extends PureTitanGeoModel<SmallTitan2Entity> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/small_titan2.geo.json");
   private static final Identifier ANIMATION = new Identifier("dannys-aot", "animations/small_titan2.animation.json");
   private static final Identifier[] TEXTURES = new Identifier[]{
      new Identifier("dannys-aot", "textures/entity/small_titan_two.png"),
      new Identifier("dannys-aot", "textures/entity/small_titan_two_brown.png"),
      new Identifier("dannys-aot", "textures/entity/small_titan_two_blonde.png"),
      new Identifier("dannys-aot", "textures/entity/small_titan_two_black.png")
   };

   public Identifier getModelResource(SmallTitan2Entity animatable) {
      return MODEL;
   }

   public Identifier getTextureResource(SmallTitan2Entity animatable) {
      int variant = animatable.getTextureVariant();
      return variant >= 0 && variant < TEXTURES.length ? TEXTURES[variant] : TEXTURES[0];
   }

   public Identifier getAnimationResource(SmallTitan2Entity animatable) {
      return ANIMATION;
   }
}
