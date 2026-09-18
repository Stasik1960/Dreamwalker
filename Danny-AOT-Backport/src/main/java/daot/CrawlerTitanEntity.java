package daot;

import daot.network.ModNetworking;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Mount;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.Entity.RemovalReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.DefaultAttributeContainer.Builder;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.util.GeckoLibUtil;

public class CrawlerTitanEntity extends HostileEntity implements GeoEntity, Mount, GrabbingTitan {
   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
   private final Set<UUID> selfInjectionPassengers = new HashSet<>();
   private final Map<UUID, Integer> sneakDeathTimers = new HashMap<>();
   private final Map<UUID, Long> lastBlockedDismountTick = new HashMap<>();
   private static final int SNEAK_DEATH_TICKS = 60;
   private static final int SNEAK_GRACE_TICKS = 5;
   private boolean dismountAllowed = false;
   private static final TrackedData<Boolean> DATA_IS_EATING = DataTracker.registerData(CrawlerTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Integer> DATA_EAT_TICK = DataTracker.registerData(CrawlerTitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Boolean> DATA_IS_DEAD = DataTracker.registerData(CrawlerTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Integer> DATA_EATING_TARGET_ID = DataTracker.registerData(CrawlerTitanEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Boolean> DATA_IS_RIDEABLE = DataTracker.registerData(CrawlerTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("Idle");
   private static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("walk");
   private static final RawAnimation EAT_ANIM = RawAnimation.begin().thenPlay("eat");
   private static final RawAnimation NIGHT_SLEEP_ANIM = RawAnimation.begin().thenLoop("sleep");
   private float forwardYaw;
   private int idleTimer = 0;
   private int walkTimer = 0;
   private boolean isIdling = false;
   private int trailCooldown = 0;
   private LivingEntity eatingTarget = null;
   private int eatGrabTicks = 0;
   public boolean isRideable = false;
   private static final double RIDEABLE_SPEED = 0.72;
   CrawlerTitanNapeEntity napeEntity;
   CrawlerTitanEyeEntity eyeEntity;
   private int nightSleepStart;
   private int nightSleepEnd;
   private boolean isNightSleeping = false;
   private double animSpeedSqr = 0.0;
   private int animSpeedTick = -1;
   private int contactDamageCooldown = 0;

   public void addSelfInjectionPassenger(Entity passenger) {
      this.selfInjectionPassengers.add(passenger.getUuid());
   }

   public void removeSelfInjectionPassenger(Entity passenger) {
      this.selfInjectionPassengers.remove(passenger.getUuid());
   }

   public boolean isSelfInjectionPassenger(Entity passenger) {
      return this.selfInjectionPassengers.contains(passenger.getUuid());
   }

   public boolean isDismountAllowed() {
      return this.dismountAllowed;
   }

   public void setDismountAllowed(boolean allowed) {
      this.dismountAllowed = allowed;
   }

   public void notifyDismountBlocked(Entity passenger) {
      this.lastBlockedDismountTick.put(passenger.getUuid(), this.getWorld().getTime());
   }

   public CrawlerTitanNapeEntity getNapeEntity() {
      return this.napeEntity;
   }

   public CrawlerTitanEyeEntity getEyeEntity() {
      return this.eyeEntity;
   }

   private void randomizeNightSleepTimes() {
      this.nightSleepStart = 13000 + this.random.nextInt(2001);
      this.nightSleepEnd = 22000 + this.random.nextInt(1001);
   }

   public boolean isNightSleeping() {
      return this.isNightSleeping;
   }

   public CrawlerTitanEntity(EntityType<? extends HostileEntity> type, World level) {
      super(type, level);
      this.forwardYaw = this.random.nextFloat() * 360.0F;
      this.randomizeNightSleepTimes();
   }

   public static Builder createAttributes() {
      return HostileEntity.createHostileAttributes()
         .add(EntityAttributes.GENERIC_MAX_HEALTH, 30.0)
         .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.024)
         .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 6.0)
         .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
         .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 16.0)
         .add(daot.compat.attributes.DaotEntityAttributes.STEP_HEIGHT, 3.0);
   }

   @Override
   protected void initDataTracker() {
      super.initDataTracker();
      this.dataTracker.startTracking(DATA_IS_EATING, false);
      this.dataTracker.startTracking(DATA_EAT_TICK, 0);
      this.dataTracker.startTracking(DATA_IS_DEAD, false);
      this.dataTracker.startTracking(DATA_EATING_TARGET_ID, -1);
      this.dataTracker.startTracking(DATA_IS_RIDEABLE, false);
   }

   @Override
   public boolean isEating() {
      return this.dataTracker.get(DATA_IS_EATING);
   }

   public void setEating(boolean eating) {
      this.dataTracker.set(DATA_IS_EATING, eating);
   }

   public int getEatTick() {
      return this.dataTracker.get(DATA_EAT_TICK);
   }

   public void setEatTick(int tick) {
      this.dataTracker.set(DATA_EAT_TICK, tick);
   }

   @Override
   public boolean isDead() {
      return this.dataTracker.get(DATA_IS_DEAD);
   }

   @Override
   public int getEatingTargetId() {
      return this.dataTracker.get(DATA_EATING_TARGET_ID);
   }

   public LivingEntity getEatingTarget() {
      return this.eatingTarget;
   }

   public void setRideable(boolean rideable) {
      this.isRideable = rideable;
      this.dataTracker.set(DATA_IS_RIDEABLE, rideable);
   }

   @Override
   public void onTrackedDataSet(TrackedData<?> data) {
      super.onTrackedDataSet(data);
      if (DATA_IS_RIDEABLE.equals(data)) {
         this.isRideable = this.dataTracker.get(DATA_IS_RIDEABLE);
      }
   }

   @Override
   protected void initGoals() {
   }

   @Override
   public void tick() {
      super.tick();
      if (!this.getWorld().isClient()) {
         if (this.isRideable) {
            if (this.getPassengerList().isEmpty()) {
               this.discard();
               return;
            }
         } else {
            this.serverTick();
         }

         if (this.contactDamageCooldown > 0) {
            this.contactDamageCooldown--;
         } else {
            this.tickContactDamage();
         }

         this.tickSelfInjectionPassengers();
      }
   }

   private void tickSelfInjectionPassengers() {
      long currentTick = this.getWorld().getTime();

      for (Entity passenger : this.getPassengerList()) {
         if (passenger instanceof ServerPlayerEntity sp && this.isSelfInjectionPassenger(sp)) {
            if (!sp.isDead()) {
               sp.setInvisible(true);
            }

            UUID playerUUID = sp.getUuid();
            Long lastBlocked = this.lastBlockedDismountTick.get(playerUUID);
            boolean isSneaking = sp.isSneaking() || lastBlocked != null && currentTick - lastBlocked <= 5L;
            if (isSneaking) {
               int sneakTicks = this.sneakDeathTimers.getOrDefault(playerUUID, 0) + 1;
               this.sneakDeathTimers.put(playerUUID, sneakTicks);
               if (sneakTicks >= 60) {
                  sp.setInvisible(false);
                  this.setDismountAllowed(true);
                  sp.stopRiding();
                  this.setDismountAllowed(false);
                  RegistryKey<DamageType> becomeTitanKey = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier("dannys-aot", "become_titan"));
                  RegistryEntry<DamageType> becomeTitanHolder = this.getWorld()
                     .getRegistryManager()
                     .getWrapperOrThrow(RegistryKeys.DAMAGE_TYPE)
                     .getOrThrow(becomeTitanKey);
                  DamageSource becomeTitanDamage = new DamageSource(becomeTitanHolder);
                  if (sp.isCreative()) {
                     sp.kill();
                  } else {
                     sp.damage(becomeTitanDamage, Float.MAX_VALUE);
                  }
               } else {
                  double remainingSeconds = (60 - sneakTicks) / 20.0;
                  String countdown = String.format("%.1f", remainingSeconds);
                  sp.sendMessage(Text.literal("Accept your fate? (" + countdown + ")"), true);
               }
            } else if (this.sneakDeathTimers.containsKey(playerUUID)) {
               this.sneakDeathTimers.remove(playerUUID);
               this.lastBlockedDismountTick.remove(playerUUID);
               sp.sendMessage(Text.empty(), true);
            }
         }
      }
   }

   @Override
   protected Vec3d getControlledMovementInput(PlayerEntity controllingPlayer, Vec3d movementInput) {
      if (!this.isRideable) {
         return super.getControlledMovementInput(controllingPlayer, movementInput);
      } else {
         float forward = controllingPlayer.forwardSpeed;
         float strafe = controllingPlayer.sidewaysSpeed;
         return forward == 0.0F && strafe == 0.0F ? Vec3d.ZERO : new Vec3d(strafe, 0.0, forward);
      }
   }

   @Override
   protected float getSaddledSpeed(PlayerEntity controllingPlayer) {
      return !this.isRideable ? super.getSaddledSpeed(controllingPlayer) : 0.72F;
   }

   @Override
   protected void tickControlled(PlayerEntity controllingPlayer, Vec3d movementInput) {
      super.tickControlled(controllingPlayer, movementInput);
      if (this.isRideable) {
         float targetYaw = controllingPlayer.getYaw();
         this.setYaw(targetYaw);
         this.prevYaw = targetYaw;
         this.bodyYaw = targetYaw;
         this.headYaw = targetYaw;
      }
   }

   private void serverTick() {
      if (!this.isDead() && this.isAlive()) {
         long timeOfDay = this.getWorld().getTimeOfDay() % 24000L;
         if (VillagerTransformTracker.getOwnerName(this) == null && timeOfDay >= this.nightSleepStart && timeOfDay <= this.nightSleepEnd) {
            if (!this.isNightSleeping) {
               this.isNightSleeping = true;
            }

            this.setAiDisabled(true);
            this.setTarget(null);
            if (this.age > 20) {
               if (this.napeEntity == null || this.napeEntity.isRemoved()) {
                  CrawlerTitanNapeEntity nape = new CrawlerTitanNapeEntity(DannysAot.CRAWLER_TITAN_NAPE, this.getWorld());
                  nape.setParentTitan(this);
                  nape.setPosition(this.getX(), this.getY(), this.getZ());
                  this.getWorld().spawnEntity(nape);
                  this.napeEntity = nape;
               }

               if (this.eyeEntity == null || this.eyeEntity.isRemoved()) {
                  CrawlerTitanEyeEntity eye = new CrawlerTitanEyeEntity(DannysAot.CRAWLER_TITAN_EYE, this.getWorld());
                  eye.setParentTitan(this);
                  eye.setPosition(this.getX(), this.getY(), this.getZ());
                  this.getWorld().spawnEntity(eye);
                  this.eyeEntity = eye;
               }
            }
         } else {
            if (this.isNightSleeping || this.isAiDisabled()) {
               this.isNightSleeping = false;
               this.setAiDisabled(false);
               this.randomizeNightSleepTimes();
            }

            if (this.isEating()) {
               this.tickEating();
            } else {
               if (this.eyeEntity != null && !this.eyeEntity.isRemoved()) {
                  double eyeX = this.eyeEntity.getX();
                  double eyeY = this.eyeEntity.getY();
                  double eyeZ = this.eyeEntity.getZ();
                  Box eatBox = new Box(eyeX - 2.0, eyeY - 1.5, eyeZ - 2.0, eyeX + 2.0, eyeY + 1.5, eyeZ + 2.0);
                  List<LivingEntity> nearby = this.getWorld()
                     .getEntitiesByClass(
                        LivingEntity.class,
                        eatBox,
                        e -> e instanceof PlayerEntity p && !p.isCreative() && !p.isSpectator() && !this.hasPassenger(p) && !ModNetworking.isFounder(p)
                           || e instanceof MerchantEntity
                     );
                  if (!nearby.isEmpty()) {
                     LivingEntity victim = nearby.get(0);
                     this.startEating(victim);
                     return;
                  }
               }

               if (this.isIdling) {
                  this.idleTimer--;
                  if (this.idleTimer <= 0) {
                     this.isIdling = false;
                     this.walkTimer = 400 + this.random.nextInt(600);
                  }

                  this.setVelocity(0.0, this.getVelocity().y, 0.0);
               } else {
                  this.walkTimer--;
                  if (this.walkTimer <= 0) {
                     this.isIdling = true;
                     this.idleTimer = 40 + this.random.nextInt(60);
                  } else {
                     float yawRad = (float)Math.toRadians(this.forwardYaw);
                     double speed = this.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
                     double dx = -Math.sin(yawRad) * speed;
                     double dz = Math.cos(yawRad) * speed;
                     this.setVelocity(dx, this.getVelocity().y, dz);
                     this.setYaw(this.forwardYaw);
                     this.bodyYaw = this.forwardYaw;
                     this.headYaw = this.forwardYaw;
                     if (this.trailCooldown <= 0) {
                        this.leaveTrail();
                        this.trailCooldown = 3;
                     } else {
                        this.trailCooldown--;
                     }

                     if (this.horizontalCollision && this.getVelocity().horizontalLengthSquared() < 1.0E-4) {
                        this.forwardYaw = this.forwardYaw + (45 + this.random.nextInt(90));
                     }
                  }
               }

               if (this.age > 20) {
                  if (this.napeEntity == null || this.napeEntity.isRemoved()) {
                     CrawlerTitanNapeEntity nape = new CrawlerTitanNapeEntity(DannysAot.CRAWLER_TITAN_NAPE, this.getWorld());
                     nape.setParentTitan(this);
                     nape.setPosition(this.getX(), this.getY(), this.getZ());
                     this.getWorld().spawnEntity(nape);
                     this.napeEntity = nape;
                  }

                  if (this.eyeEntity == null || this.eyeEntity.isRemoved()) {
                     CrawlerTitanEyeEntity eye = new CrawlerTitanEyeEntity(DannysAot.CRAWLER_TITAN_EYE, this.getWorld());
                     eye.setParentTitan(this);
                     eye.setPosition(this.getX(), this.getY(), this.getZ());
                     this.getWorld().spawnEntity(eye);
                     this.eyeEntity = eye;
                  }
               }
            }
         }
      }
   }

   private void startEating(LivingEntity victim) {
      this.eatingTarget = victim;
      this.setEating(true);
      this.setEatTick(0);
      this.eatGrabTicks = 0;
      this.dataTracker.set(DATA_EATING_TARGET_ID, victim.getId());
      victim.addCommandTag("dannysaot_being_grabbed");
   }

   private void tickEating() {
      if (this.eatingTarget != null && this.eatingTarget.isAlive()) {
         int eatTick = this.getEatTick();
         this.setEatTick(eatTick + 1);
         if (!this.eatingTarget.isConnectedThroughVehicle(this) && !this.hasPassenger(this.eatingTarget)) {
            if (this.eatingTarget.hasVehicle()) {
               this.eatingTarget.stopRiding();
            }

            this.eatingTarget.startRiding(this, true);
         }

         if (eatTick > 0 && eatTick % 20 == 0) {
            this.eatingTarget.damage(this.getDamageSources().mobAttack(this), 6.0F);
         }

         if (eatTick >= 120) {
            this.eatingTarget.stopRiding();
            this.eatingTarget.removeScoreboardTag("dannysaot_being_grabbed");
            float yawRad = (float)Math.toRadians(this.getYaw());
            this.eatingTarget.setVelocity(-Math.sin(yawRad) * 0.5, 0.3, Math.cos(yawRad) * 0.5);
            this.stopEating();
         }

         this.setVelocity(0.0, this.getVelocity().y, 0.0);
      } else {
         this.stopEating();
      }
   }

   @Override
   public LivingEntity getControllingPassenger() {
      return this.isRideable && this.getFirstPassenger() instanceof PlayerEntity player ? player : null;
   }

   public void onPlayerJump(int jumpPower) {
      if (this.isRideable && this.isOnGround()) {
         this.setVelocity(this.getVelocity().add(0.0, 0.42, 0.0));
      }
   }

   @Override
   protected void removePassenger(Entity passenger) {
      super.removePassenger(passenger);
      if (!this.getWorld().isClient()) {
         this.selfInjectionPassengers.remove(passenger.getUuid());
         this.sneakDeathTimers.remove(passenger.getUuid());
         if (passenger instanceof PlayerEntity player) {
            player.setInvisible(false);
         }

         if (this.isRideable) {
            this.discard();
         }
      }
   }

   private void stopEating() {
      if (this.eatingTarget != null) {
         this.eatingTarget.removeScoreboardTag("dannysaot_being_grabbed");
      }

      this.eatingTarget = null;
      this.setEating(false);
      this.setEatTick(0);
      this.dataTracker.set(DATA_EATING_TARGET_ID, -1);
   }

   @Override
   public void cancelEating() {
      LivingEntity target = this.eatingTarget;
      this.setEating(false);
      this.stopEating();
      if (target != null) {
         target.stopRiding();
      }
   }

   public Vec3d getPassengerRidingPos(Entity passenger) {
      if (this.isSelfInjectionPassenger(passenger)) {
         return this.getPos().add(0.0, this.getHeight() * 0.5, 0.0);
      } else if (this.isEating()) {
         float yaw = this.getYaw();
         double yawRad = Math.toRadians(yaw);
         double forwardX = -Math.sin(yawRad) * 2.0;
         double forwardZ = Math.cos(yawRad) * 2.0;
         return this.getPos().add(forwardX, 1.0, forwardZ);
      } else {
         return this.getPos().add(0, this.getMountedHeightOffset() + passenger.getHeightOffset(), 0);
      }
   }

   private void leaveTrail() {
      if (this.getWorld() instanceof ServerWorld serverLevel) {
         float var16 = (float)Math.toRadians(this.getYaw());

         for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
               double worldDx = dx * Math.cos(-var16) - dz * Math.sin(-var16);
               double worldDz = dx * Math.sin(-var16) + dz * Math.cos(-var16);
               double blockX = this.getX() + worldDx;
               double blockZ = this.getZ() + worldDz;

               for (int dy = 2; dy >= -2; dy--) {
                  BlockPos pos = BlockPos.ofFloored(blockX, this.getY() + dy, blockZ);
                  BlockState state = this.getWorld().getBlockState(pos);
                  if (state.isOf(Blocks.GRASS_BLOCK)) {
                     this.getWorld().setBlockState(pos, Blocks.FARMLAND.getDefaultState(), 3);
                     serverLevel.spawnParticles(
                        new BlockStateParticleEffect(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 3, 0.3, 0.1, 0.3, 0.02
                     );
                     break;
                  }

                  if (state.isSolid()) {
                     break;
                  }
               }
            }
         }
      }
   }

