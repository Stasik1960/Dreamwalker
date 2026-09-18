package daot;

import daot.mixin.FallingBlockEntityAccessor;
import daot.network.TitanDashAnimPayload;
import daot.network.TitanDashSyncPayload;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import daot.compat.network.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class TitanDashTracker {
   public static final float MAX_CHARGE = 300.0F;
   public static final float REFILL_PER_TICK = 3.0F;
   public static final int CHARGE_TICKS = 25;
   public static final int DASH_TICKS = 10;
   public static final double DASH_SPEED = 5.0;
   private static final double DASH_DAMAGE_RADIUS = 6.0;
   private static final double BLOCK_BREAK_RADIUS = 2.5;
   private static final float SHIFTER_DASH_DAMAGE = 150.0F;
   public static final int PHASE_IDLE = 0;
   public static final int PHASE_CHARGE = 1;
   public static final int PHASE_DASH = 2;
   private static final Map<UUID, Float> charges = new HashMap<>();
   private static final Map<UUID, TitanDashTracker.DashState> states = new HashMap<>();
   private static boolean initialized = false;

   public static void initialize() {
      if (!initialized) {
         initialized = true;
         ServerTickEvents.END_SERVER_TICK.register(TitanDashTracker::serverTick);
         DannysAot.LOGGER.info("TitanDashTracker initialized");
      }
   }

   public static void ensureAndSync(ServerPlayerEntity player) {
      charges.putIfAbsent(player.getUuid(), 300.0F);
      TitanDashTracker.DashState st = states.get(player.getUuid());
      sync(player, charges.getOrDefault(player.getUuid(), 300.0F), st != null ? st.phase : 0, st != null ? st.dir : Vec3d.ZERO);
   }

   public static void clear(ServerPlayerEntity player) {
      charges.remove(player.getUuid());
      states.remove(player.getUuid());
   }

   public static boolean activate(ServerPlayerEntity player) {
      UUID uuid = player.getUuid();
      if (states.containsKey(uuid)) {
         return false;
      } else if (killGrabbingPureTitan(player)) {
         return true;
      } else if (!DannysAot.isODMGear(player.getEquippedStack(EquipmentSlot.LEGS).getItem())) {
         player.sendMessage(Text.literal("You need ODM gear to dash").styled(style -> style.withColor(Formatting.RED)), true);
         return false;
      } else {
         float charge = charges.getOrDefault(uuid, 300.0F);
         if (charge < 300.0F) {
            return false;
         } else {
            charges.put(uuid, 0.0F);
            TitanDashTracker.DashState st = new TitanDashTracker.DashState();
            st.phase = 1;
            st.phaseTick = 0;
            states.put(uuid, st);
            sync(player, 0.0F, 1, Vec3d.ZERO);
            if (player.getWorld() instanceof ServerWorld sl) {
               sl.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.2F, 1.6F);
               sl.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_CONDUIT_ACTIVATE, SoundCategory.PLAYERS, 0.8F, 1.4F);
            }

            for (ServerPlayerEntity nearby : PlayerLookup.tracking(player)) {
               ServerPlayNetworking.send(nearby, new TitanDashAnimPayload(player.getId()));
            }

            return true;
         }
      }
   }

   private static boolean killGrabbingPureTitan(ServerPlayerEntity player) {
      if (!(player.getWorld() instanceof ServerWorld level)) {
         return false;
      } else {
         Object var6 = null;
         if (player.getVehicle() instanceof GrabbingTitan gt && gt.getEatingTargetId() == player.getId()) {
            var6 = gt;
         } else {
            Iterator var8 = level.getEntitiesByClass(
                  LivingEntity.class, player.getBoundingBox().expand(24.0), e -> e instanceof GrabbingTitan g && g.getEatingTargetId() == player.getId()
               )
               .iterator();
            if (var8.hasNext()) {
               LivingEntity le = (LivingEntity)var8.next();
               var6 = (LivingEntity & GrabbingTitan)le;
            }
         }

         if (var6 instanceof LivingEntity captor) {
            ((GrabbingTitan)var6).cancelEating();
            player.stopRiding();
            spawnBlood(level, captor);
            captor.damage(player.getDamageSources().playerAttack(player), Float.MAX_VALUE);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_TRIDENT_RIPTIDE_3, SoundCategory.PLAYERS, 1.4F, 1.0F);
            return true;
         } else {
            return false;
         }
      }
   }

   private static void serverTick(MinecraftServer server) {
      Iterator<Entry<UUID, TitanDashTracker.DashState>> it = states.entrySet().iterator();

      while (it.hasNext()) {
         Entry<UUID, TitanDashTracker.DashState> e = it.next();
         ServerPlayerEntity player = server.getPlayerManager().getPlayer(e.getKey());
         if (player != null && player.isAlive() && player.getWorld() instanceof ServerWorld level && player.getCommandTags().contains("titan_bloodline")) {
            TitanDashTracker.DashState st = e.getValue();
            if (st.phase == 1) {
               st.phaseTick++;
               if (st.phaseTick >= 25) {
                  Vec3d dir = player.getRotationVec(1.0F);
                  if (dir.lengthSquared() < 1.0E-4) {
                     dir = new Vec3d(0.0, 0.0, 1.0);
                  }

                  st.dir = dir.normalize();
                  st.start = player.getPos().add(0.0, player.getHeight() * 0.5, 0.0);
                  st.phase = 2;
                  st.phaseTick = 0;
                  st.hit.clear();
                  sync(player, 0.0F, 2, st.dir);
                  level.playSound(
                     null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_TRIDENT_RIPTIDE_3, SoundCategory.PLAYERS, 1.4F, 1.0F
                  );
                  level.playSound(
                     null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.7F, 1.5F
                  );
               }
            } else if (st.phase == 2) {
               Vec3d center = st.start.add(st.dir.multiply(st.phaseTick * 5.0));
               dashDamage(level, player, center, st);
               breakBlocks(level, player, center, st.dir);
               st.phaseTick++;
               if (st.phaseTick >= 10) {
                  it.remove();
                  sync(player, charges.getOrDefault(player.getUuid(), 0.0F), 0, Vec3d.ZERO);
               }
            }
         } else {
            it.remove();
         }
      }

      for (Entry<UUID, Float> e : charges.entrySet()) {
         if (!states.containsKey(e.getKey())) {
            float charge = e.getValue();
            if (!(charge >= 300.0F)) {
               float updated = Math.min(300.0F, charge + 3.0F);
               e.setValue(updated);
               ServerPlayerEntity player = server.getPlayerManager().getPlayer(e.getKey());
               if (player != null && server.getTicks() % 5 == 0) {
                  sync(player, updated, 0, Vec3d.ZERO);
               }
            }
         }
      }
   }

   private static void dashDamage(ServerWorld level, ServerPlayerEntity player, Vec3d center, TitanDashTracker.DashState st) {
      Box box = new Box(center, center).expand(6.0);
      DamageSource source = player.getDamageSources().playerAttack(player);

      for (Entity entity : level.getOtherEntities(player, box)) {
         if (entity != player && !st.hit.contains(entity.getId())) {
            if (entity instanceof SmallTitanNapeEntity nape) {
               oneShot(level, nape, nape.getParentTitan(), player, source, st);
            } else if (entity instanceof TitanNapeEntity nape) {
               oneShot(level, nape, nape.getParentTitan(), player, source, st);
            } else if (entity instanceof SmallTitan2NapeEntity nape) {
               oneShot(level, nape, nape.getParentTitan(), player, source, st);
            } else if (entity instanceof FritzTitanNapeEntity nape) {
               oneShot(level, nape, nape.getParentTitan(), player, source, st);
            } else if (entity instanceof TitanEntity titan) {
               if (!titan.isDead()) {
                  titan.damage(source, Float.MAX_VALUE);
               }

               st.hit.add(entity.getId());
            } else if (entity instanceof SmallTitanEntity titan) {
               if (!titan.isDead()) {
                  titan.damage(source, Float.MAX_VALUE);
               }

               st.hit.add(entity.getId());
            } else if (entity instanceof AttackTitanNapeEntity nape) {
               AttackTitanEntity p = nape.getParentTitan();
               if (p != null && !p.isDead()) {
                  spawnBlood(level, nape);
                  p.hurtFromNape(source, 150.0F);
                  st.hit.add(p.getId());
               }

               st.hit.add(entity.getId());
            } else if (entity instanceof ArmoredTitanNapeEntity nape) {
               ArmoredTitanEntity p = nape.getParentTitan();
               if (p != null && !p.isDead()) {
                  spawnBlood(level, nape);
                  p.hurtFromNape(source, 150.0F);
                  st.hit.add(p.getId());
               }

               st.hit.add(entity.getId());
            } else if (entity instanceof ColossalTitanNapeEntity nape) {
               ColossalTitanEntity p = nape.getParentTitan();
               if (p != null && !p.isDead()) {
                  spawnBlood(level, nape);
                  p.hurtFromNape(source, 150.0F);
                  st.hit.add(p.getId());
               }

               st.hit.add(entity.getId());
            } else if (entity instanceof FemaleTitanNapeEntity nape) {
               FemaleTitanEntity p = nape.getParentTitan();
               if (p != null && !p.isDead()) {
                  spawnBlood(level, nape);
                  p.hurtFromNape(source, 150.0F);
                  st.hit.add(p.getId());
               }

               st.hit.add(entity.getId());
            } else if (entity instanceof BeastTitanNapeEntity nape) {
               BeastTitanEntity p = nape.getParentTitan();
               if (p != null && !p.isDead()) {
                  spawnBlood(level, nape);
                  p.hurtFromNape(source, 150.0F);
                  st.hit.add(p.getId());
               }

               st.hit.add(entity.getId());
            } else if (entity instanceof WarhammerTitanNapeEntity nape) {
               WarhammerTitanEntity p = nape.getParentTitan();
               if (p != null && !p.isDead()) {
                  spawnBlood(level, nape);
                  p.hurtFromNape(source, 150.0F);
                  st.hit.add(p.getId());
               }

               st.hit.add(entity.getId());
            }
         }
      }
   }

   private static void oneShot(
      ServerWorld level, Entity nape, LivingEntity parent, ServerPlayerEntity player, DamageSource source, TitanDashTracker.DashState st
   ) {
      if (parent != null && !parent.isRemoved() && parent.isAlive()) {
         spawnBlood(level, nape);
         parent.damage(source, Float.MAX_VALUE);
         st.hit.add(parent.getId());
      }

      st.hit.add(nape.getId());
   }

   private static void spawnBlood(ServerWorld level, Entity at) {
      BlockStateParticleEffect blood = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.getDefaultState());
      level.spawnParticles(blood, at.getX(), at.getY(), at.getZ(), 30, 0.4, 0.4, 0.4, 0.15);
   }

   private static void breakBlocks(ServerWorld level, ServerPlayerEntity player, Vec3d center, Vec3d dir) {
      if (DannysAot.isTitanGriefingEnabled(level)) {
         int r = (int)Math.ceil(2.5);
         BlockPos base = BlockPos.ofFloored(center);
         boolean playedSound = false;

         for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
               for (int dz = -r; dz <= r; dz++) {
                  if (!(dx * dx + dy * dy + dz * dz > 6.25)) {
                     BlockPos pos = base.add(dx, dy, dz);
                     BlockState state = level.getBlockState(pos);
                     if (!state.isAir() && !(state.getHardness(level, pos) < 0.0F) && !state.isIn(BlockTags.WITHER_IMMUNE)) {
                        if (!playedSound) {
                           level.playSound(null, pos, state.getSoundGroup().getBreakSound(), SoundCategory.BLOCKS, 1.5F, 0.6F);
                           playedSound = true;
                        }

                        if (player.getRandom().nextFloat() < 0.3F) {
                           flingBlock(level, pos, state, dir, player);
                        } else {
                           level.removeBlock(pos, false);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static void flingBlock(ServerWorld level, BlockPos pos, BlockState state, Vec3d dir, ServerPlayerEntity player) {
      level.removeBlock(pos, false);
      double vx = dir.x * (1.0 + player.getRandom().nextDouble()) + (player.getRandom().nextDouble() - 0.5) * 0.6;
      double vy = 0.5 + player.getRandom().nextDouble() * 0.9;
      double vz = dir.z * (1.0 + player.getRandom().nextDouble()) + (player.getRandom().nextDouble() - 0.5) * 0.6;
      FallingBlockEntity fb = new FallingBlockEntity(EntityType.FALLING_BLOCK, level);
      ((FallingBlockEntityAccessor)fb).setBlockState(state);
      fb.setPosition(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
      fb.setFallingBlockPos(pos);
      fb.setVelocity(vx, vy, vz);
      fb.timeFalling = 1;
      fb.dropItem = false;
      level.spawnEntity(fb);
   }

   private static void sync(ServerPlayerEntity player, float charge, int phase, Vec3d dir) {
      ServerPlayNetworking.send(player, new TitanDashSyncPayload(charge, 300.0F, phase, (float)dir.x, (float)dir.y, (float)dir.z));
   }

   public static float getCharge(UUID uuid) {
      return charges.getOrDefault(uuid, 300.0F);
   }

   private static final class DashState {
      int phase = 0;
      int phaseTick = 0;
      Vec3d dir = Vec3d.ZERO;
      Vec3d start = Vec3d.ZERO;
      final Set<Integer> hit = new HashSet<>();
   }
}
