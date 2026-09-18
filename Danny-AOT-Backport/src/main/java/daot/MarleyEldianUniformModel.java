package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class MarleyEldianUniformModel extends GeoModel<MarleyEldianUniformItem> {
   private static final Identifier HELMET_MODEL = new Identifier("dannys-aot", "geo/marley_eldian_uniform_helmet.geo.json");
   private static final Identifier CHESTPLATE_MODEL = new Identifier("dannys-aot", "geo/marley_eldian_uniform_chestplate.geo.json");
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/armor/marley_eldian_uniform.png");

   public Identifier getModelResource(MarleyEldianUniformItem animatable) {
      return switch (animatable.getType()) {
         case HELMET -> HELMET_MODEL;
         default -> CHESTPLATE_MODEL;
      };
   }

   public Identifier getTextureResource(MarleyEldianUniformItem animatable) {
      return TEXTURE;
   }

   public Identifier getAnimationResource(MarleyEldianUniformItem animatable) {
      return null;
   }
}
