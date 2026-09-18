package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class ShifterMusclesArmorModel extends GeoModel<ShifterMusclesArmorItem> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/shifter_muscles.geo.json");
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/armor/shifter_muscles.png");

   public Identifier getModelResource(ShifterMusclesArmorItem animatable) {
      return MODEL;
   }

   public Identifier getTextureResource(ShifterMusclesArmorItem animatable) {
      return TEXTURE;
   }

   public Identifier getAnimationResource(ShifterMusclesArmorItem animatable) {
      return null;
   }
}
