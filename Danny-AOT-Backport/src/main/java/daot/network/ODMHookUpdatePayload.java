package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;

public record ODMHookUpdatePayload(
   boolean leftActive,
   boolean leftExtending,
   boolean leftRetracting,
   double leftX,
   double leftY,
   double leftZ,
   double leftStartX,
   double leftStartY,
   double leftStartZ,
   boolean rightActive,
   boolean rightExtending,
   boolean rightRetracting,
   double rightX,
   double rightY,
   double rightZ,
   double rightStartX,
   double rightStartY,
   double rightStartZ,
   boolean isBoosting,
   int leftHookedEntityId,
   int rightHookedEntityId
) implements CustomPayload {
   public static final Id<ODMHookUpdatePayload> TYPE = new Id<>(new Identifier("dannys-aot", "odm_hook_update"));
   public static final PacketCodec<PacketByteBuf, ODMHookUpdatePayload> STREAM_CODEC = new PacketCodec<PacketByteBuf, ODMHookUpdatePayload>() {
      public ODMHookUpdatePayload decode(PacketByteBuf buf) {
         boolean leftActive = buf.readBoolean();
         boolean leftExtending = buf.readBoolean();
         boolean leftRetracting = buf.readBoolean();
         double leftX = buf.readDouble();
         double leftY = buf.readDouble();
         double leftZ = buf.readDouble();
         double leftStartX = buf.readDouble();
         double leftStartY = buf.readDouble();
         double leftStartZ = buf.readDouble();
         boolean rightActive = buf.readBoolean();
         boolean rightExtending = buf.readBoolean();
         boolean rightRetracting = buf.readBoolean();
         double rightX = buf.readDouble();
         double rightY = buf.readDouble();
         double rightZ = buf.readDouble();
         double rightStartX = buf.readDouble();
         double rightStartY = buf.readDouble();
         double rightStartZ = buf.readDouble();
         boolean isBoosting = buf.readBoolean();
         int leftHookedEntityId = buf.readInt();
         int rightHookedEntityId = buf.readInt();
         return new ODMHookUpdatePayload(
            leftActive,
            leftExtending,
            leftRetracting,
            leftX,
            leftY,
            leftZ,
            leftStartX,
            leftStartY,
            leftStartZ,
            rightActive,
            rightExtending,
            rightRetracting,
            rightX,
            rightY,
            rightZ,
            rightStartX,
            rightStartY,
            rightStartZ,
            isBoosting,
            leftHookedEntityId,
            rightHookedEntityId
         );
      }

      public void encode(PacketByteBuf buf, ODMHookUpdatePayload payload) {
         buf.writeBoolean(payload.leftActive);
         buf.writeBoolean(payload.leftExtending);
         buf.writeBoolean(payload.leftRetracting);
         buf.writeDouble(payload.leftX);
         buf.writeDouble(payload.leftY);
         buf.writeDouble(payload.leftZ);
         buf.writeDouble(payload.leftStartX);
         buf.writeDouble(payload.leftStartY);
         buf.writeDouble(payload.leftStartZ);
         buf.writeBoolean(payload.rightActive);
         buf.writeBoolean(payload.rightExtending);
         buf.writeBoolean(payload.rightRetracting);
         buf.writeDouble(payload.rightX);
         buf.writeDouble(payload.rightY);
         buf.writeDouble(payload.rightZ);
         buf.writeDouble(payload.rightStartX);
         buf.writeDouble(payload.rightStartY);
         buf.writeDouble(payload.rightStartZ);
         buf.writeBoolean(payload.isBoosting);
         buf.writeInt(payload.leftHookedEntityId);
         buf.writeInt(payload.rightHookedEntityId);
      }
   };

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }
}
