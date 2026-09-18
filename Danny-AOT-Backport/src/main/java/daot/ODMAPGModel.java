package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class ODMAPGModel extends GeoModel<ODMAPGItem> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/odm_apg.geo.json");
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/armor/odm_apg.png");

   public Identifier getModelResource(ODMAPGItem animatable) {
      return MODEL;
   }

   public Identifier getTextureResource(ODMAPGItem animatable) {
      return TEXTURE;
   }

   public Identifier getAnimationResource(ODMAPGItem animatable) {
      return null;
   }
}
