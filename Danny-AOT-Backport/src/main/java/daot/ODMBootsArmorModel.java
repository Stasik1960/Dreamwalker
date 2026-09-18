package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class ODMBootsArmorModel extends GeoModel<ODMBootsItem> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/odm_boots.geo.json");
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/armor/odm_boots.png");
   private static final Identifier MODEL_TITAN = new Identifier("dannys-aot", "geo/odm_boots_titan.geo.json");
   private static final Identifier TEXTURE_TITAN = new Identifier("dannys-aot", "textures/armor/odm_boots_titan.png");
   boolean currentUseTitan = false;

   public Identifier getModelResource(ODMBootsItem animatable) {
      return this.currentUseTitan ? MODEL_TITAN : MODEL;
   }

   public Identifier getTextureResource(ODMBootsItem animatable) {
      return this.currentUseTitan ? TEXTURE_TITAN : TEXTURE;
   }

   public Identifier getAnimationResource(ODMBootsItem animatable) {
      return null;
   }
}
