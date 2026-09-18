package daot.network;

import net.minecraft.network.PacketByteBuf;
import daot.compat.network.PacketCodec;
import daot.compat.network.CustomPayload;
import daot.compat.network.CustomPayload.Id;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public record ODMHookSyncPayload(
   int playerId,
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
   boolean isBoosting
) implements CustomPayload {
   public static final Id<ODMHookSyncPayload> TYPE = new Id<>(new Identifier("dannys-aot", "odm_hook_sync"));
   public static final PacketCodec<PacketByteBuf, ODMHookSyncPayload> STREAM_CODEC = new PacketCodec<PacketByteBuf, ODMHookSyncPayload>() {
      public ODMHookSyncPayload decode(PacketByteBuf buf) {
         int playerId = buf.readVarInt();
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
         return new ODMHookSyncPayload(
            playerId,
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
            isBoosting
         );
      }

      public void encode(PacketByteBuf buf, ODMHookSyncPayload payload) {
         buf.writeVarInt(payload.playerId);
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
      }
   };

   @Override
   public Id<? extends CustomPayload> getId() {
      return TYPE;
   }

   public Vec3d getLeftPosition() {
      return new Vec3d(this.leftX, this.leftY, this.leftZ);
   }

   public Vec3d getLeftStartPosition() {
      return new Vec3d(this.leftStartX, this.leftStartY, this.leftStartZ);
   }

   public Vec3d getRightPosition() {
      return new Vec3d(this.rightX, this.rightY, this.rightZ);
   }

   public Vec3d getRightStartPosition() {
      return new Vec3d(this.rightStartX, this.rightStartY, this.rightStartZ);
   }
}
