package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class ODMGearModel extends GeoModel<ODMGearItem> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/odmgear.geo.json");
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/armor/odmgear.png");
   private static final Identifier ANIMATION = new Identifier("dannys-aot", "animations/odmgear.animation.json");
   private static final Identifier MODEL_HYPER = new Identifier("dannys-aot", "geo/hyper_odm.geo.json");
   private static final Identifier TEXTURE_HYPER = new Identifier("dannys-aot", "textures/armor/hyperodm.png");
   boolean currentUseHyper = false;

   public Identifier getModelResource(ODMGearItem animatable) {
      return this.currentUseHyper ? MODEL_HYPER : MODEL;
   }

   public Identifier getTextureResource(ODMGearItem animatable) {
      return this.currentUseHyper ? TEXTURE_HYPER : TEXTURE;
   }

   public Identifier getAnimationResource(ODMGearItem animatable) {
      return ANIMATION;
   }
}
