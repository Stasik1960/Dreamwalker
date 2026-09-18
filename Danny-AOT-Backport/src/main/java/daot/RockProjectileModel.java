package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class RockProjectileModel extends GeoModel<RockProjectileEntity> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/rock.geo.json");
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/entity/rock.png");
   private static final Identifier ANIMATION = new Identifier("dannys-aot", "animations/rock.animation.json");

   public Identifier getModelResource(RockProjectileEntity entity) {
      return MODEL;
   }

   public Identifier getTextureResource(RockProjectileEntity entity) {
      return TEXTURE;
   }

   public Identifier getAnimationResource(RockProjectileEntity entity) {
      return ANIMATION;
   }
}
