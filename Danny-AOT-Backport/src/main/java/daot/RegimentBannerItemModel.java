package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class RegimentBannerItemModel extends GeoModel<RegimentBannerItem> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/regiment_banner_item.geo.json");
   private static final Identifier MODEL_GOLD_CLOAK = new Identifier("dannys-aot", "geo/gold_regiment_banner.geo.json");
   private static final Identifier TEXTURE_GARRISON = new Identifier("dannys-aot", "textures/block/regiment_banner_garrison.png");
   private static final Identifier TEXTURE_MILITARY = new Identifier("dannys-aot", "textures/block/regiment_banner_military.png");
   private static final Identifier TEXTURE_SCOUT = new Identifier("dannys-aot", "textures/block/regiment_banner_scout.png");
   private static final Identifier TEXTURE_TRAINING = new Identifier("dannys-aot", "textures/block/regiment_banner_training.png");
   private static final Identifier TEXTURE_GOLD_CLOAK = new Identifier("dannys-aot", "textures/block/gold_regiment_banner.png");

   public Identifier getModelResource(RegimentBannerItem item) {
      return item.getRegimentType() == RegimentType.GOLD_CLOAK ? MODEL_GOLD_CLOAK : MODEL;
   }

   public Identifier getTextureResource(RegimentBannerItem item) {
      return TEXTURE_GARRISON;
   }

   public Identifier getAnimationResource(RegimentBannerItem item) {
      return item.getRegimentType() == RegimentType.GOLD_CLOAK ? MODEL_GOLD_CLOAK : MODEL;
   }

   public static Identifier getTextureForType(RegimentType type) {
      return switch (type) {
         case GARRISON -> TEXTURE_GARRISON;
         case MILITARY_POLICE -> TEXTURE_MILITARY;
         case SCOUT -> TEXTURE_SCOUT;
         case TRAINING -> TEXTURE_TRAINING;
         case GOLD_CLOAK -> TEXTURE_GOLD_CLOAK;
      };
   }
}
