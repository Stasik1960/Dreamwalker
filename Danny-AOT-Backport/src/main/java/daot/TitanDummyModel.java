package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class TitanDummyModel extends GeoModel<TitanDummyEntity> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/titan_dummy.geo.json");
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/block/titan_dummy.png");
   private static final Identifier ANIMATION = new Identifier("dannys-aot", "animations/titan_dummy.animation.json");

   public Identifier getModelResource(TitanDummyEntity animatable) {
      return MODEL;
   }

   public Identifier getTextureResource(TitanDummyEntity animatable) {
      return TEXTURE;
   }

   public Identifier getAnimationResource(TitanDummyEntity animatable) {
      return ANIMATION;
   }
}
