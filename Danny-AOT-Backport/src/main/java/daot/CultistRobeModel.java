package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class CultistRobeModel extends GeoModel<CultistRobeItem> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/cultist_robes.geo.json");
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/armor/cultist_robes.png");

   public Identifier getModelResource(CultistRobeItem animatable) {
      return MODEL;
   }

   public Identifier getTextureResource(CultistRobeItem animatable) {
      return TEXTURE;
   }

   public Identifier getAnimationResource(CultistRobeItem animatable) {
      return null;
   }
}
