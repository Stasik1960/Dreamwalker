package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class TitanBeardModel extends PureTitanGeoModel<TitanBeardEntity> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/titan_beard.geo.json");
   private static final Identifier ANIMATION = new Identifier("dannys-aot", "animations/titan_beard.animation.json");
   private static final Identifier[] TEXTURES = new Identifier[]{
      new Identifier("dannys-aot", "textures/entity/titan_beard.png"),
      new Identifier("dannys-aot", "textures/entity/titan_beard2.png"),
      new Identifier("dannys-aot", "textures/entity/titan_beard3.png")
   };

   public Identifier getModelResource(TitanBeardEntity animatable) {
      return MODEL;
   }

   public Identifier getTextureResource(TitanBeardEntity animatable) {
      int variant = animatable.getTextureVariant();
      return variant >= 0 && variant < TEXTURES.length ? TEXTURES[variant] : TEXTURES[0];
   }

   public Identifier getAnimationResource(TitanBeardEntity animatable) {
      return ANIMATION;
   }
}
