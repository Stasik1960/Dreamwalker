package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class AbnormalTitanModel extends PureTitanGeoModel<AbnormalTitanEntity> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/abnormal.geo.json");
   private static final Identifier ANIMATION = new Identifier("dannys-aot", "animations/abnormal.animation.json");
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/entity/abnormal.png");

   public Identifier getModelResource(AbnormalTitanEntity animatable) {
      return MODEL;
   }

   public Identifier getTextureResource(AbnormalTitanEntity animatable) {
      return TEXTURE;
   }

   public Identifier getAnimationResource(AbnormalTitanEntity animatable) {
      return ANIMATION;
   }
}
