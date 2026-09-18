package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class APGCartridgeItemModel extends GeoModel<APGCartridgeItem> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/apg_cartridge.geo.json");
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/item/apg_cartridge.png");

   public Identifier getModelResource(APGCartridgeItem animatable) {
      return MODEL;
   }

   public Identifier getTextureResource(APGCartridgeItem animatable) {
      return TEXTURE;
   }

   public Identifier getAnimationResource(APGCartridgeItem animatable) {
      return null;
   }
}
