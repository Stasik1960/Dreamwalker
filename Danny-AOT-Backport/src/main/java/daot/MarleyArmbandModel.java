package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class MarleyArmbandModel extends GeoModel<MarleyArmbandItem> {
   Identifier currentGeo = new Identifier("dannys-aot", "geo/marley_armband_cadet.geo.json");
   Identifier currentTexture = new Identifier("dannys-aot", "textures/armor/marley_armband_cadet.png");

   public Identifier getModelResource(MarleyArmbandItem animatable) {
      return this.currentGeo;
   }

   public Identifier getTextureResource(MarleyArmbandItem animatable) {
      return this.currentTexture;
   }

   public Identifier getAnimationResource(MarleyArmbandItem animatable) {
      return null;
   }
}
