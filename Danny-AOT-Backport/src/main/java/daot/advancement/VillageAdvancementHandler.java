package daot.advancement;

import daot.DannysAot;
import daot.world.ParadisChunkGenerator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public class VillageAdvancementHandler {
   private static final Set<UUID> playersInVillage = new HashSet<>();
   private static final RegistryKey<World> PARADIS_DIMENSION = RegistryKey.of(RegistryKeys.WORLD, new Identifier("dannys-aot", "paradis"));

   public static void register() {
      ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
         if (server.getTicks() % 20 == 0) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
               checkPlayerVillageEntry(player);
            }
         }
      });
      DannysAot.LOGGER.info("Registered village advancement handler");
   }

   private static void checkPlayerVillageEntry(ServerPlayerEntity player) {
      if (!player.getWorld().getRegistryKey().equals(PARADIS_DIMENSION)) {
         playersInVillage.remove(player.getUuid());
      } else {
         int playerX = (int)player.getX();
         int playerZ = (int)player.getZ();
         boolean isInsideVillage = ParadisChunkGenerator.isPositionInsideAnyVillage(playerX, playerZ);
         if (isInsideVillage && !playersInVillage.contains(player.getUuid())) {
            playersInVillage.add(player.getUuid());
            ModCriteriaTriggers.ENTERED_PARADIS_VILLAGE.trigger(player);
            DannysAot.LOGGER.info("Player {} entered a Paradis village - triggering advancement", player.getName().getString());
         } else if (!isInsideVillage) {
            playersInVillage.remove(player.getUuid());
         }
      }
   }

   public static void onPlayerDisconnect(UUID playerId) {
      playersInVillage.remove(playerId);
   }
}
