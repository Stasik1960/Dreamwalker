package daot.world;

import daot.DannysAot;
import daot.MCACompat;
import daot.ModConfig;
import daot.TitanPowerData;
import daot.TitanPowerHelper;
import daot.TitanPowerType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.PersistentState;

public class VillagePopulator {
   private static final int VILLAGERS_PER_VILLAGE = 50;
   private static final int CHECK_INTERVAL = 20;
   private static int tickCounter = 0;

   public static void register() {
      ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
         tickCounter++;
         if (tickCounter >= 20) {
            tickCounter = 0;
            if (ModConfig.get().enableVillagesPathsWalls) {
               if (!ParadisChunkGenerator.DMNK) {
                  for (ServerWorld level : server.getWorlds()) {
                     if (level.getRegistryKey().getValue().getPath().equals("paradis")) {
                        VillagePopulator.PopulatedVillagesData data = VillagePopulator.PopulatedVillagesData.get(level);
                        int[][] districtCenters = ParadisChunkGenerator.getAllDistrictCenters();
                        int districtRadius = ParadisChunkGenerator.getVillageRadius();
                        TitanPowerData powerData = TitanPowerData.get(level);

                        for (ServerPlayerEntity player : level.getPlayers()) {
                           for (int[] center : districtCenters) {
                              String villageKey = center[0] + "," + center[1];
                              double dist = Math.sqrt(Math.pow(player.getBlockX() - center[0], 2.0) + Math.pow(player.getBlockZ() - center[1], 2.0));
                              if (!(dist > districtRadius)) {
                                 if (!data.isPopulated(villageKey)) {
                                    populateVillage(level, center[0], center[1]);
                                    data.markPopulated(villageKey);
                                    data.markDirty();
                                    DannysAot.LOGGER.info("Populated district at ({}, {}) with {} villagers", new Object[]{center[0], center[1], 50});
                                 }

                                 if (!powerData.isVillageInitialized(villageKey)) {
                                    trySpawnPoweredVillagerForVillage(level, center[0], center[1], powerData);
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      });
      DannysAot.LOGGER.info("Registered VillagePopulator");
   }

   private static void populateVillage(ServerWorld level, int centerX, int centerZ) {
      List<int[]> housePositions = ParadisChunkGenerator.getVillageHousePositions(centerX, centerZ);
      if (housePositions != null && !housePositions.isEmpty()) {
         List<int[]> shuffled = new ArrayList<>(housePositions);
         Collections.shuffle(shuffled, new Random());
         int toSpawn = Math.min(50, shuffled.size());
         Random rand = new Random();
         int spawned = 0;

         for (int i = 0; i < toSpawn; i++) {
            int[] house = shuffled.get(i);
            int minX = house[0];
            int minZ = house[1];
            int maxX = house[2];
            int maxZ = house[3];
            int structY = house[4];
            int perimeterOffset = 3;
            int side = rand.nextInt(4);
            int spawnX;
            int spawnZ;
            switch (side) {
               case 0:
                  spawnX = minX + rand.nextInt(Math.max(1, maxX - minX + 1));
                  spawnZ = minZ - perimeterOffset;
                  break;
               case 1:
                  spawnX = minX + rand.nextInt(Math.max(1, maxX - minX + 1));
                  spawnZ = maxZ + perimeterOffset;
                  break;
               case 2:
                  spawnX = minX - perimeterOffset;
                  spawnZ = minZ + rand.nextInt(Math.max(1, maxZ - minZ + 1));
                  break;
               default:
                  spawnX = maxX + perimeterOffset;
                  spawnZ = minZ + rand.nextInt(Math.max(1, maxZ - minZ + 1));
            }

            BlockPos spawnPos = new BlockPos(spawnX, structY, spawnZ);
            if (level.isChunkLoaded(spawnX >> 4, spawnZ >> 4)) {
               VillagerEntity villager = MCACompat.createVillager(level);
               if (villager != null) {
                  villager.refreshPositionAndAngles(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, rand.nextFloat() * 360.0F, 0.0F);
                  villager.initialize(level, level.getLocalDifficulty(spawnPos), SpawnReason.STRUCTURE, null, null);
                  villager.setVillagerData(villager.getVillagerData().withProfession(VillagerProfession.NONE));
                  level.spawnEntity(villager);
                  spawned++;
               }
            }
         }

         DannysAot.LOGGER.info("Spawned {} villagers at village ({}, {})", new Object[]{spawned, centerX, centerZ});
      } else {
         DannysAot.LOGGER.warn("No house positions found for village at ({}, {}), trying fallback", centerX, centerZ);
         populateVillageFallback(level, centerX, centerZ);
      }
   }

   private static void populateVillageFallback(ServerWorld level, int centerX, int centerZ) {
      Random rand = new Random(centerX * 341873128712L + centerZ * 132897987541L);
      int spawned = 0;
      int attempts = 0;
      int maxAttempts = 500;

      while (spawned < 50 && attempts < maxAttempts) {
         attempts++;
         double angle = rand.nextDouble() * Math.PI * 2.0;
         double dist = 30.0 + rand.nextDouble() * (ParadisChunkGenerator.getVillageRadius() - 40);
         int spawnX = centerX + (int)(Math.cos(angle) * dist);
         int spawnZ = centerZ + (int)(Math.sin(angle) * dist);
         int spawnY = 69;
         if (level.isChunkLoaded(spawnX >> 4, spawnZ >> 4)) {
            BlockPos spawnPos = new BlockPos(spawnX, spawnY, spawnZ);
            VillagerEntity villager = MCACompat.createVillager(level);
            if (villager != null) {
               villager.refreshPositionAndAngles(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, rand.nextFloat() * 360.0F, 0.0F);
               villager.initialize(level, level.getLocalDifficulty(spawnPos), SpawnReason.STRUCTURE, null, null);
               villager.setVillagerData(villager.getVillagerData().withProfession(VillagerProfession.NONE));
               level.spawnEntity(villager);
               spawned++;
            }
         }
      }
   }

   private static void trySpawnPoweredVillagerForVillage(ServerWorld level, int centerX, int centerZ, TitanPowerData powerData) {
      if (DannysAot.doVillagersSpawnWithPowers(level)) {
         String villageKey = centerX + "," + centerZ;
         List<TitanPowerType> availablePowers = new ArrayList<>();

         for (TitanPowerType power : TitanPowerType.values()) {
            if (!powerData.playerHasPower(power)) {
               availablePowers.add(power);
            }
         }

         if (availablePowers.isEmpty()) {
            powerData.markVillageInitialized(villageKey);
         } else {
            Random rand = new Random();
            List<int[]> housePositions = ParadisChunkGenerator.getVillageHousePositions(centerX, centerZ);
            VillagerEntity poweredVillager = spawnSinglePoweredVillager(level, centerX, centerZ, housePositions, rand);
            if (poweredVillager != null) {
               TitanPowerType chosenPower = availablePowers.get(rand.nextInt(availablePowers.size()));
               powerData.setPower(poweredVillager.getUuid(), chosenPower);
               TitanPowerHelper.broadcastPowerAdd(level, poweredVillager.getId(), chosenPower);
               powerData.markVillageInitialized(villageKey);
               DannysAot.LOGGER.info("Village at ({}, {}) spawned villager with {} power", new Object[]{centerX, centerZ, chosenPower.getDisplayName()});
            }
         }
      }
   }

   private static boolean anyOnlinePlayerHasTag(ServerWorld level, String tag) {
      for (ServerPlayerEntity player : level.getServer().getPlayerManager().getPlayerList()) {
         if (player.getCommandTags().contains(tag)) {
            return true;
         }
      }

      return false;
   }

   private static VillagerEntity spawnSinglePoweredVillager(ServerWorld level, int centerX, int centerZ, List<int[]> housePositions, Random rand) {
      if (housePositions != null && !housePositions.isEmpty()) {
         List<int[]> shuffled = new ArrayList<>(housePositions);
         Collections.shuffle(shuffled, rand);

         for (int[] house : shuffled) {
            int minX = house[0];
            int minZ = house[1];
            int maxX = house[2];
            int maxZ = house[3];
            int structY = house[4];
            int perimeterOffset = 3;
            int side = rand.nextInt(4);
            int spawnX;
            int spawnZ;
            switch (side) {
               case 0:
                  spawnX = minX + rand.nextInt(Math.max(1, maxX - minX + 1));
                  spawnZ = minZ - perimeterOffset;
                  break;
               case 1:
                  spawnX = minX + rand.nextInt(Math.max(1, maxX - minX + 1));
                  spawnZ = maxZ + perimeterOffset;
                  break;
               case 2:
                  spawnX = minX - perimeterOffset;
                  spawnZ = minZ + rand.nextInt(Math.max(1, maxZ - minZ + 1));
                  break;
               default:
                  spawnX = maxX + perimeterOffset;
                  spawnZ = minZ + rand.nextInt(Math.max(1, maxZ - minZ + 1));
            }

            if (level.isChunkLoaded(spawnX >> 4, spawnZ >> 4)) {
               BlockPos spawnPos = new BlockPos(spawnX, structY, spawnZ);
               VillagerEntity villager = createVillagerAt(level, spawnPos, rand);
               if (villager != null) {
                  return villager;
               }
            }
         }

         return null;
      } else {
         return spawnPoweredVillagerFallback(level, centerX, centerZ, rand);
      }
   }

   private static VillagerEntity spawnPoweredVillagerFallback(ServerWorld level, int centerX, int centerZ, Random rand) {
      for (int i = 0; i < 12; i++) {
         double angle = rand.nextDouble() * Math.PI * 2.0;
         double dist = 30.0 + rand.nextDouble() * 30.0;
         int spawnX = centerX + (int)(Math.cos(angle) * dist);
         int spawnZ = centerZ + (int)(Math.sin(angle) * dist);
         int spawnY = 69;
         if (level.isChunkLoaded(spawnX >> 4, spawnZ >> 4)) {
            BlockPos spawnPos = new BlockPos(spawnX, spawnY, spawnZ);
            VillagerEntity villager = createVillagerAt(level, spawnPos, rand);
            if (villager != null) {
               return villager;
            }
         }
      }

      return null;
   }

   private static VillagerEntity createVillagerAt(ServerWorld level, BlockPos spawnPos, Random rand) {
      VillagerEntity villager = MCACompat.createVillager(level);
      if (villager == null) {
         return null;
      } else {
         villager.refreshPositionAndAngles(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, rand.nextFloat() * 360.0F, 0.0F);
         villager.initialize(level, level.getLocalDifficulty(spawnPos), SpawnReason.STRUCTURE, null, null);
         villager.setVillagerData(villager.getVillagerData().withProfession(VillagerProfession.NONE));
         return !level.spawnEntity(villager) ? null : villager;
      }
   }

   public static class PopulatedVillagesData extends PersistentState {
      private static final String DATA_NAME = "dannys_aot_populated_villages";
      private final Set<String> populatedVillages = new HashSet<>();

      public PopulatedVillagesData() {
      }

      public PopulatedVillagesData(NbtCompound tag) {
         NbtList list = tag.getList("villages", 8);

         for (int i = 0; i < list.size(); i++) {
            this.populatedVillages.add(list.getString(i));
         }
      }

      @Override
      public NbtCompound writeNbt(NbtCompound nbt) {
         NbtList list = new NbtList();

         for (String village : this.populatedVillages) {
            list.add(NbtString.of(village));
         }

         nbt.put("villages", list);
         return nbt;
      }

      public boolean isPopulated(String villageKey) {
         return this.populatedVillages.contains(villageKey);
      }

      public void markPopulated(String villageKey) {
         this.populatedVillages.add(villageKey);
      }

      public static VillagePopulator.PopulatedVillagesData get(ServerWorld level) {
         return level.getPersistentStateManager()
            .getOrCreate(
               tag -> new VillagePopulator.PopulatedVillagesData(tag), () -> new VillagePopulator.PopulatedVillagesData(),
               "dannys_aot_populated_villages"
            );
      }
   }
}
