package daot;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public final class IntegrityGuard {
   private static final List<IntegrityGuard.Incompatible> INCOMPATIBLE = List.of(
      new IntegrityGuard.Incompatible("talentcards", "Talent Cards", "com.dannysaot.talentcards.TalentCardsModInit", "/assets/talentcards/sounds.json"),
      new IntegrityGuard.Incompatible("titankilltrack", "Titan Kill Tracker", "asura.titantrack.Titankilltrack", "/assets/titankilltrack/icon.png")
   );
   private static final List<String> ALLOWED_MIXIN_PACKAGES = List.of("daot.mixin.");
   private static final List<String> ALLOWED_FOREIGN_MIXINS = List.of("net.springdaot.mixin.dannys.ModCommandsMixin");
   private static final List<String> PROTECTED_CLASSES = List.of(
      "daot.BloodlineData",
      "daot.TitanPowerData",
      "daot.PowerAuthority",
      "daot.DannyAccess",
      "daot.InternalAccess",
      "daot.BladeAttackTracker",
      "daot.ModCommands"
   );
   private static volatile boolean refusalReported = false;

   private IntegrityGuard() {
   }

   private static boolean isPresent(IntegrityGuard.Incompatible mod) {
      try {
         if (FabricLoader.getInstance().isModLoaded(mod.modId())) {
            return true;
         }
      } catch (Throwable var7) {
      }

      try {
         Class<?> modList = Class.forName("net.neoforged.fml.ModList");
         Object instance = modList.getMethod("get").invoke(null);
         Object loaded = modList.getMethod("isLoaded", String.class).invoke(instance, mod.modId());
         if (Boolean.TRUE.equals(loaded)) {
            return true;
         }
      } catch (Throwable var6) {
      }

      try {
         Class.forName(mod.probeClass(), false, IntegrityGuard.class.getClassLoader());
         return true;
      } catch (Throwable var5) {
         try {
            if (IntegrityGuard.class.getResource(mod.probeResource()) != null) {
               return true;
            }
         } catch (Throwable var4) {
         }

         return false;
      }
   }

   private static List<IntegrityGuard.Incompatible> detectPresent() {
      List<IntegrityGuard.Incompatible> found = new ArrayList<>();

      for (IntegrityGuard.Incompatible mod : INCOMPATIBLE) {
         if (isPresent(mod)) {
            found.add(mod);
         }
      }

      return found;
   }

   private static String refusalMessage(List<IntegrityGuard.Incompatible> found) {
      StringBuilder names = new StringBuilder();

      for (int i = 0; i < found.size(); i++) {
         if (i > 0) {
            names.append(", ");
         }

         names.append(found.get(i).displayName()).append(" (").append(found.get(i).modId()).append(")");
      }

      return "\n============================================================\n  Danny's AoT refused to start.\n\n  Files attempting to modify the code of Danny's AoT are\n  prohibited. The following add-on(s) inject into this mod\n  and are not licensed to do so:\n\n      %s\n\n  Danny's AoT is All Rights Reserved. No permission has been\n  granted to build paid-perk or monetised integrations on top\n  of it, and selling in-game advantage derived from it also\n  breaks Mojang's EULA, which applies to every Minecraft\n  server.\n\n  Remove the add-on(s) listed above and start again — Danny's\n  AoT will then run normally. Nothing has been changed or\n  deleted in your world.\n\n  See the LICENSE file included with this mod.\n============================================================\n"
         .formatted(names);
   }

   public static void checkAndRefuse() {
      List<IntegrityGuard.Incompatible> found = detectPresent();
      if (!found.isEmpty()) {
         String message = refusalMessage(found);
         if (!refusalReported) {
            refusalReported = true;

            try {
               DannysAot.LOGGER.error(message);
            } catch (Throwable var3) {
               System.err.println(message);
            }
         }

         throw new IntegrityGuard.IncompatibleAddonException(message);
      }
   }

   public static List<String> scanForTampering() {
      Set<String> foreign = new LinkedHashSet<>();

      for (String className : PROTECTED_CLASSES) {
         try {
            Class<?> clazz = Class.forName(className, false, IntegrityGuard.class.getClassLoader());

            for (Method method : clazz.getDeclaredMethods()) {
               for (Annotation annotation : method.getDeclaredAnnotations()) {
                  String type = annotation.annotationType().getName();
                  if (type.equals("org.spongepowered.asm.mixin.transformer.meta.MixinMerged")) {
                     Object owner = annotation.annotationType().getMethod("mixin").invoke(annotation);
                     String mixinClass = String.valueOf(owner);
                     boolean allowed = ALLOWED_MIXIN_PACKAGES.stream().anyMatch(mixinClass::startsWith) || ALLOWED_FOREIGN_MIXINS.contains(mixinClass);
                     if (!allowed) {
                        foreign.add(mixinClass + " -> " + className + "#" + method.getName());
                     }
                  }
               }
            }
         } catch (Throwable var16) {
         }
      }

      return new ArrayList<>(foreign);
   }

   private static List<String> behaviouralSignals(MinecraftServer server) {
      List<String> signals = new ArrayList<>();

      try {
         ServerScoreboard scoreboard = server.getScoreboard();

         for (String objective : List.of("talentcards-patron", "talentcards-levels")) {
            if (scoreboard.getNullableObjective(objective) != null) {
               signals.add("scoreboard objective '" + objective + "'");
            }
         }
      } catch (Throwable var7) {
      }

      try {
         for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            for (String tag : player.getCommandTags()) {
               if (tag.startsWith("tc_grant_patron_hands_")) {
                  signals.add("paid-tier tag '" + tag + "' on " + player.getGameProfile().getName());
               }
            }
         }
      } catch (Throwable var6) {
      }

      return signals;
   }

   public static void onServerStarted(MinecraftServer server) {
      checkAndRefuse();

      for (String entry : scanForTampering()) {
         DannysAot.LOGGER
            .error(
               "[dannys-aot] Foreign code has been merged into a protected class: {}. Danny's AoT is All Rights Reserved and does not permit third-party modification of its power, bloodline or command systems.",
               entry
            );
      }

      for (String signal : behaviouralSignals(server)) {
         DannysAot.LOGGER
            .warn(
               "[dannys-aot] Detected a paid-perk marker: {}. Selling in-game advantage derived from Danny's AoT is not licensed and breaks Mojang's EULA.",
               signal
            );
      }
   }

   private record Incompatible(String modId, String displayName, String probeClass, String probeResource) {
   }

   public static class IncompatibleAddonException extends RuntimeException {
      public IncompatibleAddonException(String message) {
         super(message);
      }
   }
}
