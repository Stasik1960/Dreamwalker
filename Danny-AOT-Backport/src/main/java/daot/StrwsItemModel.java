package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class StrwsItemModel extends GeoModel<StrwsItem> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/strws.geo.json");
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/block/strws.png");

   public Identifier getModelResource(StrwsItem animatable) {
      return MODEL;
   }

   public Identifier getTextureResource(StrwsItem animatable) {
      return TEXTURE;
   }

   public Identifier getAnimationResource(StrwsItem animatable) {
      return null;
   }
}
