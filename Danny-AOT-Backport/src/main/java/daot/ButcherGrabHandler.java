package daot;

import daot.network.ButcherGrabStatePayload;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import daot.compat.network.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

public final class ButcherGrabHandler {
   private static final double[][] ANGLE_OFFSETS = new double[][]{{-9.0, -6.0}, {9.0, -6.0}, {-9.0, 6.0}, {9.0, 6.0}};
   private static final double DEFAULT_DISTANCE = 5.0;
   private static final double MIN_DISTANCE = 1.5;
   private static final double MAX_DISTANCE = 11.0;
   private static final double DISTANCE_PER_NOTCH = 0.7;
   private static final Map<UUID, ButcherGrabHandler.GrabState> STATES = new ConcurrentHashMap<>();

   private ButcherGrabHandler() {
   }

   public static void startGrab(ServerPlayerEntity player) {
      ServerWorld level = player.getServerWorld();
      UUID id = player.getUuid();
      ButcherGrabHandler.GrabState state = STATES.computeIfAbsent(id, k -> new ButcherGrabHandler.GrabState());
      state.active = true;
      state.distance = 5.0;

      for (int i = 0; i < 4; i++) {
         state.tethered[i] = null;
      }

      Vec3d look = player.getRotationVec(1.0F);
      if (!(look.lengthSquared() < 1.0E-4)) {
         look = look.normalize();
         state.lookAtGrab = look;
         Vec3d chest = chestPos(player);
         Set<UUID> claimed = new HashSet<>();
         boolean griefing = DannysAot.isTitanGriefingEnabled(level);

         for (int t = 0; t < 4; t++) {
            Vec3d dir = tentacleDirection(look, t);
            Vec3d endpoint = chest.add(dir.multiply(11.0));
            EntityHitResult eHit = raycastEntity(level, player, chest, endpoint, claimed);
            BlockHitResult bHit = level.raycast(new RaycastContext(chest, endpoint, ShapeType.COLLIDER, FluidHandling.NONE, player));
            double eDist = eHit == null ? Double.MAX_VALUE : eHit.getPos().distanceTo(chest);
            double bDist = bHit.getType() == Type.MISS ? Double.MAX_VALUE : bHit.getPos().distanceTo(chest);
            if (eHit != null && eDist <= bDist) {
               Entity target = eHit.getEntity();
               if (target instanceof LivingEntity && target != player) {
                  state.tethered[t] = target;
                  claimed.add(target.getUuid());
               }
            } else if (bHit.getType() == Type.BLOCK && griefing) {
               BlockPos pos = bHit.getBlockPos();
               BlockState bs = level.getBlockState(pos);
               if (!bs.isAir() && bs.getHardness(level, pos) >= 0.0F && !bs.isIn(BlockTags.WITHER_IMMUNE) && !(bs.getBlock() instanceof FluidBlock)) {
                  FallingBlockEntity fbe = FallingBlockEntity.spawnFromBlock(level, pos, bs);
                  fbe.setHurtEntities(2.0F, 20);
                  fbe.timeFalling = 1;
                  state.tethered[t] = fbe;
               }
            }
         }

         broadcastState(player, state);
      }
   }

   public static void tick(MinecraftServer server) {
      if (!STATES.isEmpty()) {
         for (Entry<UUID, ButcherGrabHandler.GrabState> entry : STATES.entrySet()) {
            UUID id = entry.getKey();
            ButcherGrabHandler.GrabState state = entry.getValue();
            if (state.active) {
               ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
               if (player == null) {
                  state.active = false;

                  for (int i = 0; i < 4; i++) {
                     state.tethered[i] = null;
                  }
               } else if (!ModEffects.isPowerDisabled(player) && BloodlineData.get(player.getServerWorld()).getBloodline(id) == BloodlineType.BUTCHER) {
                  Vec3d look = player.getRotationVec(1.0F);
                  if (!(look.lengthSquared() < 1.0E-4)) {
                     look = look.normalize();
                     state.lookAtGrab = look;
                     Vec3d chest = chestPos(player);

                     for (int t = 0; t < 4; t++) {
                        Entity target = state.tethered[t];
                        if (target != null) {
                           if (target.isAlive() && !target.isRemoved()) {
                              Vec3d dir = tentacleDirection(look, t);
                              Vec3d anchor = chest.add(dir.multiply(state.distance));
                              holdAtAnchor(target, anchor);
                           } else {
                              state.tethered[t] = null;
                           }
                        }
                     }

                     broadcastState(player, state);
                  }
               } else {
                  releaseAll(player, state);
               }
            }
         }
      }
   }

