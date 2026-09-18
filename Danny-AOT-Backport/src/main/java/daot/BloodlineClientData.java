package daot;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;

@Environment(EnvType.CLIENT)
public class BloodlineClientData {
   private static final Map<UUID, BloodlineType> bloodlines = new ConcurrentHashMap<>();

   public static void set(UUID uuid, BloodlineType type) {
      bloodlines.put(uuid, type);
   }

   public static void clear(UUID uuid) {
      bloodlines.remove(uuid);
   }

   public static BloodlineType get(UUID uuid) {
      return bloodlines.get(uuid);
   }

   public static boolean isAckerman(UUID uuid) {
      return bloodlines.get(uuid) == BloodlineType.ACKERMAN;
   }

   public static boolean isMarleyan(UUID uuid) {
      return bloodlines.get(uuid) == BloodlineType.MARLEYAN;
   }

   public static boolean isRoyal(UUID uuid) {
      return bloodlines.get(uuid) == BloodlineType.ROYAL;
   }

   public static void set(BloodlineType type) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null) {
         set(mc.player.getUuid(), type);
      }
   }

   public static void clear() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null) {
         clear(mc.player.getUuid());
      }
   }

   public static BloodlineType get() {
      MinecraftClient mc = MinecraftClient.getInstance();
      return mc.player != null ? get(mc.player.getUuid()) : null;
   }

   public static boolean isAckerman() {
      MinecraftClient mc = MinecraftClient.getInstance();
      return mc.player != null && isAckerman(mc.player.getUuid());
   }

   public static boolean isMarleyan() {
      MinecraftClient mc = MinecraftClient.getInstance();
      return mc.player != null && isMarleyan(mc.player.getUuid());
   }

   public static boolean isRoyal() {
      MinecraftClient mc = MinecraftClient.getInstance();
      return mc.player != null && isRoyal(mc.player.getUuid());
   }

   public static void clearAll() {
      bloodlines.clear();
   }
}
