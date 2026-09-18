package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class FoundingTitanModel extends GeoModel<FoundingTitanEntity> {
   public Identifier getModelResource(FoundingTitanEntity animatable) {
      return new Identifier("dannys-aot", "geo/oattack.geo.json");
   }

   public Identifier getTextureResource(FoundingTitanEntity animatable) {
      return new Identifier("dannys-aot", "textures/entity/oattack.png");
   }

   public Identifier getAnimationResource(FoundingTitanEntity animatable) {
      return new Identifier("dannys-aot", "animations/attacktitan2.animation.json");
   }
}
