package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class AttackTitanModel extends GeoModel<AttackTitanEntity> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/attacktitan2.geo.json");
   private static final Identifier TEX_BASE = new Identifier("dannys-aot", "textures/entity/attacktitan2.png");
   private static final Identifier TEX_HARDENED_HANDS = new Identifier("dannys-aot", "textures/entity/attacktitan2_hardened_hands.png");
   private static final Identifier TEX_HARDENED_HANDSFEET = new Identifier("dannys-aot", "textures/entity/attacktitan2_hardened_handsfeet.png");
   private static final Identifier ANIMATION = new Identifier("dannys-aot", "animations/attacktitan2.animation.json");
   private static Boolean handsfeetExistsCache = null;

   public static Identifier textureFor(int hardeningMode) {
      return switch (hardeningMode) {
         case 1 -> TEX_HARDENED_HANDS;
         case 2 -> handsfeetExists() ? TEX_HARDENED_HANDSFEET : TEX_HARDENED_HANDS;
         default -> TEX_BASE;
      };
   }

   private static boolean handsfeetExists() {
      if (handsfeetExistsCache == null) {
         try {
            handsfeetExistsCache = MinecraftClient.getInstance().getResourceManager().getResource(TEX_HARDENED_HANDSFEET).isPresent();
         } catch (Throwable var1) {
            handsfeetExistsCache = false;
         }
      }

      return handsfeetExistsCache;
   }

   @Deprecated
   static boolean isVariantShifter(AttackTitanEntity animatable) {
      return animatable.isVariantShifter();
   }

   public Identifier getModelResource(AttackTitanEntity animatable) {
      return MODEL;
   }

   public Identifier getTextureResource(AttackTitanEntity animatable) {
      return textureFor(animatable.getDisplayedHardeningMode());
   }

   public Identifier getAnimationResource(AttackTitanEntity animatable) {
      return ANIMATION;
   }
}
