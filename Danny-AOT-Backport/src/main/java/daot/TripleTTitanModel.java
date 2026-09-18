package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class TripleTTitanModel extends GeoModel<TripleTTitanEntity> {
   public Identifier getModelResource(TripleTTitanEntity animatable) {
      return new Identifier("dannys-aot", "geo/tung.geo.json");
   }

   public Identifier getTextureResource(TripleTTitanEntity animatable) {
      return new Identifier("dannys-aot", "textures/entity/tung.png");
   }

   public Identifier getAnimationResource(TripleTTitanEntity animatable) {
      return new Identifier("dannys-aot", "animations/tung.animation.json");
   }
}
