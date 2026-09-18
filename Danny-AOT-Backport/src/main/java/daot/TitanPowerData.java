package daot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

public class TitanPowerData extends PersistentState {
   private static final String DATA_NAME = "dannys_aot_titan_powers";
   private static final int SCHEMA_VERSION = 2;
   private final Map<UUID, TitanPowerType> poweredVillagers = new HashMap<>();
   private final Set<String> initializedVillages = new HashSet<>();
   private static volatile boolean configDirty = false;
   private final EnumMap<TitanPowerType, Set<UUID>> playerPowers = new EnumMap<>(TitanPowerType.class);
   private final EnumMap<TitanPowerType, Map<UUID, GrantProvenance>> powerProvenance = new EnumMap<>(TitanPowerType.class);
   private final Map<String, Set<UUID>> extraTagGrants = new HashMap<>();

   public static void markConfigDirty() {
      configDirty = true;
   }

   public static boolean isConfigDirty() {
      return configDirty;
   }

   public static void clearConfigDirty() {
      configDirty = false;
   }

   public TitanPowerData() {
   }

   public TitanPowerData(NbtCompound tag) {
      NbtList villagerList = tag.getList("powered_villagers", 10);

      for (int i = 0; i < villagerList.size(); i++) {
         NbtCompound entry = villagerList.getCompound(i);
         UUID uuid = entry.getUuid("uuid");
         int powerOrdinal = entry.getInt("power");
         TitanPowerType power = TitanPowerType.fromOrdinal(powerOrdinal);
         if (power != null) {
            this.poweredVillagers.put(uuid, power);
         }
      }

      NbtList villageList = tag.getList("initialized_villages", 8);

      for (int ix = 0; ix < villageList.size(); ix++) {
         this.initializedVillages.add(villageList.getString(ix));
      }

      for (TitanPowerType power : TitanPowerType.values()) {
         String legacyKey = power.name().toLowerCase(Locale.ROOT) + "_power_player";
         String listKey = power.name().toLowerCase(Locale.ROOT) + "_power_players";
         Set<UUID> set = new HashSet<>();
         if (tag.contains(listKey)) {
            NbtList list = tag.getList(listKey, 11);

            for (int ix = 0; ix < list.size(); ix++) {
               set.add(NbtHelper.toUuid(list.get(ix)));
            }
         } else if (tag.containsUuid(legacyKey)) {
            set.add(tag.getUuid(legacyKey));
         }

         if (!set.isEmpty()) {
            this.playerPowers.put(power, set);
         }

         String provKey = power.name().toLowerCase(Locale.ROOT) + "_power_provenance";
         NbtList provList = tag.getList(provKey, 10);

         for (int ix = 0; ix < provList.size(); ix++) {
            NbtCompound entry = provList.getCompound(ix);
            if (entry.containsUuid("uuid")) {
               this.powerProvenance.computeIfAbsent(power, k -> new HashMap<>()).put(entry.getUuid("uuid"), GrantProvenance.load(entry.getCompound("grant")));
            }
         }
      }

      NbtCompound extras = tag.getCompound("extra_tag_grants");

      for (String tagName : extras.getKeys()) {
         NbtList list = extras.getList(tagName, 11);
         Set<UUID> setx = new HashSet<>();

         for (int ixx = 0; ixx < list.size(); ixx++) {
            setx.add(NbtHelper.toUuid(list.get(ixx)));
         }

         if (!setx.isEmpty()) {
            this.extraTagGrants.put(tagName, setx);
         }
      }

      if (tag.getInt("schemaVersion") < 2) {
         for (Entry<TitanPowerType, Set<UUID>> entry : this.playerPowers.entrySet()) {
            Map<UUID, GrantProvenance> prov = this.powerProvenance.computeIfAbsent(entry.getKey(), k -> new HashMap<>());

            for (UUID uuid : entry.getValue()) {
               prov.putIfAbsent(uuid, GrantProvenance.migrated());
            }
         }
      }
   }

