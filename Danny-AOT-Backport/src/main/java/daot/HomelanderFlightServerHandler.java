package daot;

import daot.mixin.FallingBlockEntityAccessor;
import daot.network.EffectPayload;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import daot.compat.network.ServerPlayNetworking;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class HomelanderFlightServerHandler {
   public static final double NOCLIP_THRESHOLD = 2.4225;
   public static final double DAMAGE_THRESHOLD = 1.21125;
   private static final double TELEPORT_THRESHOLD = 25.0;
   private static final float IMPACT_PITCH = 0.9F;
   private static final int MAX_BLOCKS_PER_TICK = 96;
   private static final int MAX_INITIAL_IMPACT_BLOCKS = 1024;
   private static final long INITIAL_IMPACT_REARM_TICKS = 100L;
   private static final Set<UUID> flyingPlayers = ConcurrentHashMap.newKeySet();
   private static final Map<UUID, Vec3d> lastPos = new HashMap<>();
   private static final Set<UUID> initialImpactConsumed = ConcurrentHashMap.newKeySet();
   private static final Map<UUID, Long> lastBlockHitTick = new HashMap<>();
   private static final Map<UUID, Long> lastImpactSoundTick = new HashMap<>();
   private static final long IMPACT_SOUND_COOLDOWN_TICKS = 5L;
   private static final Map<UUID, Byte> lastInputBits = new ConcurrentHashMap<>();
   private static final Map<UUID, Long> lastGibSpawnTick = new ConcurrentHashMap<>();
   private static final long GIB_COOLDOWN_TICKS = 20L;
   private static final Map<UUID, Long> pendingTakeoffKnockback = new ConcurrentHashMap<>();
   private static final List<HomelanderFlightServerHandler.LaserFireEntry> pendingLaserFires = Collections.synchronizedList(new ArrayList<>());
   private static final Map<ServerWorld, Set<BlockPos>> laserFirePositions = new ConcurrentHashMap<>();
   private static final long LASER_FIRE_LIFETIME_TICKS = 30L;
   private static final int TAKEOFF_KNOCKBACK_DELAY_TICKS = 8;
   private static final double TAKEOFF_KNOCKBACK_RADIUS = 5.0;
   private static final double TAKEOFF_KNOCKBACK_HORIZONTAL = 1.5;
   private static final double TAKEOFF_KNOCKBACK_UP = 0.8;

   private HomelanderFlightServerHandler() {
   }

   public static void register() {
      ServerTickEvents.END_SERVER_TICK.register(HomelanderFlightServerHandler::tick);
   }

   public static void setFlying(ServerPlayerEntity player, boolean flying) {
      UUID id = player.getUuid();
      if (flying) {
         flyingPlayers.add(id);
         initialImpactConsumed.remove(id);
         lastBlockHitTick.remove(id);
         pendingTakeoffKnockback.put(id, player.getServerWorld().getTime() + 8L);
      } else {
         flyingPlayers.remove(id);
         lastPos.remove(id);
         initialImpactConsumed.remove(id);
         lastBlockHitTick.remove(id);
         lastImpactSoundTick.remove(id);
         lastInputBits.remove(id);
         pendingTakeoffKnockback.remove(id);
         player.noClip = false;
      }
   }

   public static void onPlayerLeave(UUID id) {
      flyingPlayers.remove(id);
      lastPos.remove(id);
      initialImpactConsumed.remove(id);
      lastBlockHitTick.remove(id);
      lastImpactSoundTick.remove(id);
      lastInputBits.remove(id);
      pendingTakeoffKnockback.remove(id);
   }

   public static boolean isFlying(UUID uuid) {
      return flyingPlayers.contains(uuid);
   }

   public static Set<UUID> currentlyFlying() {
      return Set.copyOf(flyingPlayers);
   }

   public static void cacheInputBits(UUID uuid, byte bits) {
      if (bits == 0) {
         lastInputBits.remove(uuid);
      } else {
         lastInputBits.put(uuid, bits);
      }
   }

   public static byte getCachedInputBits(UUID uuid) {
      Byte v = lastInputBits.get(uuid);
      return v == null ? 0 : v;
   }

   private static void tick(MinecraftServer server) {
      if (!pendingLaserFires.isEmpty()) {
         synchronized (pendingLaserFires) {
            Iterator<HomelanderFlightServerHandler.LaserFireEntry> it = pendingLaserFires.iterator();

            while (it.hasNext()) {
               HomelanderFlightServerHandler.LaserFireEntry entry = it.next();
               if (entry.level.getTime() >= entry.expireTick) {
                  BlockState now = entry.level.getBlockState(entry.pos);
                  if (now.getBlock() instanceof AbstractFireBlock) {
                     entry.level.setBlockState(entry.pos, Blocks.AIR.getDefaultState(), 11);
                  }

                  it.remove();
                  Set<BlockPos> positions = laserFirePositions.get(entry.level);
                  if (positions != null) {
                     positions.remove(entry.pos);
                     if (positions.isEmpty()) {
                        laserFirePositions.remove(entry.level);
                     }
                  }
               }
            }
         }
      }

      if (!flyingPlayers.isEmpty()) {
         for (ServerWorld level : server.getWorlds()) {
            for (ServerPlayerEntity player : level.getPlayers()) {
               UUID id = player.getUuid();
               if (flyingPlayers.contains(id)) {
                  BloodlineType bl = BloodlineData.get(level).getBloodline(id);
                  if (bl != BloodlineType.HOMELANDER) {
                     flyingPlayers.remove(id);
                     lastPos.remove(id);
                     pendingTakeoffKnockback.remove(id);
                     player.noClip = false;
                  } else {
                     Long scheduledTick = pendingTakeoffKnockback.get(id);
                     if (scheduledTick != null && level.getTime() >= scheduledTick) {
                        pendingTakeoffKnockback.remove(id);
                        fireTakeoffKnockback(level, player);
                     }

                     Vec3d cur = player.getPos();
                     Vec3d prev = lastPos.put(id, cur);
                     if (prev != null) {
                        Vec3d dpt = cur.subtract(prev);
                        double speed = dpt.length();
                        if (speed < 1.0E-4) {
                           if (player.noClip) {
                              player.noClip = false;
                           }
                        } else if (speed > 25.0) {
                           if (player.noClip) {
                              player.noClip = false;
                           }
                        } else {
                           if (speed >= 2.4225) {
                              player.noClip = true;
                              smashBlocks(level, player, dpt, speed);
                           } else if (player.noClip) {
                              player.noClip = false;
                           }

                           if (initialImpactConsumed.contains(id)) {
                              Long lastHit = lastBlockHitTick.get(id);
                              if (lastHit != null && level.getTime() - lastHit >= 100L) {
                                 initialImpactConsumed.remove(id);
                              }
                           }

                           if (speed >= 1.21125 && !HomelanderGrabServerHandler.hasGrabIntent(id)) {
                              damageEntitiesInPath(level, player, dpt, speed);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static void smashBlocks(ServerWorld level, ServerPlayerEntity player, Vec3d dpt, double speed) {
      boolean griefingEnabled = DannysAot.isTitanGriefingEnabled(level);
      UUID id = player.getUuid();
      boolean initialArmed = !initialImpactConsumed.contains(id);
      double over = speed - 2.4225;
      int sustainedRadius = (int)Math.floor(Math.max(1.0, Math.min(4.0, 1.0 + over * 0.5)));
      Vec3d forward = dpt.normalize();
      double cx = player.getX();
      double cy = player.getY() + player.getHeight() * 0.5;
      double cz = player.getZ();
      if (hasSmashableBlockInRadius(level, cx, cy, cz, sustainedRadius)) {
         if (!griefingEnabled) {
            playImpactSoundThrottled(level, id, BlockPos.ofFloored(cx, cy, cz));
            lastBlockHitTick.put(id, level.getTime());
         } else {
            int radius = initialArmed ? sustainedRadius * 3 : sustainedRadius;
            int budget = initialArmed ? 1024 : 96;
            double radiusSq = radius * radius;
            int destroyed = 0;

            for (int dx = -radius; dx <= radius && destroyed < budget; dx++) {
               for (int dy = -radius; dy <= radius && destroyed < budget; dy++) {
                  for (int dz = -radius; dz <= radius && destroyed < budget; dz++) {
                     if (!(dx * dx + dy * dy + dz * dz > radiusSq)) {
                        BlockPos pos = BlockPos.ofFloored(cx + dx, cy + dy, cz + dz);
                        BlockState state = level.getBlockState(pos);
                        if (!state.isAir()
                           && !(state.getHardness(level, pos) < 0.0F)
                           && !state.isIn(BlockTags.WITHER_IMMUNE)
                           && (!(state.getBlock() instanceof FluidBlock) || !(level.random.nextFloat() > 0.05F))) {
                           flingAndPlaySound(level, id, pos, state, forward, speed);
                           destroyed++;
                        }
                     }
                  }
               }
            }

            if (destroyed > 0) {
               lastBlockHitTick.put(id, level.getTime());
               if (initialArmed) {
                  initialImpactConsumed.add(id);
               }
            }
         }
      }
   }

   private static boolean hasSmashableBlockInRadius(ServerWorld level, double cx, double cy, double cz, int radius) {
      double radiusSq = radius * radius;

      for (int dx = -radius; dx <= radius; dx++) {
         for (int dy = -radius; dy <= radius; dy++) {
            for (int dz = -radius; dz <= radius; dz++) {
               if (!(dx * dx + dy * dy + dz * dz > radiusSq)) {
                  BlockPos pos = BlockPos.ofFloored(cx + dx, cy + dy, cz + dz);
                  BlockState state = level.getBlockState(pos);
                  if (!state.isAir()
                     && !(state.getHardness(level, pos) < 0.0F)
                     && !state.isIn(BlockTags.WITHER_IMMUNE)
                     && !(state.getBlock() instanceof FluidBlock)) {
                     return true;
                  }
               }
            }
         }
      }

      return false;
   }

   public static void registerLaserFire(ServerWorld level, BlockPos pos) {
      BlockPos imm = pos.toImmutable();
      pendingLaserFires.add(new HomelanderFlightServerHandler.LaserFireEntry(level, imm, level.getTime() + 30L));
      laserFirePositions.computeIfAbsent(level, k -> ConcurrentHashMap.newKeySet()).add(imm);
   }

   public static boolean isLaserFire(ServerWorld level, BlockPos pos) {
      Set<BlockPos> positions = laserFirePositions.get(level);
      return positions != null && positions.contains(pos);
   }

   public static void flingBlockAsLaser(ServerWorld level, ServerPlayerEntity player, BlockPos pos, BlockState state, Vec3d forward) {
      level.removeBlock(pos, false);
      FallingBlockEntity fb = new FallingBlockEntity(EntityType.FALLING_BLOCK, level);
      ((FallingBlockEntityAccessor)fb).setBlockState(state);
      fb.setPosition(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
      fb.setFallingBlockPos(pos);
      double velScale = 1.2;
      double jitter = 0.25;
      double velX = forward.x * velScale + (level.random.nextDouble() - 0.5) * jitter;
      double velY = forward.y * velScale + (level.random.nextDouble() - 0.5) * jitter;
      double velZ = forward.z * velScale + (level.random.nextDouble() - 0.5) * jitter;
      fb.setVelocity(velX, velY, velZ);
      fb.timeFalling = 1;
      fb.dropItem = false;
      level.spawnEntity(fb);
      level.spawnParticles(
         new BlockStateParticleEffect(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.4, 0.4, 0.4, 0.1
      );
   }

   private static void fireTakeoffKnockback(ServerWorld level, ServerPlayerEntity launcher) {
      Vec3d origin = launcher.getPos();
      Box aabb = new Box(origin.x - 5.0, origin.y - 5.0, origin.z - 5.0, origin.x + 5.0, origin.y + 5.0, origin.z + 5.0);
      double radiusSq = 25.0;

      for (Entity e : level.getOtherEntities(launcher, aabb)) {
         if (e instanceof LivingEntity living && living != launcher) {
            Vec3d toEntity = e.getPos().subtract(origin);
            double distSq = toEntity.lengthSquared();
            if (!(distSq > radiusSq)) {
               double falloff = 1.0 - Math.sqrt(distSq) / 5.0;
               Vec3d horiz = new Vec3d(toEntity.x, 0.0, toEntity.z);
               Vec3d outward;
               if (horiz.lengthSquared() < 1.0E-4) {
                  double a = level.random.nextDouble() * Math.PI * 2.0;
                  outward = new Vec3d(Math.cos(a), 0.0, Math.sin(a));
               } else {
                  outward = horiz.normalize();
               }

               Vec3d kick = outward.multiply(1.5 * (0.5 + 0.5 * falloff)).add(0.0, 0.8 * (0.5 + 0.5 * falloff), 0.0);
               living.setVelocity(living.getVelocity().add(kick));
               living.velocityModified = true;
            }
         }
      }
   }

   private static void flingAndPlaySound(ServerWorld level, UUID playerId, BlockPos pos, BlockState state, Vec3d forward, double speed) {
      double boom = Math.min(speed / 2.4225, 5.0);
      double velScale = 1.0 + (boom - 1.0) * 0.5;
      level.removeBlock(pos, false);
      FallingBlockEntity fb = new FallingBlockEntity(EntityType.FALLING_BLOCK, level);
      ((FallingBlockEntityAccessor)fb).setBlockState(state);
      fb.setPosition(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
      fb.setFallingBlockPos(pos);
      double velX = (forward.x * (1.0 + level.random.nextDouble() * 0.5) + (level.random.nextDouble() - 0.5) * 0.5) * velScale;
      double velY = (0.5 + level.random.nextDouble() * 0.8) * velScale;
      double velZ = (forward.z * (1.0 + level.random.nextDouble() * 0.5) + (level.random.nextDouble() - 0.5) * 0.5) * velScale;
      fb.setVelocity(velX, velY, velZ);
      fb.timeFalling = 1;
      fb.dropItem = false;
      level.spawnEntity(fb);
      level.spawnParticles(
         new BlockStateParticleEffect(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.4, 0.4, 0.4, 0.1
      );
      playImpactSoundThrottled(level, playerId, pos);
   }

   private static void playImpactSoundThrottled(ServerWorld level, UUID playerId, BlockPos pos) {
      long now = level.getTime();
      Long last = lastImpactSoundTick.get(playerId);
      if (last == null || now - last >= 5L) {
         lastImpactSoundTick.put(playerId, now);
         level.playSound(null, pos, ModSounds.SHOT_IMPACT, SoundCategory.PLAYERS, 1.0F, 0.9F);
      }
   }

   private static void damageEntitiesInPath(ServerWorld level, ServerPlayerEntity player, Vec3d dpt, double speed) {
      Box hitBox = player.getBoundingBox().expand(0.4);
      Box swept = hitBox.stretch(dpt.x, dpt.y, dpt.z);
      float damage = (float)Math.min(60.0, speed * 4.0);
      Vec3d knock = dpt.normalize().multiply(speed * 0.7);
      boolean hitAny = false;
      Vec3d flightDir = dpt.normalize();

      for (Entity e : level.getOtherEntities(player, swept)) {
         if (e instanceof LivingEntity living && !living.isTeammate(player)) {
            boolean wasAlive = living.isAlive();
            living.damage(level.getDamageSources().playerAttack(player), damage);
            living.setVelocity(living.getVelocity().add(knock));
            living.velocityModified = true;
            playFlyHitFx(level, living);
            hitAny = true;
            boolean killed = wasAlive && living.isDead();
            if (killed) {
               GibEntity.spawnGibs(level, living, flightDir, 1.3F);
               lastGibSpawnTick.remove(living.getUuid());
            } else if (damage >= 10.0F) {
               long nowTick = level.getTime();
               Long last = lastGibSpawnTick.get(living.getUuid());
               if (last == null || nowTick - last >= 20L) {
                  GibEntity.spawnGibs(level, living, flightDir, 1.3F);
                  lastGibSpawnTick.put(living.getUuid(), nowTick);
               }
            }
         }
      }

      if (hitAny) {
         HomelanderBloodTracker.onFlyHit(player);
      }
   }

   public static void playFlyHitFx(ServerWorld level, LivingEntity victim) {
      float pitch = 0.7F + level.random.nextFloat() * 0.1F;
      level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), ModSounds.HANDBITE, SoundCategory.PLAYERS, 1.5F, pitch);
      EffectPayload bloodPayload = new EffectPayload("blood", victim.getX(), victim.getY() + victim.getHeight() * 0.5, victim.getZ(), 1.0F);
      BlockPos pos = new BlockPos((int)victim.getX(), (int)victim.getY(), (int)victim.getZ());

      for (ServerPlayerEntity p : PlayerLookup.tracking(level, pos)) {
         ServerPlayNetworking.send(p, bloodPayload);
      }
   }

   private record LaserFireEntry(ServerWorld level, BlockPos pos, long expireTick) {
   }
}
