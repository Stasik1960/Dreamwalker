package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class KeyNecklaceArmorModel extends GeoModel<BasementKeyItem> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/key_necklace.geo.json");
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/armor/key_necklace.png");

   public Identifier getModelResource(BasementKeyItem animatable) {
      return MODEL;
   }

   public Identifier getTextureResource(BasementKeyItem animatable) {
      return TEXTURE;
   }

   public Identifier getAnimationResource(BasementKeyItem animatable) {
      return null;
   }
}
