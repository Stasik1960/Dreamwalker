package daot;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;

public final class TranslucentTracker {
   private static final Set<UUID> toggled = ConcurrentHashMap.newKeySet();

   private TranslucentTracker() {
   }

   public static void setToggled(ServerPlayerEntity player, boolean active) {
      UUID id = player.getUuid();
      if (active) {
         toggled.add(id);
         player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, -1, 0, false, false, true));
      } else {
         toggled.remove(id);
         player.removeStatusEffect(StatusEffects.INVISIBILITY);
      }
   }

   public static boolean isToggled(UUID id) {
      return toggled.contains(id);
   }

   public static void clear(ServerPlayerEntity player) {
      if (player != null) {
         toggled.remove(player.getUuid());
         player.removeStatusEffect(StatusEffects.INVISIBILITY);
      }
   }

   public static void onPlayerDisconnect(UUID id) {
      toggled.remove(id);
   }
}