   public static void stopGrab(ServerPlayerEntity player) {
      ButcherGrabHandler.GrabState state = STATES.get(player.getUuid());
      if (state != null) {
         releaseAll(player, state);
      }
   }

   public static void scroll(ServerPlayerEntity player, int notches) {
      ButcherGrabHandler.GrabState state = STATES.get(player.getUuid());
      if (state != null && state.active) {
         state.distance = Math.max(1.5, Math.min(11.0, state.distance - notches * 0.7));
         broadcastState(player, state);
      }
   }

   public static void onPlayerDisconnect(UUID id) {
      STATES.remove(id);
   }

   private static void holdAtAnchor(Entity target, Vec3d anchor) {
      target.setPosition(anchor.x, anchor.y, anchor.z);
      target.setVelocity(Vec3d.ZERO);
      target.fallDistance = 0.0F;
      target.velocityModified = true;
      if (target instanceof FallingBlockEntity fbe) {
         fbe.setNoGravity(true);
         fbe.noClip = true;
         fbe.timeFalling = 1;
      }

      if (target instanceof ServerPlayerEntity sp && sp.networkHandler != null) {
         sp.networkHandler.syncWithPlayerPosition();
      }
   }

   private static void releaseAll(ServerPlayerEntity player, ButcherGrabHandler.GrabState state) {
      state.active = false;

      for (int i = 0; i < 4; i++) {
         if (state.tethered[i] instanceof FallingBlockEntity fbe) {
            fbe.setNoGravity(false);
            fbe.noClip = false;
         }

         state.tethered[i] = null;
      }

      broadcastState(player, state);
   }

   private static void broadcastState(ServerPlayerEntity player, ButcherGrabHandler.GrabState state) {
      Vec3d dir = state.lookAtGrab.lengthSquared() < 1.0E-6 ? player.getRotationVec(1.0F) : state.lookAtGrab;
      ButcherGrabStatePayload payload = new ButcherGrabStatePayload(player.getUuid(), state.active, dir.x, dir.y, dir.z, state.distance);

      for (ServerPlayerEntity p : player.getServerWorld().getServer().getPlayerManager().getPlayerList()) {
         ServerPlayNetworking.send(p, payload);
      }
   }

   private static Vec3d tentacleDirection(Vec3d look, int t) {
      double baseYaw = Math.atan2(-look.x, look.z);
      double basePitch = -Math.asin(Math.max(-1.0, Math.min(1.0, look.y)));
      double yawRad = baseYaw + Math.toRadians(ANGLE_OFFSETS[t][0]);
      double pitchRad = basePitch + Math.toRadians(ANGLE_OFFSETS[t][1]);
      double cosPitch = Math.cos(pitchRad);
      return new Vec3d(-Math.sin(yawRad) * cosPitch, -Math.sin(pitchRad), Math.cos(yawRad) * cosPitch);
   }

   private static Vec3d chestPos(ServerPlayerEntity player) {
      return player.getPos().add(0.0, player.getStandingEyeHeight() * 0.7, 0.0);
   }

   private static EntityHitResult raycastEntity(ServerWorld level, ServerPlayerEntity caster, Vec3d start, Vec3d end, Set<UUID> claimed) {
      Vec3d dir = end.subtract(start);
      Box box = new Box(start, end).expand(1.0);
      Entity best = null;
      double bestDist = Double.MAX_VALUE;
      Vec3d bestHit = null;

      for (Entity e : level.getOtherEntities(caster, box)) {
         if (e instanceof LivingEntity && e != caster && !claimed.contains(e.getUuid())) {
            Box eBox = e.getBoundingBox().expand(0.3);
            Optional<Vec3d> clip = eBox.raycast(start, end);
            if (!clip.isEmpty()) {
               Vec3d hitPos = clip.get();
               double d = hitPos.distanceTo(start);
               if (d < bestDist) {
                  bestDist = d;
                  best = e;
                  bestHit = hitPos;
               }
            }
         }
      }

      return best == null ? null : new EntityHitResult(best, bestHit);
   }

   private static final class GrabState {
      boolean active = false;
      double distance = 5.0;
      Vec3d lookAtGrab = Vec3d.ZERO;
      final Entity[] tethered = new Entity[4];
   }
}
