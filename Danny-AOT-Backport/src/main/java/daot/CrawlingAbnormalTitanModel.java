package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class CrawlingAbnormalTitanModel extends PureTitanGeoModel<CrawlingAbnormalTitanEntity> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/crawlingabnormal.geo.json");
   private static final Identifier ANIMATION = new Identifier("dannys-aot", "animations/crawlingabnormal.animation.json");
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/entity/crawlingabnormal.png");

   public Identifier getModelResource(CrawlingAbnormalTitanEntity animatable) {
      return MODEL;
   }

   public Identifier getTextureResource(CrawlingAbnormalTitanEntity animatable) {
      return TEXTURE;
   }

   public Identifier getAnimationResource(CrawlingAbnormalTitanEntity animatable) {
      return ANIMATION;
   }
}
