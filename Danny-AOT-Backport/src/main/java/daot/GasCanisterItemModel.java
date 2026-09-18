package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class GasCanisterItemModel extends GeoModel<GasCanisterItem> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/gas_canister.geo.json");
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/item/gas_canister.png");

   public Identifier getModelResource(GasCanisterItem animatable) {
      return MODEL;
   }

   public Identifier getTextureResource(GasCanisterItem animatable) {
      return TEXTURE;
   }

   public Identifier getAnimationResource(GasCanisterItem animatable) {
      return null;
   }
}
