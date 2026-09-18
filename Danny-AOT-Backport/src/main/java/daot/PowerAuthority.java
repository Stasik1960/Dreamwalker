package daot;

import daot.network.ModNetworking;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class PowerAuthority {
   private static final int SWEEP_INTERVAL_TICKS = 20;
   private static final int REPEAT_STRIP_THRESHOLD = 3;
   private static final List<String> DANNY_ONLY_TAGS = List.of("triple_t", "ogre_shifter", "titan_bloodline");
   private static final List<String> RECORD_BACKED_EXTRA_TAGS = List.of("cart_shifter");
   private static final Map<String, Long> unbackedSince = new ConcurrentHashMap<>();
   private static final Map<String, Integer> stripCount = new ConcurrentHashMap<>();
   private static final Set<UUID> reportedUnauthorizedPowers = ConcurrentHashMap.newKeySet();
   private static volatile Map<UUID, BloodlineType> bloodlineSnapshot = Map.of();

   private PowerAuthority() {
   }

   private static int graceTicks() {
      try {
         return Math.max(0, ModConfig.get().unbackedTagGraceTicks);
      } catch (Throwable var1) {
         return 100;
      }
   }

   private static boolean grandfathering() {
      try {
         return ModConfig.get().grandfatherLegacyShifterTags;
      } catch (Throwable var1) {
         return false;
      }
   }

   private static String key(UUID uuid, String tag) {
      return uuid + "|" + tag;
   }

   public static boolean hasShifter(PlayerEntity player, TitanPowerType power) {
      if (player != null && power != null) {
         if (!player.getCommandTags().contains(power.getTagName())) {
            return false;
         } else if (player.getWorld().isClient()) {
            return true;
         } else {
            MinecraftServer server = player.getServer();
            if (server == null) {
               return true;
            } else {
               try {
                  if (TitanPowerData.get(server.getOverworld()).playerHoldsPower(player.getUuid(), power)) {
                     return true;
                  }
               } catch (Throwable var4) {
                  return true;
               }

               return player instanceof ServerPlayerEntity sp && DannyAccess.mayHoldDiscreetShifter(sp)
                  ? true
                  : withinGrace(server, player.getUuid(), power.getTagName());
            }
         }
      } else {
         return false;
      }
   }

   public static boolean hasShifterTag(PlayerEntity player, String tag) {
      for (TitanPowerType power : TitanPowerType.values()) {
         if (power.getTagName().equals(tag)) {
            return hasShifter(player, power);
         }
      }

      return player != null && player.getCommandTags().contains(tag);
   }

   public static BloodlineType effectiveBloodline(UUID playerUUID) {
      return bloodlineSnapshot.get(playerUUID);
   }

   public static boolean isAckerman(UUID playerUUID) {
      return bloodlineSnapshot.get(playerUUID) == BloodlineType.ACKERMAN;
   }

   private static boolean withinGrace(MinecraftServer server, UUID uuid, String tag) {
      String k = key(uuid, tag);
      if (stripCount.getOrDefault(k, 0) >= 3) {
         return false;
      } else {
         long now = server.getOverworld().getTime();
         Long since = unbackedSince.putIfAbsent(k, now);
         return since == null || now - since < graceTicks();
      }
   }

   public static void sweep(MinecraftServer server) {
      for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
         enforceDannyOnlyTags(player);
      }

      if (server.getTicks() % 20 == 0) {
         try {
            BloodlineData bloodlines = BloodlineData.get(server);
            bloodlineSnapshot = bloodlines.snapshotEffective();
            reportUnauthorizedPowers(server, bloodlines);
         } catch (Throwable var18) {
         }

         TitanPowerData data;
         try {
            data = TitanPowerData.get(server.getOverworld());
         } catch (Throwable var17) {
            return;
         }

         long now = server.getOverworld().getTime();

         for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID uuid = player.getUuid();
            enforceRecordBackedExtraTags(server, player, data, now);
            if (DannyAccess.mayHoldDiscreetShifter(player)) {
               forgetShifterTracking(uuid);
            } else {
               for (TitanPowerType power : TitanPowerType.values()) {
                  String tag = power.getTagName();
                  String k = key(uuid, tag);
                  if (!player.getCommandTags().contains(tag)) {
                     unbackedSince.remove(k);
                  } else if (data.playerHoldsPower(uuid, power)) {
                     unbackedSince.remove(k);
                     stripCount.remove(k);
                  } else {
                     int strips = stripCount.getOrDefault(k, 0);
                     long since = unbackedSince.computeIfAbsent(k, x -> now);
                     boolean expired = now - since >= graceTicks();
                     if (strips >= 3 || expired) {
                        if (grandfathering()) {
                           data.addPlayerToPower(uuid, power, GrantProvenance.migrated());
                           unbackedSince.remove(k);
                           DannysAot.LOGGER
                              .info(
                                 "Adopted pre-existing '{}' tag for {} as a migrated grant (grandfatherLegacyShifterTags is on)",
                                 tag,
                                 player.getGameProfile().getName()
                              );
                        } else {
                           stripShifterTag(player, power, tag, strips);
                           unbackedSince.remove(k);
                           stripCount.merge(k, 1, Integer::sum);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static void enforceRecordBackedExtraTags(MinecraftServer server, ServerPlayerEntity player, TitanPowerData data, long now) {
      UUID uuid = player.getUuid();

      for (String tag : RECORD_BACKED_EXTRA_TAGS) {
         String k = key(uuid, tag);
         if (!player.getCommandTags().contains(tag)) {
            unbackedSince.remove(k);
         } else if (data.holdsExtraTag(uuid, tag)) {
            unbackedSince.remove(k);
            stripCount.remove(k);
         } else {
            int strips = stripCount.getOrDefault(k, 0);
            long since = unbackedSince.computeIfAbsent(k, x -> now);
            boolean expired = now - since >= graceTicks();
            if (strips >= 3 || expired) {
               player.removeScoreboardTag(tag);
               unbackedSince.remove(k);
               stripCount.merge(k, 1, Integer::sum);
               DannysAot.LOGGER
                  .warn(
                     "[dannys-aot] Removed unauthorized '{}' tag from {} — no grant record backs it. This power is only issued by /daot danny {}. (prior strips for this player/tag: {})",
                     new Object[]{tag, player.getGameProfile().getName(), "cart_shifter".equals(tag) ? "cart" : "founder", strips}
                  );
               player.sendMessage(
                  Text.literal("Your " + extraTagDisplayName(tag) + " power was removed (not issued by Danny's AoT)").formatted(Formatting.RED), false
               );
            }
         }
      }
   }

   private static String extraTagDisplayName(String tag) {
      if ("cart_shifter".equals(tag)) {
         return "Cart Titan";
      } else {
         return "founder".equals(tag) ? "Founding Titan" : tag;
      }
   }

   private static void forgetShifterTracking(UUID uuid) {
      for (TitanPowerType power : TitanPowerType.values()) {
         String k = key(uuid, power.getTagName());
         unbackedSince.remove(k);
         stripCount.remove(k);
      }
   }

   private static void reportUnauthorizedPowers(MinecraftServer server, BloodlineData bloodlines) {
      if (DannyAccess.isPowerHolderListConfigured()) {
         for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (bloodlines.hasUnauthorizedPower(player.getUuid()) && reportedUnauthorizedPowers.add(player.getUuid())) {
               DannysAot.LOGGER
                  .error(
                     "[dannys-aot] {} has a stored Speakor power but is not on the permitted-holder list, so it confers nothing. No command issued this — the saved data was most likely edited directly.",
                     player.getGameProfile().getName()
                  );
            }
         }
      }
   }

   private static void stripShifterTag(ServerPlayerEntity player, TitanPowerType power, String tag, int priorStrips) {
      player.removeScoreboardTag(tag);
      DannysAot.LOGGER
         .warn(
            "[dannys-aot] Removed unauthorized '{}' tag from {} — no grant record backs it. Shifter powers must be issued through /daot shifter set so they are recorded. (prior strips for this player/tag: {})",
            new Object[]{tag, player.getGameProfile().getName(), priorStrips}
         );
      player.sendMessage(Text.literal("Your " + power.getDisplayName() + " power was removed (not issued by Danny's AoT)").formatted(Formatting.RED), false);
   }

   private static void enforceDannyOnlyTags(ServerPlayerEntity player) {
      boolean removed = false;

      for (String tag : DANNY_ONLY_TAGS) {
         if (player.getCommandTags().contains(tag) && !DannyAccess.mayHoldTag(player, tag)) {
            player.removeScoreboardTag(tag);
            removed = true;
            if ("ogre_shifter".equals(tag)) {
               ModNetworking.removeOgreShifterAttributes(player);
            } else if ("titan_bloodline".equals(tag)) {
               ModNetworking.removeTitanBloodlineAttributes(player);
               ModNetworking.sendTitanBloodlineToPlayer(player);
            }
         }
      }

      if (removed) {
         DannysAot.LOGGER.info("Removed hidden-shifter tag(s) from {} (not an authorized account)", player.getGameProfile().getName());
      }
   }

   public static void onPlayerDisconnect(UUID uuid) {
      String prefix = uuid + "|";
      unbackedSince.keySet().removeIf(k -> k.startsWith(prefix));
   }

   public static void reset() {
      unbackedSince.clear();
      stripCount.clear();
      reportedUnauthorizedPowers.clear();
      bloodlineSnapshot = Map.of();
   }

   public static void refreshBloodlineSnapshot(MinecraftServer server) {
      if (server != null) {
         try {
            bloodlineSnapshot = BloodlineData.get(server).snapshotEffective();
         } catch (Throwable var2) {
         }
      }
   }

   static void primeSnapshot(Map<UUID, BloodlineType> snapshot) {
      bloodlineSnapshot = new HashMap<>(snapshot);
   }
}
