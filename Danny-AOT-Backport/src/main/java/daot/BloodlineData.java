package daot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

public class BloodlineData extends PersistentState {
   private static final String DATA_NAME = "dannys_aot_bloodlines";
   private static final int SCHEMA_VERSION = 2;
   private final Map<UUID, BloodlineType> playerBloodlines = new HashMap<>();
   private final Map<UUID, BloodlineType> playerPowers = new HashMap<>();
   private final Map<UUID, GrantProvenance> bloodlineProvenance = new HashMap<>();
   private final Map<UUID, GrantProvenance> powerProvenance = new HashMap<>();

   public BloodlineData() {
   }

   public BloodlineData(NbtCompound tag) {
      NbtList list = tag.getList("bloodlines", 10);

      for (int i = 0; i < list.size(); i++) {
         NbtCompound entry = list.getCompound(i);
         UUID uuid = entry.getUuid("uuid");
         int ordinal = entry.getInt("type");
         BloodlineType type = BloodlineType.fromOrdinal(ordinal);
         if (type != null) {
            if (type.isPower()) {
               this.playerPowers.put(uuid, type);
            } else {
               this.playerBloodlines.put(uuid, type);
            }
         }
      }

      NbtList powers = tag.getList("powers", 10);

      for (int ix = 0; ix < powers.size(); ix++) {
         NbtCompound entry = powers.getCompound(ix);
         UUID uuid = entry.getUuid("uuid");
         BloodlineType type = BloodlineType.fromOrdinal(entry.getInt("type"));
         if (type != null && type.isPower()) {
            this.playerPowers.put(uuid, type);
         }
      }

      loadProvenance(tag, "bloodline_provenance", this.bloodlineProvenance);
      loadProvenance(tag, "power_provenance", this.powerProvenance);
      if (tag.getInt("schemaVersion") < 2) {
         for (UUID uuid : this.playerBloodlines.keySet()) {
            this.bloodlineProvenance.putIfAbsent(uuid, GrantProvenance.migrated());
         }

         for (UUID uuid : this.playerPowers.keySet()) {
            this.powerProvenance.putIfAbsent(uuid, GrantProvenance.migrated());
         }
      }
   }

   private static void loadProvenance(NbtCompound tag, String key, Map<UUID, GrantProvenance> into) {
      NbtList list = tag.getList(key, 10);

      for (int i = 0; i < list.size(); i++) {
         NbtCompound entry = list.getCompound(i);
         if (entry.containsUuid("uuid")) {
            into.put(entry.getUuid("uuid"), GrantProvenance.load(entry.getCompound("grant")));
         }
      }
   }

   private static void saveProvenance(NbtCompound tag, String key, Map<UUID, GrantProvenance> from) {
      NbtList list = new NbtList();

      for (Entry<UUID, GrantProvenance> entry : from.entrySet()) {
         NbtCompound entryTag = new NbtCompound();
         entryTag.putUuid("uuid", entry.getKey());
         entryTag.put("grant", entry.getValue().save());
         list.add(entryTag);
      }

      tag.put(key, list);
   }

   @Override
   public NbtCompound writeNbt(NbtCompound nbt) {
      nbt.putInt("schemaVersion", 2);
      NbtList list = new NbtList();

      for (Entry<UUID, BloodlineType> entry : this.playerBloodlines.entrySet()) {
         NbtCompound entryTag = new NbtCompound();
         entryTag.putUuid("uuid", entry.getKey());
         entryTag.putInt("type", entry.getValue().ordinal());
         list.add(entryTag);
      }

      nbt.put("bloodlines", list);
      NbtList powers = new NbtList();

      for (Entry<UUID, BloodlineType> entry : this.playerPowers.entrySet()) {
         NbtCompound entryTag = new NbtCompound();
         entryTag.putUuid("uuid", entry.getKey());
         entryTag.putInt("type", entry.getValue().ordinal());
         powers.add(entryTag);
      }

      nbt.put("powers", powers);
      saveProvenance(nbt, "bloodline_provenance", this.bloodlineProvenance);
      saveProvenance(nbt, "power_provenance", this.powerProvenance);
      return nbt;
   }

