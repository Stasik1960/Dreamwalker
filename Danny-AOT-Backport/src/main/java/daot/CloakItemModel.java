package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class CloakItemModel extends GeoModel<CloakItem> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/cloak_hood_item.geo.json");
   static final Identifier TEXTURE_BLACK = new Identifier("dannys-aot", "textures/armor/aot_hood_black.png");
   static final Identifier TEXTURE_GREEN = new Identifier("dannys-aot", "textures/armor/aot_hood_green.png");
   boolean isBlackCloak = true;

   public Identifier getModelResource(CloakItem animatable) {
      return MODEL;
   }

   public Identifier getTextureResource(CloakItem animatable) {
      return this.isBlackCloak ? TEXTURE_BLACK : TEXTURE_GREEN;
   }

   public Identifier getAnimationResource(CloakItem animatable) {
      return null;
   }
}
