package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class SadTitanModel extends PureTitanGeoModel<SadTitanEntity> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/sadtitan.geo.json");
   private static final Identifier ANIMATION = new Identifier("dannys-aot", "animations/sadtitan.animation.json");
   private static final Identifier[] TEXTURES = new Identifier[]{
      new Identifier("dannys-aot", "textures/entity/sadtitan.png"),
      new Identifier("dannys-aot", "textures/entity/sadtitan2.png"),
      new Identifier("dannys-aot", "textures/entity/sadtitan3.png")
   };

   public Identifier getModelResource(SadTitanEntity animatable) {
      return MODEL;
   }

   public Identifier getTextureResource(SadTitanEntity animatable) {
      int variant = animatable.getTextureVariant();
      return variant >= 0 && variant < TEXTURES.length ? TEXTURES[variant] : TEXTURES[0];
   }

   public Identifier getAnimationResource(SadTitanEntity animatable) {
      return ANIMATION;
   }

   @Override
   protected float headPitchSign() {
      return -1.0F;
   }
}
