package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class ScoutUniformModel extends GeoModel<ScoutUniformItem> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/uniform.geo.json");
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/armor/scoutuniform.png");
   boolean currentUseTitan = false;

   public Identifier getModelResource(ScoutUniformItem animatable) {
      return this.currentUseTitan ? UniformSkin.TITAN_MODEL : MODEL;
   }

   public Identifier getTextureResource(ScoutUniformItem animatable) {
      return this.currentUseTitan ? UniformSkin.TITAN_TEXTURE : TEXTURE;
   }

   public Identifier getAnimationResource(ScoutUniformItem animatable) {
      return null;
   }
}
