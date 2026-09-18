package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record BladeAnimSyncPayload(int entityId, int action) implements CustomPayload {
   public static final int START_BLOCK = 0;
   public static final int STOP_BLOCK = 1;
   public static final int BLOCK_BROKEN = 2;
   public static final int SPIN_SLASH_RIGHT = 3;
   public static final int SPIN_SLASH_LEFT = 4;
   public static final int DOWN_SLASH = 5;
   public static final int UP_SLASH = 6;
   public static final int THUNDER_SPEAR_RIGHT = 7;
   public static final int THUNDER_SPEAR_LEFT = 8;
   public static final int THUNDER_SPEAR_DUAL = 9;
   public static final int RELOAD = 10;
   public static final Id<BladeAnimSyncPayload> TYPE = new Id<>(new Identifier("dannys-aot", "blade_anim_sync"));
   public static final PacketCodec<PacketByteBuf, BladeAnimSyncPayload> STREAM_CODEC = PacketCodec.ofStatic((buf, payload) -> {
      buf.writeVarInt(payload.entityId());
      buf.writeVarInt(payload.action());
   }, buf -> new BladeAnimSyncPayload(buf.readVarInt(), buf.readVarInt()));

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