   @Override
   public NbtCompound writeNbt(NbtCompound nbt) {
      nbt.putInt("schemaVersion", 2);
      NbtList villagerList = new NbtList();

      for (Entry<UUID, TitanPowerType> entry : this.poweredVillagers.entrySet()) {
         NbtCompound villagerTag = new NbtCompound();
         villagerTag.putUuid("uuid", entry.getKey());
         villagerTag.putInt("power", entry.getValue().ordinal());
         villagerList.add(villagerTag);
      }

      nbt.put("powered_villagers", villagerList);
      NbtList villageList = new NbtList();

      for (String village : this.initializedVillages) {
         villageList.add(NbtString.of(village));
      }

      nbt.put("initialized_villages", villageList);

      for (Entry<TitanPowerType, Set<UUID>> entry : this.playerPowers.entrySet()) {
         Set<UUID> set = entry.getValue();
         if (set != null && !set.isEmpty()) {
            String listKey = entry.getKey().name().toLowerCase(Locale.ROOT) + "_power_players";
            NbtList list = new NbtList();

            for (UUID uuid : set) {
               list.add(NbtHelper.fromUuid(uuid));
            }

            nbt.put(listKey, list);
         }
      }

      for (Entry<TitanPowerType, Map<UUID, GrantProvenance>> entryx : this.powerProvenance.entrySet()) {
         Map<UUID, GrantProvenance> prov = entryx.getValue();
         if (prov != null && !prov.isEmpty()) {
            NbtList list = new NbtList();

            for (Entry<UUID, GrantProvenance> p : prov.entrySet()) {
               NbtCompound entryTag = new NbtCompound();
               entryTag.putUuid("uuid", p.getKey());
               entryTag.put("grant", p.getValue().save());
               list.add(entryTag);
            }

            nbt.put(entryx.getKey().name().toLowerCase(Locale.ROOT) + "_power_provenance", list);
         }
      }

      NbtCompound extras = new NbtCompound();

      for (Entry<String, Set<UUID>> entryxx : this.extraTagGrants.entrySet()) {
         Set<UUID> set = entryxx.getValue();
         if (set != null && !set.isEmpty()) {
            NbtList list = new NbtList();

            for (UUID uuid : set) {
               list.add(NbtHelper.fromUuid(uuid));
            }

            extras.put(entryxx.getKey(), list);
         }
      }

      nbt.put("extra_tag_grants", extras);
      return nbt;
   }

   public boolean holdsExtraTag(UUID playerUUID, String tagName) {
      Set<UUID> set = this.extraTagGrants.get(tagName);
      return set != null && set.contains(playerUUID);
   }

   public void addExtraTagGrant(UUID playerUUID, String tagName) {
      this.extraTagGrants.computeIfAbsent(tagName, k -> new HashSet<>()).add(playerUUID);
      this.markDirty();
   }

   public void removeExtraTagGrant(UUID playerUUID, String tagName) {
      Set<UUID> set = this.extraTagGrants.get(tagName);
      if (set != null && set.remove(playerUUID)) {
         if (set.isEmpty()) {
            this.extraTagGrants.remove(tagName);
         }

         this.markDirty();
      }
   }

   public void setPower(UUID villagerUUID, TitanPowerType power) {
      this.poweredVillagers.put(villagerUUID, power);
      this.markDirty();
   }

   public TitanPowerType getPower(UUID villagerUUID) {
      return this.poweredVillagers.get(villagerUUID);
   }

   public void removePower(UUID villagerUUID) {
      this.poweredVillagers.remove(villagerUUID);
      this.markDirty();
   }

   public boolean hasPower(UUID villagerUUID) {
      return this.poweredVillagers.containsKey(villagerUUID);
   }

   public Set<UUID> getAllPoweredVillagers() {
      return Collections.unmodifiableSet(this.poweredVillagers.keySet());
   }

   public Map<UUID, TitanPowerType> getPowerMap() {
      return Collections.unmodifiableMap(this.poweredVillagers);
   }

   public boolean isVillageInitialized(String villageKey) {
      return this.initializedVillages.contains(villageKey);
   }

   public void markVillageInitialized(String villageKey) {
      this.initializedVillages.add(villageKey);
      this.markDirty();
   }

   public boolean playerHasPower(TitanPowerType power) {
      Set<UUID> set = this.playerPowers.get(power);
      return set != null && !set.isEmpty();
   }

