package daot.network;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record PoweredVillagerSyncPayload(Map<Integer, Integer> poweredVillagers, boolean fullSync) implements CustomPayload {
   public static final Id<PoweredVillagerSyncPayload> TYPE = new Id<>(new Identifier("dannys-aot", "powered_villager_sync"));
   public static final PacketCodec<PacketByteBuf, PoweredVillagerSyncPayload> STREAM_CODEC = new PacketCodec<PacketByteBuf, PoweredVillagerSyncPayload>() {
      public PoweredVillagerSyncPayload decode(PacketByteBuf buf) {
         boolean fullSync = buf.readBoolean();
         int size = buf.readVarInt();
         Map<Integer, Integer> map = new HashMap<>();

         for (int i = 0; i < size; i++) {
            int entityId = buf.readVarInt();
            int powerOrdinal = buf.readVarInt();
            map.put(entityId, powerOrdinal);
         }

         return new PoweredVillagerSyncPayload(map, fullSync);
      }

      public void encode(PacketByteBuf buf, PoweredVillagerSyncPayload payload) {
         buf.writeBoolean(payload.fullSync);
         buf.writeVarInt(payload.poweredVillagers.size());

         for (Entry<Integer, Integer> entry : payload.poweredVillagers.entrySet()) {
            buf.writeVarInt(entry.getKey());
            buf.writeVarInt(entry.getValue());
         }
      }
   };

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }

   public static PoweredVillagerSyncPayload fullSync(Map<Integer, Integer> data) {
      return new PoweredVillagerSyncPayload(data, true);
   }

   public static PoweredVillagerSyncPayload addPower(int entityId, int powerOrdinal) {
      Map<Integer, Integer> map = new HashMap<>();
      map.put(entityId, powerOrdinal);
      return new PoweredVillagerSyncPayload(map, false);
   }

   public static PoweredVillagerSyncPayload removePower(int entityId) {
      Map<Integer, Integer> map = new HashMap<>();
      map.put(entityId, -1);
      return new PoweredVillagerSyncPayload(map, false);
   }
}
