package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class WarhammerTitanModel extends GeoModel<WarhammerTitanEntity> {
   public Identifier getModelResource(WarhammerTitanEntity animatable) {
      return new Identifier("dannys-aot", "geo/warhammertitan.geo.json");
   }

   public Identifier getTextureResource(WarhammerTitanEntity animatable) {
      return new Identifier("dannys-aot", "textures/entity/warhammertitan.png");
   }

   public Identifier getAnimationResource(WarhammerTitanEntity animatable) {
      return new Identifier("dannys-aot", "animations/warhammertitan.animation.json");
   }
}
