package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class ZekesGlassesItemModel extends GeoModel<ZekesGlassesItem> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/zeke_glasses.geo.json");
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/armor/zeke_glasses.png");

   public Identifier getModelResource(ZekesGlassesItem animatable) {
      return MODEL;
   }

   public Identifier getTextureResource(ZekesGlassesItem animatable) {
      return TEXTURE;
   }

   public Identifier getAnimationResource(ZekesGlassesItem animatable) {
      return null;
   }
}