   public UUID getPlayerWithPower(TitanPowerType power) {
      Set<UUID> set = this.playerPowers.get(power);
      return set != null && !set.isEmpty() ? set.iterator().next() : null;
   }

   public Set<UUID> getPlayersWithPower(TitanPowerType power) {
      Set<UUID> set = this.playerPowers.get(power);
      return set == null ? Collections.emptySet() : Collections.unmodifiableSet(set);
   }

   public boolean playerHoldsPower(UUID playerUUID, TitanPowerType power) {
      Set<UUID> set = this.playerPowers.get(power);
      return set != null && set.contains(playerUUID);
   }

   public void setPlayerPower(UUID playerUUID, TitanPowerType power) {
      this.setPlayerPower(playerUUID, power, null);
   }

   public void setPlayerPower(UUID playerUUID, TitanPowerType power, GrantProvenance provenance) {
      if (InternalAccess.verify("TitanPowerData.setPlayerPower")) {
         Set<UUID> set = this.playerPowers.computeIfAbsent(power, k -> new HashSet<>());
         Map<UUID, GrantProvenance> prov = this.powerProvenance.computeIfAbsent(power, k -> new HashMap<>());
         prov.keySet().retainAll(Set.of(playerUUID));
         set.clear();
         set.add(playerUUID);
         prov.put(playerUUID, provenance != null ? provenance : GrantProvenance.unknown(null));
         this.poweredVillagers.entrySet().removeIf(entry -> entry.getValue() == power);
         this.markDirty();
         markConfigDirty();
      }
   }

   public void addPlayerToPower(UUID playerUUID, TitanPowerType power) {
      this.addPlayerToPower(playerUUID, power, null);
   }

   public void addPlayerToPower(UUID playerUUID, TitanPowerType power, GrantProvenance provenance) {
      if (InternalAccess.verify("TitanPowerData.addPlayerToPower")) {
         this.playerPowers.computeIfAbsent(power, k -> new HashSet<>()).add(playerUUID);
         this.powerProvenance.computeIfAbsent(power, k -> new HashMap<>()).put(playerUUID, provenance != null ? provenance : GrantProvenance.unknown(null));
         this.poweredVillagers.entrySet().removeIf(entry -> entry.getValue() == power);
         this.markDirty();
         markConfigDirty();
      }
   }

   public GrantProvenance getPowerProvenance(UUID playerUUID, TitanPowerType power) {
      Map<UUID, GrantProvenance> prov = this.powerProvenance.get(power);
      return prov == null ? null : prov.get(playerUUID);
   }

   public void removePlayerFromPower(UUID playerUUID, TitanPowerType power) {
      Set<UUID> set = this.playerPowers.get(power);
      if (set != null) {
         if (set.remove(playerUUID)) {
            if (set.isEmpty()) {
               this.playerPowers.remove(power);
            }

            Map<UUID, GrantProvenance> prov = this.powerProvenance.get(power);
            if (prov != null) {
               prov.remove(playerUUID);
               if (prov.isEmpty()) {
                  this.powerProvenance.remove(power);
               }
            }

            this.markDirty();
            markConfigDirty();
         }
      }
   }

   public void clearPlayerPower(TitanPowerType power) {
      boolean removed = this.playerPowers.remove(power) != null;
      removed |= this.powerProvenance.remove(power) != null;
      if (removed) {
         this.markDirty();
         markConfigDirty();
      }
   }

   public void clearAllVillagerPowers() {
      this.poweredVillagers.clear();
      this.initializedVillages.clear();
      this.markDirty();
      markConfigDirty();
   }

   public List<UUID> getVillagersWithPower(TitanPowerType power) {
      List<UUID> result = new ArrayList<>();

      for (Entry<UUID, TitanPowerType> entry : this.poweredVillagers.entrySet()) {
         if (entry.getValue() == power) {
            result.add(entry.getKey());
         }
      }

      return result;
   }

   public static TitanPowerData get(ServerWorld level) {
      ServerWorld overworld = level.getServer().getOverworld();
      return overworld.getPersistentStateManager()
         .getOrCreate(tag -> new TitanPowerData(tag), () -> new TitanPowerData(), "dannys_aot_titan_powers");
   }
}
