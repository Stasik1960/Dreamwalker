package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class ColossalTitanModel extends GeoModel<ColossalTitanEntity> {
   public Identifier getModelResource(ColossalTitanEntity animatable) {
      return new Identifier("dannys-aot", "geo/colossal.geo.json");
   }

   public Identifier getTextureResource(ColossalTitanEntity animatable) {
      return new Identifier("dannys-aot", "textures/entity/colossal.png");
   }

   public Identifier getAnimationResource(ColossalTitanEntity animatable) {
      return new Identifier("dannys-aot", "animations/colossal.animation.json");
   }
}
