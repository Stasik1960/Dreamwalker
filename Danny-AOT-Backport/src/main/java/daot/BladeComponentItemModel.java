package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class BladeComponentItemModel extends GeoModel<BladeComponentItem> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/bladeitem.geo.json");
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/item/bladeitem.png");
   private static final Identifier ANIMATION = new Identifier("dannys-aot", "animations/blade.animation.json");

   public Identifier getModelResource(BladeComponentItem animatable) {
      return MODEL;
   }

   public Identifier getTextureResource(BladeComponentItem animatable) {
      return TEXTURE;
   }

   public Identifier getAnimationResource(BladeComponentItem animatable) {
      return ANIMATION;
   }
}
