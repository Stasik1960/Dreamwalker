package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class ThunderSpearItemModel extends GeoModel<ThunderSpearItem> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/thunder_spear.geo.json");
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/item/thunder_spear.png");
   private static final Identifier ANIMATION = new Identifier("dannys-aot", "animations/thunder_spear.animation.json");

   public Identifier getModelResource(ThunderSpearItem animatable) {
      return MODEL;
   }

   public Identifier getTextureResource(ThunderSpearItem animatable) {
      return TEXTURE;
   }

   public Identifier getAnimationResource(ThunderSpearItem animatable) {
      return ANIMATION;
   }
}
