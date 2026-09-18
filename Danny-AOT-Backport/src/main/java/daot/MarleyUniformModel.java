package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class MarleyUniformModel extends GeoModel<MarleyUniformItem> {
   private static final Identifier HELMET_MODEL = new Identifier("dannys-aot", "geo/marley_uniform_helmet.geo.json");
   private static final Identifier CHESTPLATE_MODEL = new Identifier("dannys-aot", "geo/marley_uniform_chestplate.geo.json");
   private static final Identifier LEGGINGS_MODEL = new Identifier("dannys-aot", "geo/marley_uniform_leggings.geo.json");
   private static final Identifier BOOTS_MODEL = new Identifier("dannys-aot", "geo/marley_uniform_boots.geo.json");
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/armor/marley_uniform.png");

   public Identifier getModelResource(MarleyUniformItem animatable) {
      return switch (animatable.getType()) {
         case HELMET -> HELMET_MODEL;
         case CHESTPLATE -> CHESTPLATE_MODEL;
         case LEGGINGS -> LEGGINGS_MODEL;
         case BOOTS -> BOOTS_MODEL;
         default -> CHESTPLATE_MODEL;
      };
   }

   public Identifier getTextureResource(MarleyUniformItem animatable) {
      return TEXTURE;
   }

   public Identifier getAnimationResource(MarleyUniformItem animatable) {
      return null;
   }
}
