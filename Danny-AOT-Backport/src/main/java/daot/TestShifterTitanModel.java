package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class TestShifterTitanModel extends GeoModel<TestShifterTitanEntity> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/jawtitan.geo.json");
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/entity/jawtitan.png");
   private static final Identifier TEXTURE_HARDENED_NAPE = new Identifier("dannys-aot", "textures/entity/jawtitan_hardened_nape.png");
   private static final Identifier ANIMATION = new Identifier("dannys-aot", "animations/jawtitan.animation.json");

   public static Identifier baseTexture() {
      return TEXTURE;
   }

   public Identifier getModelResource(TestShifterTitanEntity animatable) {
      return MODEL;
   }

   public Identifier getTextureResource(TestShifterTitanEntity animatable) {
      return animatable.isNapeHardened() ? TEXTURE_HARDENED_NAPE : TEXTURE;
   }

   public Identifier getAnimationResource(TestShifterTitanEntity animatable) {
      return ANIMATION;
   }
}
