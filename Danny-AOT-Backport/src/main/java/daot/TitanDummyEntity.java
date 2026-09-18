package daot;

import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.Entity.RemovalReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.util.GeckoLibUtil;

public class TitanDummyEntity extends MobEntity implements GeoEntity {
   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
   private static final RawAnimation SPIN_ANIM = RawAnimation.begin().thenLoop("spin");
   private static final RawAnimation HIT_ANIM = RawAnimation.begin().thenPlay("hit");
   private static final TrackedData<Boolean> DATA_SPINNING = DataTracker.registerData(TitanDummyEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> DATA_EYE_STUNNED = DataTracker.registerData(TitanDummyEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> DATA_NAPE_HIT = DataTracker.registerData(TitanDummyEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Integer> DATA_NAPE_HIT_COUNT = DataTracker.registerData(TitanDummyEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private int clientLastHitCount = 0;
   private BlockPos centerBlockPos;
   private TitanDummyNapeEntity napeEntity;
   private TitanDummyEyeEntity eyeEntity;
   private int eyeStunTimer = 0;
   private int napeHitTimer = 0;
   private int hitboxRespawnCooldown = 0;
   private static final int EYE_STUN_DURATION = 100;
   private static final int NAPE_HIT_DURATION = 11;

   public TitanDummyEntity(EntityType<? extends MobEntity> entityType, World level) {
      super(entityType, level);
      this.noClip = true;
      this.setNoGravity(true);
      this.setPersistent();
      this.setInvulnerable(true);
      this.setAiDisabled(true);
   }

   @Override
   protected void initDataTracker() {
      super.initDataTracker();
      this.dataTracker.startTracking(DATA_SPINNING, false);
      this.dataTracker.startTracking(DATA_EYE_STUNNED, false);
      this.dataTracker.startTracking(DATA_NAPE_HIT, false);
      this.dataTracker.startTracking(DATA_NAPE_HIT_COUNT, 0);
   }

   public static net.minecraft.entity.attribute.DefaultAttributeContainer.Builder createAttributes() {
      return MobEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 999.0).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0);
   }

   public void setCenterBlockPos(BlockPos pos) {
      this.centerBlockPos = pos;
   }

   public BlockPos getCenterBlockPos() {
      return this.centerBlockPos;
   }

   public TitanDummyNapeEntity getNapeEntity() {
      return this.napeEntity;
   }

   public TitanDummyEyeEntity getEyeEntity() {
      return this.eyeEntity;
   }

   public void spawnHitboxes() {
      if (!this.getWorld().isClient()) {
         TitanDummyNapeEntity nape = new TitanDummyNapeEntity(DannysAot.TITAN_DUMMY_NAPE, this.getWorld());
         nape.setParentDummy(this);
         nape.setPosition(this.getX(), this.getY(), this.getZ());
         this.getWorld().spawnEntity(nape);
         this.napeEntity = nape;
         TitanDummyEyeEntity eye = new TitanDummyEyeEntity(DannysAot.TITAN_DUMMY_EYE, this.getWorld());
         eye.setParentDummy(this);
         eye.setPosition(this.getX(), this.getY(), this.getZ());
         this.getWorld().spawnEntity(eye);
         this.eyeEntity = eye;
      }
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
   public void tick() {
      super.tick();
      if (!this.getWorld().isClient()) {
         if (this.centerBlockPos != null) {
            BlockState state = this.getWorld().getBlockState(this.centerBlockPos);
            if (!(state.getBlock() instanceof TitanDummyBlock)) {
               this.discard();
               return;
            }
         }

         boolean redstone = this.centerBlockPos != null && this.getWorld().isReceivingRedstonePower(this.centerBlockPos);
         if (this.napeHitTimer > 0) {
            this.napeHitTimer--;
            if (this.napeHitTimer == 0) {
               this.dataTracker.set(DATA_NAPE_HIT, false);
            }
         }

         if (this.eyeStunTimer > 0) {
            this.eyeStunTimer--;
            if (this.eyeStunTimer == 0) {
               this.dataTracker.set(DATA_EYE_STUNNED, false);
            }
         }

         boolean stunned = this.dataTracker.get(DATA_EYE_STUNNED);
         boolean hit = this.dataTracker.get(DATA_NAPE_HIT);
         this.dataTracker.set(DATA_SPINNING, redstone && !stunned && !hit);
         if (this.hitboxRespawnCooldown > 0) {
            this.hitboxRespawnCooldown--;
         } else if (this.napeEntity == null || this.napeEntity.isRemoved() || this.eyeEntity == null || this.eyeEntity.isRemoved()) {
            if (this.napeEntity != null && !this.napeEntity.isRemoved()) {
               this.napeEntity.discard();
            }

            if (this.eyeEntity != null && !this.eyeEntity.isRemoved()) {
               this.eyeEntity.discard();
            }

            this.spawnHitboxes();
            this.hitboxRespawnCooldown = 100;
         }
      }
   }

   public void triggerEyeStun() {
      this.dataTracker.set(DATA_EYE_STUNNED, true);
      this.eyeStunTimer = 100;
      this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BLOCK_WOOD_HIT, SoundCategory.BLOCKS, 1.0F, 0.8F);
   }

   public void triggerNapeHit() {
      this.dataTracker.set(DATA_NAPE_HIT, true);
      this.dataTracker.set(DATA_NAPE_HIT_COUNT, this.dataTracker.get(DATA_NAPE_HIT_COUNT) + 1);
      this.dataTracker.set(DATA_EYE_STUNNED, false);
      this.eyeStunTimer = 0;
      this.napeHitTimer = 11;
      TitanDummyNapeEntity nape = this.napeEntity;
      double sx = nape != null ? nape.getX() : this.getX();
      double sy = nape != null ? nape.getY() : this.getY();
      double sz = nape != null ? nape.getZ() : this.getZ();
      this.getWorld().playSound(null, sx, sy, sz, SoundEvents.ITEM_SHIELD_BREAK, SoundCategory.BLOCKS, 1.0F, 1.0F);
      this.getWorld().playSound(null, sx, sy, sz, SoundEvents.BLOCK_WOOD_BREAK, SoundCategory.BLOCKS, 1.0F, 1.0F);
      this.getWorld().playSound(null, sx, sy, sz, ModSounds.SLASH1, SoundCategory.PLAYERS, 0.5F, 1.0F);
   }

   @Override
   public boolean damage(DamageSource source, float amount) {
      return false;
   }

   @Override
   public boolean canHit() {
      return false;
   }

   @Override
   public boolean canImmediatelyDespawn(double distanceSquared) {
      return false;
   }

   @Override
   public boolean isPushable() {
      return false;
   }

   @Override
   public void writeCustomDataToNbt(NbtCompound nbt) {
      super.writeCustomDataToNbt(nbt);
      if (this.centerBlockPos != null) {
         nbt.putInt("CenterX", this.centerBlockPos.getX());
         nbt.putInt("CenterY", this.centerBlockPos.getY());
         nbt.putInt("CenterZ", this.centerBlockPos.getZ());
      }
   }

   @Override
   public void readCustomDataFromNbt(NbtCompound nbt) {
      super.readCustomDataFromNbt(nbt);
      if (nbt.contains("CenterX")) {
         this.centerBlockPos = new BlockPos(nbt.getInt("CenterX"), nbt.getInt("CenterY"), nbt.getInt("CenterZ"));
      }
   }

   public void registerControllers(ControllerRegistrar controllers) {
      controllers.add(new AnimationController(this, "controller", 0, this::predicate));
   }

   private <T extends GeoAnimatable> PlayState predicate(AnimationState<T> state) {
      if (this.dataTracker.get(DATA_NAPE_HIT)) {
         int hitCount = this.dataTracker.get(DATA_NAPE_HIT_COUNT);
         if (hitCount != this.clientLastHitCount) {
            this.clientLastHitCount = hitCount;
            state.getController().forceAnimationReset();
         }

         state.getController().transitionLength(0);
         state.getController().setAnimationSpeed(1.0);
         state.getController().setAnimation(HIT_ANIM);
         return PlayState.CONTINUE;
      } else if (this.dataTracker.get(DATA_EYE_STUNNED)) {
         state.getController().transitionLength(0);
         state.getController().setAnimationSpeed(0.0);
         state.getController().setAnimation(SPIN_ANIM);
         return PlayState.CONTINUE;
      } else if (this.dataTracker.get(DATA_SPINNING)) {
         state.getController().transitionLength(5);
         state.getController().setAnimationSpeed(1.0);
         state.getController().setAnimation(SPIN_ANIM);
         return PlayState.CONTINUE;
      } else {
         state.getController().transitionLength(5);
         state.getController().setAnimationSpeed(1.0);
         return PlayState.STOP;
      }
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.cache;
   }
}
