package daot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

public class YmirCurseData extends PersistentState {
   private static final String KEY = "dannysaot_ymir_curse";
   private final Map<UUID, Long> startTicks = new HashMap<>();

   public long getStartTick(UUID uuid) {
      return this.startTicks.getOrDefault(uuid, -1L);
   }

   public void setStartTick(UUID uuid, long tick) {
      this.startTicks.put(uuid, tick);
      this.markDirty();
   }

   public void clear(UUID uuid) {
      if (this.startTicks.remove(uuid) != null) {
         this.markDirty();
      }
   }

   @Override
   public NbtCompound writeNbt(NbtCompound nbt) {
      NbtList list = new NbtList();

      for (Entry<UUID, Long> e : this.startTicks.entrySet()) {
         NbtCompound entry = new NbtCompound();
         entry.putUuid("uuid", e.getKey());
         entry.putLong("startTick", e.getValue());
         list.add(entry);
      }

      nbt.put("entries", list);
      return nbt;
   }

   public static YmirCurseData load(NbtCompound tag) {
      YmirCurseData data = new YmirCurseData();
      NbtList list = tag.getList("entries", 10);

      for (int i = 0; i < list.size(); i++) {
         NbtCompound entry = list.getCompound(i);
         data.startTicks.put(entry.getUuid("uuid"), entry.getLong("startTick"));
      }

      return data;
   }

   public static YmirCurseData get(MinecraftServer server) {
      return server.getOverworld().getPersistentStateManager().getOrCreate(YmirCurseData::load, YmirCurseData::new, "dannysaot_ymir_curse");
   }
}
