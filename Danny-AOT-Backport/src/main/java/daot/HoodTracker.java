package daot;

import daot.network.HoodSyncPayload;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import daot.compat.network.ServerPlayNetworking;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class HoodTracker {
   public static final String HOOD_UP_TAG = "hood_up";
   private static final Map<UUID, Boolean> clientHoodStates = new ConcurrentHashMap<>();

   public static boolean isHoodUp(PlayerEntity player) {
      return player.getCommandTags().contains("hood_up");
   }

   public static void toggleHood(ServerPlayerEntity player) {
      boolean nowUp;
      if (player.getCommandTags().contains("hood_up")) {
         player.removeScoreboardTag("hood_up");
         nowUp = false;
      } else {
         player.addCommandTag("hood_up");
         nowUp = true;
      }

      float pitch = nowUp ? 1.0F : 0.8F;
      player.getWorld()
         .playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, SoundCategory.PLAYERS, 1.0F, pitch);
      if (nowUp) {
         player.sendMessage(Text.literal("Hood: Up").formatted(Formatting.GRAY), true);
      } else {
         player.sendMessage(Text.literal("Hood: Down").formatted(Formatting.GRAY), true);
      }

      ServerWorld level = player.getServerWorld();
      HoodSyncPayload syncPayload = new HoodSyncPayload(player.getUuid(), nowUp);

      for (ServerPlayerEntity other : PlayerLookup.world(level)) {
         ServerPlayNetworking.send(other, syncPayload);
      }
   }

   public static void syncToPlayer(ServerPlayerEntity joiningPlayer) {
      if (joiningPlayer.getCommandTags().contains("hood_up")) {
         ServerPlayNetworking.send(joiningPlayer, new HoodSyncPayload(joiningPlayer.getUuid(), true));
      }

      ServerWorld level = joiningPlayer.getServerWorld();

      for (ServerPlayerEntity other : PlayerLookup.world(level)) {
         if (other != joiningPlayer && other.getCommandTags().contains("hood_up")) {
            ServerPlayNetworking.send(joiningPlayer, new HoodSyncPayload(other.getUuid(), true));
         }
      }
   }

   public static boolean isHoodUpClient(UUID playerUuid) {
      return clientHoodStates.getOrDefault(playerUuid, false);
   }

   public static void setClientHoodState(UUID playerUuid, boolean hoodUp) {
      if (hoodUp) {
         clientHoodStates.put(playerUuid, true);
      } else {
         clientHoodStates.remove(playerUuid);
      }
   }

   public static void removePlayer(UUID playerUuid) {
      clientHoodStates.remove(playerUuid);
   }

   public static void clearClientStates() {
      clientHoodStates.clear();
   }

   public static void registerServerTick() {
      ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
         for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.getCommandTags().contains("hood_up") && !(player.getEquippedStack(EquipmentSlot.HEAD).getItem() instanceof CloakItem)) {
               player.removeScoreboardTag("hood_up");
               ServerWorld level = player.getServerWorld();
               HoodSyncPayload syncPayload = new HoodSyncPayload(player.getUuid(), false);

               for (ServerPlayerEntity other : PlayerLookup.world(level)) {
                  ServerPlayNetworking.send(other, syncPayload);
               }
            }
         }
      });
   }
}
