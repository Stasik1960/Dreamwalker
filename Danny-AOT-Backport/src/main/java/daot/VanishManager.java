package daot;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.text.Text;

public class VanishManager {
   private static final Set<UUID> vanishedPlayers = new HashSet<>();
   private static int tickCounter = 0;
   public static int suppressBroadcastCounter = 0;

   public static boolean isVanished(UUID uuid) {
      return vanishedPlayers.contains(uuid);
   }

   public static void toggle(ServerPlayerEntity player) {
      if (isVanished(player.getUuid())) {
         unvanish(player);
      } else {
         vanish(player);
      }
   }

   public static void vanish(ServerPlayerEntity player) {
      vanishedPlayers.add(player.getUuid());
      MinecraftServer server = player.server;
      player.setInvisible(true);

      for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
         if (other != player) {
            other.networkHandler.sendPacket(new PlayerRemoveS2CPacket(List.of(player.getUuid())));
            other.networkHandler.sendPacket(new EntitiesDestroyS2CPacket(player.getId()));
         }
      }

      player.sendMessage(Text.literal("You are now vanished.").styled(s -> s.withColor(5635925)));
   }

   public static void unvanish(ServerPlayerEntity player) {
      vanishedPlayers.remove(player.getUuid());
      MinecraftServer server = player.server;
      player.setInvisible(false);

      for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
         if (other != player) {
            other.networkHandler.sendPacket(PlayerListS2CPacket.entryFromPlayer(List.of(player)));
         }
      }

      ServerChunkManager chunkSource = player.getServerWorld().getChunkManager();
      chunkSource.unloadEntity(player);
      chunkSource.loadEntity(player);
      player.sendMessage(Text.literal("You are no longer vanished.").styled(s -> s.withColor(16733525)));
   }

   public static void onPlayerJoin(ServerPlayerEntity joiner) {
      MinecraftServer server = joiner.server;
      if (vanishedPlayers.contains(joiner.getUuid())) {
         joiner.setInvisible(true);

         for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
            if (other != joiner) {
               other.networkHandler.sendPacket(new PlayerRemoveS2CPacket(List.of(joiner.getUuid())));
               other.networkHandler.sendPacket(new EntitiesDestroyS2CPacket(joiner.getId()));
            }
         }
      }

      for (UUID vanishedUUID : vanishedPlayers) {
         if (!vanishedUUID.equals(joiner.getUuid())) {
            ServerPlayerEntity vanished = server.getPlayerManager().getPlayer(vanishedUUID);
            if (vanished != null) {
               joiner.networkHandler.sendPacket(new PlayerRemoveS2CPacket(List.of(vanishedUUID)));
               joiner.networkHandler.sendPacket(new EntitiesDestroyS2CPacket(vanished.getId()));
            }
         }
      }
   }

   public static void onPlayerLeave(ServerPlayerEntity player) {
   }

   public static void tick(MinecraftServer server) {
      tickCounter++;
      suppressBroadcastCounter = 0;
      if (tickCounter % 20 == 0) {
         for (UUID uuid : vanishedPlayers) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null) {
               player.networkHandler.sendPacket(new OverlayMessageS2CPacket(Text.literal("You are vanished").styled(s -> s.withColor(11184810))));
            }
         }
      }
   }

   public static boolean shouldSuppressJoinLeave(UUID uuid) {
      return vanishedPlayers.contains(uuid);
   }
}
