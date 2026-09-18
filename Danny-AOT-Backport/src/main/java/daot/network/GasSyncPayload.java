package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record GasSyncPayload(int gas) implements CustomPayload {
   public static final Id<GasSyncPayload> TYPE = new Id<>(new Identifier("dannys-aot", "gas_sync"));
   public static final PacketCodec<PacketByteBuf, GasSyncPayload> STREAM_CODEC = PacketCodec.ofStatic(GasSyncPayload::encode, GasSyncPayload::decode);

   private static void encode(PacketByteBuf buf, GasSyncPayload payload) {
      buf.writeInt(payload.gas);
   }

   private static GasSyncPayload decode(PacketByteBuf buf) {
      return new GasSyncPayload(buf.readInt());
   }

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
