package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class TrenchCoatModel extends GeoModel<TrenchCoatItem> {
   Identifier currentGeo = new Identifier("dannys-aot", "geo/trench_coat.geo.json");
   Identifier currentTexture = new Identifier("dannys-aot", "textures/armor/trench_coat.png");

   public Identifier getModelResource(TrenchCoatItem animatable) {
      return this.currentGeo;
   }

   public Identifier getTextureResource(TrenchCoatItem animatable) {
      return this.currentTexture;
   }

   public Identifier getAnimationResource(TrenchCoatItem animatable) {
      return null;
   }
}
