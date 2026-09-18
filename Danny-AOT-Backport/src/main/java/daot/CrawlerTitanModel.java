package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class CrawlerTitanModel extends PureTitanGeoModel<CrawlerTitanEntity> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/crawler.geo.json");
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/entity/crawler.png");
   private static final Identifier ANIMATION = new Identifier("dannys-aot", "animations/crawler.animation.json");

   public Identifier getModelResource(CrawlerTitanEntity animatable) {
      return MODEL;
   }

   public Identifier getTextureResource(CrawlerTitanEntity animatable) {
      return TEXTURE;
   }

   public Identifier getAnimationResource(CrawlerTitanEntity animatable) {
      return ANIMATION;
   }
}
