package daot;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;

public final class LsoCompat {
   private static Boolean available = null;
   private static Object[] bodyParts;
   private static Method healBodyPart;
   private static Method getHealthRatio;
   private static Method getMaxHealth;
   private static Method updateBrokenHearts;

   private LsoCompat() {
   }

   public static boolean isAvailable() {
      if (available == null) {
         available = resolve();
         if (available) {
            DannysAot.LOGGER.info("Legendary Survival Overhaul detected - ogre shifters get indestructible limbs");
         }
      }

      return available;
   }

   private static boolean resolve() {
      boolean modPresent = FabricLoader.getInstance().isModLoaded("legendarysurvivaloverhaul") || FabricLoader.getInstance().isModLoaded("survivaloverhaul");
      if (!modPresent) {
         return false;
      } else {
         try {
            Class<?> util = Class.forName("sfiomn.legendarysurvivaloverhaul.api.bodydamage.BodyDamageUtil");
            Class<?> partEnum = Class.forName("sfiomn.legendarysurvivaloverhaul.api.bodydamage.BodyPartEnum");
            bodyParts = (Object[])partEnum.getMethod("values").invoke(null);
            healBodyPart = util.getMethod("healBodyPart", PlayerEntity.class, partEnum, float.class);
            getHealthRatio = util.getMethod("getHealthRatio", PlayerEntity.class, partEnum);
            getMaxHealth = util.getMethod("getMaxHealth", PlayerEntity.class, partEnum);
            updateBrokenHearts = util.getMethod("updatePlayerBrokenHeartAttribute", PlayerEntity.class);
            return true;
         } catch (Throwable var3) {
            DannysAot.LOGGER.warn("LSO is loaded but its body-damage API could not be resolved - indestructible limbs disabled: {}", var3.toString());
            return false;
         }
      }
   }

   public static void keepLimbsFull(PlayerEntity player) {
      if (isAvailable()) {
         try {
            boolean healedAny = false;

            for (Object part : bodyParts) {
               float ratio = (Float)getHealthRatio.invoke(null, player, part);
               if (ratio < 1.0F) {
                  float max = (Float)getMaxHealth.invoke(null, player, part);
                  healBodyPart.invoke(null, player, part, max);
                  healedAny = true;
               }
            }

            if (healedAny) {
               updateBrokenHearts.invoke(null, player);
            }
         } catch (Throwable var8) {
         }
      }
   }
}
