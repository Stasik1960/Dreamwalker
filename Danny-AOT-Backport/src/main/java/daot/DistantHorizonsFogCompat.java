package daot;

import java.lang.reflect.Method;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;

@Environment(EnvType.CLIENT)
public final class DistantHorizonsFogCompat {
   private static final boolean DH_PRESENT = FabricLoader.getInstance().isModLoaded("distanthorizons");
   private static boolean resolved = false;
   private static boolean broken = false;
   private static Object renderToggle;
   private static Method getValue;
   private static Method setValue;
   private static Boolean savedValue = null;
   private static boolean suppressing = false;

   private DistantHorizonsFogCompat() {
   }

   private static Object callNoArg(Object target, String method) throws Exception {
      Method m = target.getClass().getMethod(method);
      m.setAccessible(true);
      return m.invoke(target);
   }

   private static void resolve() {
      if (!resolved && !broken && DH_PRESENT) {
         try {
            Class<?> delayed = Class.forName("com.seibel.distanthorizons.api.DhApi$Delayed");
            Object configs = delayed.getField("configs").get(null);
            if (configs == null) {
               return;
            }

            Object graphics = callNoArg(configs, "graphics");
            Object toggle = callNoArg(graphics, "quickEnableRendering");
            getValue = toggle.getClass().getMethod("getValue");
            getValue.setAccessible(true);
            setValue = toggle.getClass().getMethod("setValue", Object.class);
            setValue.setAccessible(true);
            renderToggle = toggle;
            resolved = true;
         } catch (Throwable var4) {
            broken = true;
         }
      }
   }

   public static void setSuppressed(boolean suppress) {
      if (DH_PRESENT && !broken) {
         resolve();
         if (resolved) {
            try {
               if (suppress && !suppressing) {
                  savedValue = (Boolean)getValue.invoke(renderToggle);
                  setValue.invoke(renderToggle, Boolean.FALSE);
                  suppressing = true;
               } else if (!suppress && suppressing) {
                  setValue.invoke(renderToggle, savedValue != null ? savedValue : Boolean.TRUE);
                  suppressing = false;
               }
            } catch (Throwable var2) {
               broken = true;
            }
         }
      }
   }

   public static void restore() {
      setSuppressed(false);
   }
}
