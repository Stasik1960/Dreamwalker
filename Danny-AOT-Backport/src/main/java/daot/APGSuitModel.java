package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class APGSuitModel extends GeoModel<APGSuitItem> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/apg_suit.geo.json");
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/armor/apg_suit.png");

   public Identifier getModelResource(APGSuitItem animatable) {
      return MODEL;
   }

   public Identifier getTextureResource(APGSuitItem animatable) {
      return TEXTURE;
   }

   public Identifier getAnimationResource(APGSuitItem animatable) {
      return null;
   }
}
