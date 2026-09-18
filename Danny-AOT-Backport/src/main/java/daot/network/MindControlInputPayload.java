package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record MindControlInputPayload(
   float yaw,
   float pitch,
   float forward,
   float strafe,
   boolean jumping,
   boolean sprinting,
   boolean shifting,
   int selectedSlot,
   boolean swing,
   double posX,
   double posY,
   double posZ,
   boolean inventoryOpen
) implements CustomPayload {
   public static final Id<MindControlInputPayload> TYPE = new Id<>(new Identifier("dannys-aot", "mind_control_input"));
   public static final PacketCodec<PacketByteBuf, MindControlInputPayload> STREAM_CODEC = PacketCodec.ofStatic(
      MindControlInputPayload::encode, MindControlInputPayload::decode
   );

   private static void encode(PacketByteBuf buf, MindControlInputPayload payload) {
      buf.writeFloat(payload.yaw);
      buf.writeFloat(payload.pitch);
      buf.writeFloat(payload.forward);
      buf.writeFloat(payload.strafe);
      buf.writeBoolean(payload.jumping);
      buf.writeBoolean(payload.sprinting);
      buf.writeBoolean(payload.shifting);
      buf.writeInt(payload.selectedSlot);
      buf.writeBoolean(payload.swing);
      buf.writeDouble(payload.posX);
      buf.writeDouble(payload.posY);
      buf.writeDouble(payload.posZ);
      buf.writeBoolean(payload.inventoryOpen);
   }

   private static MindControlInputPayload decode(PacketByteBuf buf) {
      return new MindControlInputPayload(
         buf.readFloat(),
         buf.readFloat(),
         buf.readFloat(),
         buf.readFloat(),
         buf.readBoolean(),
         buf.readBoolean(),
         buf.readBoolean(),
         buf.readInt(),
         buf.readBoolean(),
         buf.readDouble(),
         buf.readDouble(),
         buf.readDouble(),
         buf.readBoolean()
      );
   }

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