   @Override
   public boolean damage(DamageSource source, float amount) {
      Entity attacker = source.getAttacker();
      if (!(attacker instanceof AttackTitanEntity)
         && !(attacker instanceof ArmoredTitanEntity)
         && !(attacker instanceof ColossalTitanEntity)
         && !(attacker instanceof FemaleTitanEntity)
         && !(attacker instanceof BeastTitanEntity)
         && !(attacker instanceof WarhammerTitanEntity)
         && !(attacker instanceof OgreTitanEntity)) {
         return amount < 1.7014117E38F && !source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY) ? false : super.damage(source, amount);
      } else if (this.dataTracker.get(DATA_IS_DEAD)) {
         return false;
      } else if (amount == Float.MAX_VALUE) {
         return false;
      } else {
         float shifterDamage = this.getMaxHealth() / 3.0F;
         float newHealth = this.getHealth() - shifterDamage;
         if (newHealth <= 0.0F) {
            this.setHealth(0.0F);
            this.onDeath(source);
         } else {
            this.setHealth(newHealth);
         }

         return false;
      }
   }

   @Override
   public void onDeath(DamageSource damageSource) {
      this.dataTracker.set(DATA_IS_DEAD, true);
      if (this.eatingTarget != null) {
         this.eatingTarget.stopRiding();
         this.eatingTarget.removeScoreboardTag("dannysaot_being_grabbed");
      }

      this.setDismountAllowed(true);

      for (Entity passenger : this.getPassengerList()) {
         if (passenger instanceof ServerPlayerEntity sp && this.isSelfInjectionPassenger(sp)) {
            sp.setInvisible(false);
            sp.stopRiding();
            sp.kill();
         }
      }

      super.onDeath(damageSource);
   }

   @Override
   public void remove(RemovalReason reason) {
      if (this.napeEntity != null && !this.napeEntity.isRemoved()) {
         this.napeEntity.discard();
      }

      if (this.eyeEntity != null && !this.eyeEntity.isRemoved()) {
         this.eyeEntity.discard();
      }

      super.remove(reason);
   }

   @Override
   public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason, EntityData entityData, net.minecraft.nbt.NbtCompound entityNbt) {
      entityData = super.initialize(world, difficulty, spawnReason, entityData, null);
      this.forwardYaw = this.random.nextFloat() * 360.0F;
      this.walkTimer = 400 + this.random.nextInt(600);
      if (!this.getWorld().isClient()) {
         CrawlerTitanNapeEntity nape = new CrawlerTitanNapeEntity(DannysAot.CRAWLER_TITAN_NAPE, this.getWorld());
         nape.setParentTitan(this);
         nape.setPosition(this.getX(), this.getY(), this.getZ());
         this.getWorld().spawnEntity(nape);
         this.napeEntity = nape;
         CrawlerTitanEyeEntity eye = new CrawlerTitanEyeEntity(DannysAot.CRAWLER_TITAN_EYE, this.getWorld());
         eye.setParentTitan(this);
         eye.setPosition(this.getX(), this.getY(), this.getZ());
         this.getWorld().spawnEntity(eye);
         this.eyeEntity = eye;
      }

      return entityData;
   }

   @Override
   public void onPassengerLookAround(Entity passenger) {
      if (this.isRideable && passenger instanceof LivingEntity living) {
         living.bodyYaw = this.getYaw();
      }
   }

   @Override
   protected boolean canAddPassenger(Entity passenger) {
      return this.getPassengerList().size() < 1;
   }

   public void registerControllers(ControllerRegistrar controllers) {
      controllers.add(new AnimationController(this, "controller", 5, state -> {
         if (this.isDead()) {
            return PlayState.STOP;
         } else if (this.isAiDisabled()) {
            state.getController().setAnimation(NIGHT_SLEEP_ANIM);
            return PlayState.CONTINUE;
         } else if (this.isEating()) {
            state.getController().setAnimation(EAT_ANIM);
            return PlayState.CONTINUE;
         } else if (this.measuredSpeedSqr() > 1.0E-4) {
            state.getController().setAnimation(WALK_ANIM);
            state.getController().setAnimationSpeed(this.isRideable ? 9.0 : 1.0);
            return PlayState.CONTINUE;
         } else {
            state.getController().setAnimation(IDLE_ANIM);
            return PlayState.CONTINUE;
         }
      }));
   }

   private double measuredSpeedSqr() {
      if (this.age != this.animSpeedTick) {
         this.animSpeedTick = this.age;
         double dx = this.getX() - this.lastRenderX;
         double dz = this.getZ() - this.lastRenderZ;
         this.animSpeedSqr = this.animSpeedSqr * 0.7 + (dx * dx + dz * dz) * 0.3;
      }

      return this.animSpeedSqr;
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.cache;
   }

   @Override
   public void writeCustomDataToNbt(NbtCompound nbt) {
      super.writeCustomDataToNbt(nbt);
      nbt.putInt("NightSleepStart", this.nightSleepStart);
      nbt.putInt("NightSleepEnd", this.nightSleepEnd);
      nbt.putBoolean("IsNightSleeping", this.isNightSleeping);
   }

   @Override
   public void readCustomDataFromNbt(NbtCompound nbt) {
      super.readCustomDataFromNbt(nbt);
      if (nbt.contains("NightSleepStart")) {
         this.nightSleepStart = nbt.getInt("NightSleepStart");
      }

      if (nbt.contains("NightSleepEnd")) {
         this.nightSleepEnd = nbt.getInt("NightSleepEnd");
      }

      this.isNightSleeping = nbt.getBoolean("IsNightSleeping");
   }

   @Override
   public boolean canImmediatelyDespawn(double distanceSquared) {
      return !this.isRideable;
   }

   private void tickContactDamage() {
      if (!this.dataTracker.get(DATA_IS_DEAD)) {
         Iterator var1 = this.getWorld()
            .getEntitiesByClass(
               LivingEntity.class,
               this.getBoundingBox().expand(1.0),
               e -> e != this
                  && (
                     e instanceof AttackTitanEntity
                        || e instanceof ArmoredTitanEntity
                        || e instanceof ColossalTitanEntity
                        || e instanceof FemaleTitanEntity
                        || e instanceof BeastTitanEntity
                        || e instanceof WarhammerTitanEntity
                  )
            )
            .iterator();
         if (var1.hasNext()) {
            LivingEntity target = (LivingEntity)var1.next();
            target.damage(this.getDamageSources().mobAttack(this), 10.0F);
            this.contactDamageCooldown = 40;
         }
      }
   }

   @Override
   public boolean isPushable() {
      return false;
   }

   public static boolean checkCrawlerSpawnRules(
      EntityType<CrawlerTitanEntity> type, ServerWorldAccess level, SpawnReason spawnType, BlockPos pos, Random random
   ) {
      if (!BreachManager.isNaturalSpawnAllowed(type, level, pos.getX(), pos.getZ())) {
         return false;
      } else {
         int playerCount = level.toServerWorld().getPlayers().size();
         int maxTitans = TitanEntity.getEffectiveMaxTitans(playerCount);
         return TitanEntity.getLoadedTitanCount() >= maxTitans ? false : TitanEntity.tryClaimSpawnTick(level);
      }
   }

   @Override protected void updatePassengerPosition(Entity passenger, PositionUpdater positionUpdater) {
      if (!this.hasPassenger(passenger)) return;
      Vec3d bpRidingPos = this.getPassengerRidingPos(passenger);
      positionUpdater.accept(passenger, bpRidingPos.x, bpRidingPos.y, bpRidingPos.z);
   }
}