   private BloodlineType permittedPower(UUID playerUUID) {
      BloodlineType power = this.playerPowers.get(playerUUID);
      if (power == null) {
         return null;
      } else {
         return DannyAccess.mayHoldDannyPower(playerUUID) ? power : null;
      }
   }

   boolean hasUnauthorizedPower(UUID playerUUID) {
      return this.playerPowers.get(playerUUID) != null && !DannyAccess.mayHoldDannyPower(playerUUID);
   }

   public BloodlineType getBloodline(UUID playerUUID) {
      BloodlineType power = this.permittedPower(playerUUID);
      return power != null ? power : this.playerBloodlines.get(playerUUID);
   }

   Map<UUID, BloodlineType> snapshotEffective() {
      Map<UUID, BloodlineType> snapshot = new HashMap<>(this.playerBloodlines);

      for (Entry<UUID, BloodlineType> entry : this.playerPowers.entrySet()) {
         if (DannyAccess.mayHoldDannyPower(entry.getKey())) {
            snapshot.put(entry.getKey(), entry.getValue());
         }
      }

      return snapshot;
   }

   public BloodlineType getRealBloodline(UUID playerUUID) {
      return this.playerBloodlines.get(playerUUID);
   }

   public BloodlineType getPower(UUID playerUUID) {
      return this.permittedPower(playerUUID);
   }

   public GrantProvenance getBloodlineProvenance(UUID playerUUID) {
      return this.bloodlineProvenance.get(playerUUID);
   }

   public GrantProvenance getPowerProvenance(UUID playerUUID) {
      return this.powerProvenance.get(playerUUID);
   }

   public void setBloodline(UUID playerUUID, BloodlineType type) {
      this.setBloodline(playerUUID, type, null);
   }

   public void setBloodline(UUID playerUUID, BloodlineType type, GrantProvenance provenance) {
      if (InternalAccess.verify("BloodlineData.setBloodline")) {
         if (type != null && type.isPower()) {
            this.setPower(playerUUID, type, provenance);
         } else {
            this.playerBloodlines.put(playerUUID, type);
            this.bloodlineProvenance.put(playerUUID, provenance != null ? provenance : GrantProvenance.unknown(null));
            this.markDirty();
         }
      }
   }

   public void setPower(UUID playerUUID, BloodlineType power) {
      this.setPower(playerUUID, power, null);
   }

   public void setPower(UUID playerUUID, BloodlineType power, GrantProvenance provenance) {
      if (InternalAccess.verify("BloodlineData.setPower")) {
         this.playerPowers.put(playerUUID, power);
         this.powerProvenance.put(playerUUID, provenance != null ? provenance : GrantProvenance.unknown(null));
         this.markDirty();
      }
   }

   public void removeBloodline(UUID playerUUID) {
      if (InternalAccess.verify("BloodlineData.removeBloodline")) {
         this.playerBloodlines.remove(playerUUID);
         this.bloodlineProvenance.remove(playerUUID);
         this.markDirty();
      }
   }

   public void removePower(UUID playerUUID) {
      if (InternalAccess.verify("BloodlineData.removePower")) {
         this.playerPowers.remove(playerUUID);
         this.powerProvenance.remove(playerUUID);
         this.markDirty();
      }
   }

   public boolean hasBloodline(UUID playerUUID) {
      return this.playerBloodlines.containsKey(playerUUID) || this.playerPowers.containsKey(playerUUID);
   }

   public static BloodlineData get(ServerWorld level) {
      return get(level.getServer());
   }

   public static BloodlineData get(MinecraftServer server) {
      return server.getOverworld()
         .getPersistentStateManager()
         .getOrCreate(tag -> new BloodlineData(tag), BloodlineData::new, "dannys_aot_bloodlines");
   }
}
