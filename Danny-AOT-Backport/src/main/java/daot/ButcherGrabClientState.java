package daot;

import daot.network.ButcherGrabScrollPayload;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import daot.compat.network.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public final class ButcherGrabClientState {
   private static final Map<UUID, ButcherGrabClientState.State> STATES = new ConcurrentHashMap<>();

   private ButcherGrabClientState() {
   }

   public static void update(UUID id, boolean active, double dirX, double dirY, double dirZ, double distance) {
      if (!active) {
         STATES.remove(id);
      } else {
         Vec3d dir = new Vec3d(dirX, dirY, dirZ);
         if (dir.lengthSquared() < 1.0E-6) {
            dir = new Vec3d(0.0, 0.0, 1.0);
         }

         STATES.put(id, new ButcherGrabClientState.State(true, dir.normalize(), distance));
      }
   }

   public static ButcherGrabClientState.State get(UUID id) {
      return STATES.get(id);
   }

   public static Set<UUID> activeFirers() {
      return STATES.keySet();
   }

   public static void clear() {
      STATES.clear();
   }

   public static boolean shouldInterceptScroll() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null && mc.currentScreen == null && mc.getWindow() != null) {
         if (BloodlineClientData.get(mc.player.getUuid()) != BloodlineType.BUTCHER) {
            return false;
         } else {
            return ModEffects.isPowerDisabled(mc.player) ? false : InputUtil.isKeyPressed(mc.getWindow().getHandle(), 71);
         }
      } else {
         return false;
      }
   }

   public static void sendScroll(int notches) {
      ClientPlayNetworking.send(new ButcherGrabScrollPayload(notches));
   }

   @Environment(EnvType.CLIENT)
   public record State(boolean active, Vec3d dir, double distance) {
   }
}
