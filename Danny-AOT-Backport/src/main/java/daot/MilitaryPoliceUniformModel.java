package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class MilitaryPoliceUniformModel extends GeoModel<MilitaryPoliceUniformItem> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/uniform.geo.json");
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/armor/militarypoliceuniform.png");
   boolean currentUseTitan = false;

   public Identifier getModelResource(MilitaryPoliceUniformItem animatable) {
      return this.currentUseTitan ? UniformSkin.TITAN_MODEL : MODEL;
   }

   public Identifier getTextureResource(MilitaryPoliceUniformItem animatable) {
      return this.currentUseTitan ? UniformSkin.TITAN_TEXTURE : TEXTURE;
   }

   public Identifier getAnimationResource(MilitaryPoliceUniformItem animatable) {
      return null;
   }
}
