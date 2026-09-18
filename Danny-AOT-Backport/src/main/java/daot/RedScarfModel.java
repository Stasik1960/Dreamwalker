package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class RedScarfModel extends GeoModel<RedScarfItem> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/red_scarf.geo.json");
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/armor/red_scarf.png");

   public Identifier getModelResource(RedScarfItem animatable) {
      return MODEL;
   }

   public Identifier getTextureResource(RedScarfItem animatable) {
      return TEXTURE;
   }

   public Identifier getAnimationResource(RedScarfItem animatable) {
      return null;
   }
}
