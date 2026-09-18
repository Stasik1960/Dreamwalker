package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class BeastTitanModel extends GeoModel<BeastTitanEntity> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/beast_titan.geo.json");
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/entity/beasttitan.png");

   public Identifier getModelResource(BeastTitanEntity animatable) {
      return MODEL;
   }

   public Identifier getTextureResource(BeastTitanEntity animatable) {
      return TEXTURE;
   }

   public Identifier getAnimationResource(BeastTitanEntity animatable) {
      return new Identifier("dannys-aot", "animations/beasttitan.animation.json");
   }
}
