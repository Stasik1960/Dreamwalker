package daot;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ServerHookTracker {
   private static final Map<UUID, ServerHookTracker.HookState> hookStates = new ConcurrentHashMap<>();
   private static final Map<UUID, Integer> dismountFallImmunity = new ConcurrentHashMap<>();

   public static void updateHookState(UUID playerUUID, boolean leftActive, boolean rightActive) {
      if (!leftActive && !rightActive) {
         hookStates.remove(playerUUID);
      } else {
         hookStates.put(playerUUID, new ServerHookTracker.HookState(leftActive, rightActive));
      }
   }

   public static boolean hasActiveHook(UUID playerUUID) {
      ServerHookTracker.HookState state = hookStates.get(playerUUID);
      return state != null && state.hasActiveHook();
   }

   public static void grantDismountFallImmunity(UUID playerUUID) {
      dismountFallImmunity.put(playerUUID, 0);
   }

   public static boolean consumeDismountFallImmunity(UUID playerUUID) {
      return dismountFallImmunity.remove(playerUUID) != null;
   }

   public static boolean hasDismountFallImmunity(UUID playerUUID) {
      return dismountFallImmunity.containsKey(playerUUID);
   }

   public static int tickDismountImmunity(UUID playerUUID) {
      Integer newVal = dismountFallImmunity.computeIfPresent(playerUUID, (uuid, ticks) -> ticks + 1);
      return newVal != null ? newVal : -1;
   }

   public static int getDismountImmunityTicks(UUID playerUUID) {
      return dismountFallImmunity.getOrDefault(playerUUID, -1);
   }

   public static void removePlayer(UUID playerUUID) {
      hookStates.remove(playerUUID);
      dismountFallImmunity.remove(playerUUID);
   }

   public record HookState(boolean leftActive, boolean rightActive) {
      public boolean hasActiveHook() {
         return this.leftActive || this.rightActive;
      }
   }
}
