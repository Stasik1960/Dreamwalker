package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class OgreTitanModel extends PureTitanGeoModel<OgreTitanEntity> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/ogre.geo.json");
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/entity/ogre.png");
   private static final Identifier ANIMATION = new Identifier("dannys-aot", "animations/ogre.animation.json");

   public Identifier getModelResource(OgreTitanEntity animatable) {
      return MODEL;
   }

   public Identifier getTextureResource(OgreTitanEntity animatable) {
      return TEXTURE;
   }

   public Identifier getAnimationResource(OgreTitanEntity animatable) {
      return ANIMATION;
   }
}
