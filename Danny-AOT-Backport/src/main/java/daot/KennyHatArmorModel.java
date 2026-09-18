package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class KennyHatArmorModel extends GeoModel<KennyHatItem> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/kennyhat.geo.json");
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/armor/kennyhat.png");

   public Identifier getModelResource(KennyHatItem animatable) {
      return MODEL;
   }

   public Identifier getTextureResource(KennyHatItem animatable) {
      return TEXTURE;
   }

   public Identifier getAnimationResource(KennyHatItem animatable) {
      return null;
   }
}
