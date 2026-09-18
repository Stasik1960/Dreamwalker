package daot;

import daot.mixin.WalkAnimationStateAccessor;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public final class AtrainSpeedAfterImageHandler {
   private static final int SNAPSHOT_INTERVAL_TICKS = 2;
   private static final int MAX_SNAPSHOTS = 5;
   private static final float PEAK_ALPHA = 0.55F;
   private static final double MIN_SPEED_SQ = 0.04;
   private static final Map<UUID, Deque<AtrainSpeedAfterImageHandler.Snapshot>> SNAPSHOTS = new HashMap<>();

   private AtrainSpeedAfterImageHandler() {
   }

   public static void register() {
      ClientTickEvents.END_CLIENT_TICK.register(AtrainSpeedAfterImageHandler::onClientTick);
      WorldRenderEvents.AFTER_ENTITIES.register(AtrainSpeedAfterImageHandler::onWorldRender);
   }

   private static void onClientTick(MinecraftClient mc) {
      ClientWorld level = mc.world;
      if (level == null) {
         SNAPSHOTS.clear();
      } else {
         long now = level.getTime();

         for (PlayerEntity p : level.getPlayers()) {
            UUID id = p.getUuid();
            boolean tracking = BloodlineClientData.get(id) == BloodlineType.ATRAIN
               && AtrainSpeedClientState.isActive(id)
               && p.isSprinting()
               && p.getVelocity().lengthSquared() >= 0.04;
            if (tracking) {
               Deque<AtrainSpeedAfterImageHandler.Snapshot> deque = SNAPSHOTS.computeIfAbsent(id, k -> new ArrayDeque<>());
               if (deque.isEmpty() || now - deque.peekFirst().tick() >= 2L) {
                  WalkAnimationStateAccessor walk = (WalkAnimationStateAccessor)p.limbAnimator;
                  deque.addFirst(
                     new AtrainSpeedAfterImageHandler.Snapshot(
                        p.prevX,
                        p.prevY,
                        p.prevZ,
                        p.prevYaw,
                        p.prevBodyYaw,
                        p.prevHeadYaw,
                        p.prevPitch,
                        walk.getPosition(),
                        walk.getSpeed(),
                        walk.getSpeedOld(),
                        now
                     )
                  );

                  while (deque.size() > 5) {
                     deque.removeLast();
                  }
               }
            }
         }

         long expireAge = 14L;
         Iterator<Entry<UUID, Deque<AtrainSpeedAfterImageHandler.Snapshot>>> it = SNAPSHOTS.entrySet().iterator();

         while (it.hasNext()) {
            Entry<UUID, Deque<AtrainSpeedAfterImageHandler.Snapshot>> entry = it.next();
            entry.getValue().removeIf(s -> now - s.tick() > expireAge);
            if (entry.getValue().isEmpty()) {
               it.remove();
            }
         }
      }
   }

   private static void onWorldRender(WorldRenderContext context) {
      if (!SNAPSHOTS.isEmpty()) {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.world != null) {
            EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
            Vec3d cameraPos = context.camera().getPos();
            float partialTick = context.tickDelta();
            long now = mc.world.getTime();
            Immediate buffers = mc.getBufferBuilders().getEntityVertexConsumers();
            boolean localInFirstPerson = mc.options.getPerspective() == Perspective.FIRST_PERSON;
            UUID localId = mc.player != null ? mc.player.getUuid() : null;

            for (Entry<UUID, Deque<AtrainSpeedAfterImageHandler.Snapshot>> entry : SNAPSHOTS.entrySet()) {
               PlayerEntity player = mc.world.getPlayerByUuid(entry.getKey());
               if (player != null && (!localInFirstPerson || !entry.getKey().equals(localId))) {
                  WalkAnimationStateAccessor walk = (WalkAnimationStateAccessor)player.limbAnimator;

                  for (AtrainSpeedAfterImageHandler.Snapshot snap : entry.getValue()) {
                     float age = ((float)now + partialTick - (float)snap.tick()) / 10.0F;
                     float alpha = 0.55F * Math.max(0.0F, 1.0F - age);
                     if (!(alpha < 0.02F)) {
                        float saveBodyRot = player.bodyYaw;
                        float saveBodyRotO = player.prevBodyYaw;
                        float saveHeadRot = player.headYaw;
                        float saveHeadRotO = player.prevHeadYaw;
                        float saveXRot = player.getPitch();
                        float saveXRotO = player.prevPitch;
                        float saveWalkPos = walk.getPosition();
                        float saveWalkSpeed = walk.getSpeed();
                        float saveWalkSpeedOld = walk.getSpeedOld();
                        player.bodyYaw = snap.yBodyRot();
                        player.prevBodyYaw = snap.yBodyRot();
                        player.headYaw = snap.yHeadRot();
                        player.prevHeadYaw = snap.yHeadRot();
                        player.setPitch(snap.xRot());
                        player.prevPitch = snap.xRot();
                        walk.setPosition(snap.walkPosition());
                        walk.setSpeed(snap.walkSpeed());
                        walk.setSpeedOld(snap.walkSpeedOld());
                        AtrainGhostState.currentAlpha = alpha;

                        try {
                           dispatcher.render(
                              player,
                              snap.x() - cameraPos.x,
                              snap.y() - cameraPos.y,
                              snap.z() - cameraPos.z,
                              snap.yRot(),
                              partialTick,
                              context.matrixStack(),
                              buffers,
                              dispatcher.getLight(player, partialTick)
                           );
                        } finally {
                           AtrainGhostState.currentAlpha = 1.0F;
                           player.bodyYaw = saveBodyRot;
                           player.prevBodyYaw = saveBodyRotO;
                           player.headYaw = saveHeadRot;
                           player.prevHeadYaw = saveHeadRotO;
                           player.setPitch(saveXRot);
                           player.prevPitch = saveXRotO;
                           walk.setPosition(saveWalkPos);
                           walk.setSpeed(saveWalkSpeed);
                           walk.setSpeedOld(saveWalkSpeedOld);
                        }
                     }
                  }
               }
            }

            buffers.draw();
         }
      }
   }

   @Environment(EnvType.CLIENT)
   private record Snapshot(
      double x, double y, double z, float yRot, float yBodyRot, float yHeadRot, float xRot, float walkPosition, float walkSpeed, float walkSpeedOld, long tick
   ) {
   }
}
