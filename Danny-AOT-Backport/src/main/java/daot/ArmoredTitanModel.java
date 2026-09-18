package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class ArmoredTitanModel extends GeoModel<ArmoredTitanEntity> {
   private static final Identifier TEXTURE_DEFAULT = new Identifier("dannys-aot", "textures/entity/armoredtitan.png");
   private static final Identifier TEXTURE_LEG_SHATTER = new Identifier("dannys-aot", "textures/entity/armoredtitan_legshatter.png");
   private static final Identifier TEXTURE_NAPE_SHATTER = new Identifier("dannys-aot", "textures/entity/armoredtitan_napeshatter.png");
   private static final Identifier TEXTURE_BOTH_SHATTER = new Identifier("dannys-aot", "textures/entity/armoredtitan_bothshatter.png");

   public static Identifier baseTexture() {
      return TEXTURE_DEFAULT;
   }

   public Identifier getModelResource(ArmoredTitanEntity animatable) {
      return new Identifier("dannys-aot", "geo/armoredtitan.geo.json");
   }

   public Identifier getTextureResource(ArmoredTitanEntity animatable) {
      boolean legs = animatable.isLegsShattered();
      boolean nape = animatable.isNapeShattered();
      if (legs && nape) {
         return TEXTURE_BOTH_SHATTER;
      } else if (legs) {
         return TEXTURE_LEG_SHATTER;
      } else {
         return nape ? TEXTURE_NAPE_SHATTER : TEXTURE_DEFAULT;
      }
   }

   public Identifier getAnimationResource(ArmoredTitanEntity animatable) {
      return new Identifier("dannys-aot", "animations/armoredtitan.animation.json");
   }
}
