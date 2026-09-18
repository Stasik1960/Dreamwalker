package daot;

import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.loading.object.BakedAnimations;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class OgreShifterTitanModel extends GeoModel<OgreShifterTitanEntity> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/ogre.geo.json");
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/entity/ogre.png");
   private static final Identifier ANIMATION = new Identifier("dannys-aot", "animations/attacktitan2.animation.json");
   private static final Identifier OGRE_ANIMATION = new Identifier("dannys-aot", "animations/ogre.animation.json");
   private static final Set<String> OGRE_OWNED = Set.of("ground_smash1", "ground_smash2", "kick_attack", "uppercut_l", "uppercut_r", "wire_yank", "protect");

   public Identifier getModelResource(OgreShifterTitanEntity animatable) {
      return MODEL;
   }

   public Identifier getTextureResource(OgreShifterTitanEntity animatable) {
      return TEXTURE;
   }

   public Identifier getAnimationResource(OgreShifterTitanEntity animatable) {
      return ANIMATION;
   }

   public Identifier[] getAnimationResourceFallbacks(OgreShifterTitanEntity animatable) {
      return new Identifier[]{OGRE_ANIMATION};
   }

   public Animation getAnimation(OgreShifterTitanEntity animatable, String name) {
      if (OGRE_OWNED.contains(name)) {
         BakedAnimations baked = (BakedAnimations)GeckoLibCache.getBakedAnimations().get(OGRE_ANIMATION);
         Animation own = baked != null ? baked.getAnimation(name) : null;
         if (own != null) {
            return own;
         }
      }

      return super.getAnimation(animatable, name);
   }
}
