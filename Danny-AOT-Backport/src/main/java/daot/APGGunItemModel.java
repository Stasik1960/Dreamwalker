package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class APGGunItemModel extends GeoModel<APGGunItem> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/apg_gun.geo.json");
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/item/apg_gun.png");
   private static final Identifier ANIMATION = new Identifier("dannys-aot", "animations/apg_gun.animation.json");

   public Identifier getModelResource(APGGunItem animatable) {
      return MODEL;
   }

   public Identifier getTextureResource(APGGunItem animatable) {
      return TEXTURE;
   }

   public Identifier getAnimationResource(APGGunItem animatable) {
      return ANIMATION;
   }
}
