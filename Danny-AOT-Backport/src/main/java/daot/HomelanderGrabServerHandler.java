package daot;

import daot.network.EffectPayload;
import daot.network.HomelanderGrabSyncPayload;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import daot.compat.network.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class HomelanderGrabServerHandler {
   private static final int ATTACK_COOLDOWN_TICKS = 6;
   private static final int ATTACK_IMPACT_WINDOW_TICKS = 20;
   private static final int ATTACK_DAMAGE_OFFSET = 3;
   private static final double GRAB_REACH = 1.5;
   private static final double GRAB_HOLD_DISTANCE = 1.1;
   private static final double GRAB_HOLD_Y_OFFSET = 0.15;
   private static final double ATTACK_REACH = 3.0;
   private static final double ATTACK_RADIUS = 1.6;
   private static final float FLY_ATTACK_DAMAGE = 8.0F;
   private static final float HEAVY_ATTACK_DAMAGE = 12.0F;
   private static final float HEAVY_ATTACK_KNOCKBACK = 1.2F;
   private static final String GRAB_TAG = "dannys-aot:being_grabbed";
   private static final Set<UUID> grabIntent = ConcurrentHashMap.newKeySet();
   private static final Map<UUID, Integer> grabbedEntity = new HashMap<>();
   private static final Map<UUID, HomelanderGrabServerHandler.PendingAttack> pendingAttacks = new HashMap<>();
   private static final Map<UUID, boolean[]> savedFlightAbilities = new HashMap<>();

   private HomelanderGrabServerHandler() {
   }

   public static void register() {
      ServerTickEvents.END_SERVER_TICK.register(HomelanderGrabServerHandler::tick);
   }

   public static void setIntent(ServerPlayerEntity player, boolean wantsGrab) {
      UUID id = player.getUuid();
      if (wantsGrab) {
         grabIntent.add(id);
      } else {
         grabIntent.remove(id);
         releaseGrab(player);
      }
   }

   public static boolean hasGrabIntent(UUID uuid) {
      return grabIntent.contains(uuid);
   }

   public static Set<UUID> currentGrabIntents() {
      return Set.copyOf(grabIntent);
   }

   public static int getGrabbedEntityId(UUID grabber) {
      Integer id = grabbedEntity.get(grabber);
      return id == null ? -1 : id;
   }

   public static void scheduleAttack(ServerPlayerEntity player, byte type) {
      UUID id = player.getUuid();
      HomelanderGrabServerHandler.PendingAttack existing = pendingAttacks.get(id);
      if (existing == null || existing.cooldownLeft <= 0) {
         pendingAttacks.put(id, new HomelanderGrabServerHandler.PendingAttack(type));
      }
   }

   public static void onPlayerLeave(UUID id) {
      grabIntent.remove(id);
      Integer grabbedId = grabbedEntity.remove(id);
      pendingAttacks.remove(id);
      savedFlightAbilities.remove(id);
   }

   private static void tick(MinecraftServer server) {
      if (!grabIntent.isEmpty() || !grabbedEntity.isEmpty() || !pendingAttacks.isEmpty()) {
         for (ServerWorld level : server.getWorlds()) {
            for (ServerPlayerEntity player : level.getPlayers()) {
               UUID id = player.getUuid();
               BloodlineType bl = BloodlineData.get(level).getBloodline(id);
               if (bl != BloodlineType.HOMELANDER) {
                  if (grabIntent.remove(id)) {
                     releaseGrab(player);
                  }

                  pendingAttacks.remove(id);
               } else {
                  tickGrab(level, player, id);
                  tickPendingAttack(level, player, id);
               }
            }
         }
      }
   }

   private static void tickGrab(ServerWorld level, ServerPlayerEntity player, UUID id) {
      Integer heldId = grabbedEntity.get(id);
      if (heldId == null) {
         if (grabIntent.contains(id)) {
            if (HomelanderFlightServerHandler.isFlying(player.getUuid())) {
               Entity victim = findGrabTarget(level, player);
               if (victim != null) {
                  startGrab(player, victim);
               }
            }
         }
      } else {
         Entity held = level.getEntityById(heldId);
         if (held == null || held.isRemoved() || !held.isAlive()) {
            releaseGrab(player);
         } else if (!grabIntent.contains(id)) {
            releaseGrab(player);
         } else if (!HomelanderFlightServerHandler.isFlying(player.getUuid())) {
            releaseGrab(player);
         } else {
            holdAtGrasp(player, held);
         }
      }
   }

   private static Entity findGrabTarget(ServerWorld level, ServerPlayerEntity player) {
      Box box = player.getBoundingBox().expand(1.5);

      for (Entity e : level.getOtherEntities(player, box)) {
         if (e != player && !e.hasPassenger(player) && !player.hasPassenger(e) && !e.getCommandTags().contains("dannys-aot:being_grabbed")) {
            return e;
         }
      }

      return null;
   }

   private static void startGrab(ServerPlayerEntity player, Entity victim) {
      UUID id = player.getUuid();
      grabbedEntity.put(id, victim.getId());
      if (victim.getVehicle() != null) {
         victim.stopRiding();
      }

      if (victim instanceof MobEntity mob) {
         mob.setAiDisabled(true);
      }

      if (victim instanceof ServerPlayerEntity sp) {
         sp.noClip = true;
         PlayerAbilities ab = sp.getAbilities();
         savedFlightAbilities.put(sp.getUuid(), new boolean[]{ab.allowFlying, ab.flying});
         boolean changed = false;
         if (!ab.allowFlying) {
            ab.allowFlying = true;
            changed = true;
         }

         if (!ab.flying) {
            ab.flying = true;
            changed = true;
         }

         if (changed) {
            sp.sendAbilitiesUpdate();
         }
      }

      victim.addCommandTag("dannys-aot:being_grabbed");
      holdAtGrasp(player, victim);
      broadcastGrabSync(player, victim.getId());
   }

   private static void holdAtGrasp(ServerPlayerEntity player, Entity victim) {
      Vec3d look = player.getRotationVec(1.0F);
      double tx = player.getX() + look.x * 1.1;
      double ty = player.getY() + 0.15 + look.y * 1.1;
      double tz = player.getZ() + look.z * 1.1;
      victim.setPosition(tx, ty, tz);
      victim.setVelocity(Vec3d.ZERO);
      victim.fallDistance = 0.0F;
      victim.velocityModified = true;
      if (victim instanceof ServerPlayerEntity sp && sp.networkHandler != null) {
         sp.networkHandler.syncWithPlayerPosition();
      }
   }

   private static void releaseGrab(ServerPlayerEntity player) {
      UUID id = player.getUuid();
      Integer heldId = grabbedEntity.remove(id);
      if (heldId != null) {
         broadcastGrabSync(player, -1);
         ServerWorld level = player.getServerWorld();
         Entity held = level.getEntityById(heldId);
         if (held != null) {
            if (held instanceof MobEntity mob) {
               mob.setAiDisabled(false);
            }

            if (held instanceof ServerPlayerEntity sp) {
               sp.noClip = false;
               boolean[] saved = savedFlightAbilities.remove(sp.getUuid());
               if (saved != null) {
                  PlayerAbilities ab = sp.getAbilities();
                  boolean stillFlyingHomelander = HomelanderFlightServerHandler.isFlying(sp.getUuid());
                  ab.allowFlying = saved[0] || stillFlyingHomelander || sp.isCreative() || sp.isSpectator();
                  ab.flying = stillFlyingHomelander || saved[1] && ab.allowFlying;
                  sp.sendAbilitiesUpdate();
               }
            }

            held.removeScoreboardTag("dannys-aot:being_grabbed");
         }
      }
   }

   private static void broadcastGrabSync(ServerPlayerEntity grabber, int victimEntityId) {
      HomelanderGrabSyncPayload payload = new HomelanderGrabSyncPayload(grabber.getUuid(), victimEntityId);

      for (ServerPlayerEntity p : PlayerLookup.tracking(grabber)) {
         ServerPlayNetworking.send(p, payload);
      }

      ServerPlayNetworking.send(grabber, payload);
   }

   private static void tickPendingAttack(ServerWorld level, ServerPlayerEntity player, UUID id) {
      HomelanderGrabServerHandler.PendingAttack atk = pendingAttacks.get(id);
      if (atk != null) {
         if (atk.type == 0 && !atk.impacted && atk.cooldownLeft == 3) {
            applyAttack(level, player, id, atk.type);
            atk.impacted = true;
         }

         if (atk.cooldownLeft > 0) {
            atk.cooldownLeft--;
         }

         if (atk.windowLeft > 0) {
            atk.windowLeft--;
         }

         if (atk.windowLeft <= 0) {
            pendingAttacks.remove(id);
         }
      }
   }

   public static void triggerKeyframeImpact(ServerPlayerEntity player, byte attackType) {
      if (attackType == 1 || attackType == 2) {
         UUID id = player.getUuid();
         HomelanderGrabServerHandler.PendingAttack atk = pendingAttacks.get(id);
         if (atk != null && !atk.impacted && atk.windowLeft > 0 && atk.type == attackType) {
            applyAttack(player.getServerWorld(), player, id, atk.type);
            atk.impacted = true;
         }
      }
   }

   private static void applyAttack(ServerWorld level, ServerPlayerEntity player, UUID id, byte type) {
      if (type == 0) {
         Integer heldId = grabbedEntity.get(id);
         if (heldId != null) {
            if (level.getEntityById(heldId) instanceof LivingEntity living) {
               boolean var20 = living.isAlive();
               living.damage(level.getDamageSources().playerAttack(player), 8.0F);
               living.velocityModified = true;
               playHitFx(level, living);
               HomelanderBloodTracker.onM1Hit(player);
               if (var20 && living.isDead()) {
                  GibEntity.spawnGibs(level, living, player.getRotationVec(1.0F), 0.7F);
               }
            }
         }
      } else {
         Vec3d look = player.getRotationVec(1.0F);
         double cx = player.getX() + look.x * 3.0 * 0.6;
         double cy = player.getY() + player.getHeight() * 0.5 + look.y * 3.0 * 0.6;
         double cz = player.getZ() + look.z * 3.0 * 0.6;
         Box hitBox = new Box(cx - 1.6, cy - 1.6, cz - 1.6, cx + 1.6, cy + 1.6, cz + 1.6);
         Iterator<Entity> it = level.getOtherEntities(player, hitBox).iterator();
         boolean hitAny = false;

         while (it.hasNext()) {
            Entity e = it.next();
            if (e instanceof LivingEntity living && !living.isTeammate(player)) {
               boolean wasAlive = living.isAlive();
               living.damage(level.getDamageSources().playerAttack(player), 12.0F);
               Vec3d knock = look.multiply(1.2F);
               living.setVelocity(living.getVelocity().add(knock.x, 0.4, knock.z));
               living.velocityModified = true;
               playHitFx(level, living);
               hitAny = true;
               if (wasAlive && living.isDead()) {
                  GibEntity.spawnGibs(level, living, look, 0.7F);
               }
            }
         }

         if (hitAny) {
            HomelanderBloodTracker.onM1Hit(player);
         }
      }
   }

   private static void playHitFx(ServerWorld level, LivingEntity victim) {
      SoundEvent punch = level.random.nextBoolean() ? ModSounds.FLESH_IMPACT_4 : ModSounds.FLESH_IMPACT_5;
      float pitch = 0.9F + level.random.nextFloat() * 0.2F;
      level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), punch, SoundCategory.PLAYERS, 1.5F, pitch);
      EffectPayload bloodPayload = new EffectPayload("blood", victim.getX(), victim.getY() + victim.getHeight() * 0.5, victim.getZ(), 1.0F);
      BlockPos pos = new BlockPos((int)victim.getX(), (int)victim.getY(), (int)victim.getZ());

      for (ServerPlayerEntity p : PlayerLookup.tracking(level, pos)) {
         ServerPlayNetworking.send(p, bloodPayload);
      }
   }

   private static class PendingAttack {
      final byte type;
      int cooldownLeft;
      int windowLeft;
      boolean impacted;

      PendingAttack(byte type) {
         this.type = type;
         this.cooldownLeft = 6;
         this.windowLeft = 20;
         this.impacted = false;
      }
   }
}
