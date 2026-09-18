package daot;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Entity.PositionUpdater;
import net.minecraft.entity.Entity.RemovalReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.boss.BossBar.Color;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar;

public class CartShifterTitanEntity extends TestShifterTitanEntity implements daot.compat.BaseDimensionsProvider {
   private static final RawAnimation CART_IDLE = RawAnimation.begin().thenLoop("idle");
   private static final RawAnimation CART_WALK = RawAnimation.begin().thenLoop("walk");
   private static final RawAnimation CART_RUN = RawAnimation.begin().thenLoop("run");
   private static final RawAnimation CART_DISMOUNT = RawAnimation.begin().thenLoop("dismount");
   private static final RawAnimation CART_SHIFT = RawAnimation.begin().thenPlayAndHold("shift");
   private static final RawAnimation CART_CHOMP = RawAnimation.begin().thenPlayAndHold("chomp");
   private static final RawAnimation CART_CHOMP2 = RawAnimation.begin().thenPlayAndHold("chomp2");
   private static final RawAnimation CART_HANG = RawAnimation.begin().thenLoop("hang");
   private static final RawAnimation CART_LEFT_HANG = RawAnimation.begin().thenLoop("left_hang");
   private static final RawAnimation CART_RIGHT_HANG = RawAnimation.begin().thenLoop("right_hang");
   private static final RawAnimation CART_HANG_JUMP = RawAnimation.begin().thenPlayAndHold("hang_jump");
   private static final TrackedData<Boolean> DATA_CHOMPING = DataTracker.registerData(CartShifterTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final TrackedData<Boolean> DATA_CHOMP_SECOND = DataTracker.registerData(CartShifterTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private static final int CHOMP_DURATION_TICKS = 20;
   private static final int CHOMP_SOUND_TICK = 13;
   private static final int CHOMP_WINDOW_START = 6;
   private static final int CHOMP_WINDOW_END = 18;
   private int chompTicks = 0;
   private int chompElapsed = 0;
   private boolean nextChompIsSecond = false;
   private boolean chompSoundPlayed = false;
   private boolean chompConnected = false;
   private static final TrackedData<Boolean> DATA_CARGO = DataTracker.registerData(CartShifterTitanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   private final SimpleInventory cargoContainer = new SimpleInventory(54) {
      @Override
      public boolean canPlayerUse(PlayerEntity player) {
         return !CartShifterTitanEntity.this.isRemoved()
            && CartShifterTitanEntity.this.isCargo()
            && player.squaredDistanceTo(CartShifterTitanEntity.this) < 144.0;
      }
   };
   private static final double RUN_ANIM_SPEED_MULT = 1.15;
   private static final double WALK_ANIM_SPEED_MULT = 1.15;
   private static final EntityDimensions CART_DIMENSIONS = EntityDimensions.changing(2.4F, 4.0F);
   private static final double CART_WALK_SPEED = 0.25875;
   private static final double CART_RUN_SPEED = 1.173;
   private static final double CART_SPEED_LERP_STEP = 0.152375;
   private double cartCurrentSpeed = 0.25875;
   private static final double NORMAL_RIDER_Y = 4.4;
   private static final double DISMOUNT_RIDER_Y = 2.4;
   private static final double NORMAL_FORWARD_OFFSET = 0.4;
   private static final double DISMOUNT_FORWARD_OFFSET = 1.4;
   private static final float CHOMP_DAMAGE = 30.0F;
   private static final double CHOMP_REACH = 6.0;
   private static final double CHOMP_HALF_WIDTH = 1.5;

   public CartShifterTitanEntity(EntityType<? extends HostileEntity> entityType, World level) {
      super(entityType, level);
   }

   @Override
   protected void initDataTracker() {
      super.initDataTracker();
      this.dataTracker.startTracking(DATA_CHOMPING, false);
      this.dataTracker.startTracking(DATA_CHOMP_SECOND, false);
      this.dataTracker.startTracking(DATA_CARGO, false);
   }

   public static net.minecraft.entity.attribute.DefaultAttributeContainer.Builder createAttributes() {
      return HostileEntity.createHostileAttributes()
         .add(EntityAttributes.GENERIC_MAX_HEALTH, 200.0)
         .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 1.173)
         .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 5.0)
         .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0)
         .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
         .add(daot.compat.attributes.DaotEntityAttributes.STEP_HEIGHT, 5.0)
         .add(daot.compat.attributes.DaotEntityAttributes.GRAVITY, 0.65)
         .add(daot.compat.attributes.DaotEntityAttributes.WATER_MOVEMENT_EFFICIENCY, 1.0);
   }

   @Override
   public EntityDimensions getBaseDimensions(EntityPose pose) {
      return CART_DIMENSIONS;
   }

   @Override
   protected float getJumpVerticalPower() {
      return super.getJumpVerticalPower() * 1.5F;
   }

   @Override
   protected boolean shiftExplosionDamagesEntities() {
      return false;
   }

   @Override
   protected float shiftExplosionDamageScale() {
      return 0.1F;
   }

   @Override
   public void spawnHitboxes() {
      super.spawnHitboxes();
      if (!this.getWorld().isClient() && this.grabHitboxEntity == null) {
         AttackTitanGrabEntity grab = new AttackTitanGrabEntity(DannysAot.ATTACK_TITAN_GRAB, this.getWorld());
         grab.setParentTitan(this);
         grab.setPosition(this.getX(), this.getY(), this.getZ());
         this.getWorld().spawnEntity(grab);
         this.grabHitboxEntity = grab;
      }
   }

   @Override
   protected float getSaddledSpeed(PlayerEntity controllingPlayer) {
      double target = this.isSprinting() ? 1.173 : 0.25875;
      if (this.cartCurrentSpeed < target) {
         this.cartCurrentSpeed = Math.min(this.cartCurrentSpeed + 0.152375, target);
      } else if (this.cartCurrentSpeed > target) {
         this.cartCurrentSpeed = Math.max(this.cartCurrentSpeed - 0.152375, target);
      }

      return (float)this.cartCurrentSpeed;
   }

   @Override
   public void updatePassengerPosition(Entity passenger, PositionUpdater positionUpdater) {
      if (this.hasPassenger(passenger)) {
         float yawRad = (float)Math.toRadians(this.getYaw());
         double forwardOffset = this.isDismounting() ? 1.4 : 0.4;
         double offsetX = -Math.sin(yawRad) * forwardOffset;
         double offsetZ = Math.cos(yawRad) * forwardOffset;
         double riderY = this.isDismounting() ? 2.4 : 4.4;
         positionUpdater.accept(passenger, this.getX() + offsetX, this.getY() + riderY, this.getZ() + offsetZ);
      }
   }

   public Vec3d getPassengerRidingPos(Entity passenger) {
      double y = this.isDismounting() ? 2.4 : 4.4;
      return this.getPos().add(0.0, y, 0.0);
   }

   @Override
   public void triggerAttack() {
   }

   @Override
   public void triggerAbility(int abilityNumber) {
      if (!this.getWorld().isClient()) {
         if (!this.isTransforming() && !this.isDismounting()) {
            if (abilityNumber == 1) {
               if (this.isGrabbing()) {
                  this.releaseGrabbedEntity();
               } else if (!this.isChomping()) {
                  this.startChomp();
               }
            } else if (abilityNumber == 2) {
               this.toggleCargo();
            }
         }
      }
   }

   private boolean chompBite() {
      if (this.getWorld().isClient()) {
         return false;
      } else if (this.isGrabbing()) {
         return false;
      } else {
         float yawRad = (float)Math.toRadians(this.getYaw());
         double fwdX = -Math.sin(yawRad);
         double fwdZ = Math.cos(yawRad);
         LivingEntity controller = this.getControllingPassenger();
         Box box = this.getBoundingBox().expand(6.0, 3.0, 6.0);
         Entity best = null;
         double bestForward = Double.MAX_VALUE;

         for (Entity e : this.getWorld().getOtherEntities(this, box)) {
            if (e != controller) {
               boolean grabbable = FemaleTitanEntity.canBeGrabbedByFemale(e);
               boolean titan = this.isChompTitanTarget(e);
               if (grabbable || titan) {
                  double dx = e.getX() - this.getX();
                  double dz = e.getZ() - this.getZ();
                  double forwardDist = dx * fwdX + dz * fwdZ;
                  if (!(forwardDist < 0.0) && !(forwardDist > 6.0)) {
                     double lateral = Math.abs(dx * fwdZ - dz * fwdX);
                     if (!(lateral > 1.5) && forwardDist < bestForward) {
                        best = e;
                        bestForward = forwardDist;
                     }
                  }
               }
            }
         }

         if (best == null) {
            return false;
         } else {
            if (this.isChompTitanTarget(best)) {
               float dmg = best instanceof ShifterTitan ? 15.0F : 30.0F;
               best.damage(this.getDamageSources().mobAttack(this), dmg);
            } else {
               this.grabEntity(best);
            }

            return true;
         }
      }
   }

   private boolean isChompTitanTarget(Entity e) {
      return e != this && e instanceof LivingEntity && e.isAlive()
         ? e instanceof AttackTitanEntity
            || e instanceof ArmoredTitanEntity
            || e instanceof ColossalTitanEntity
            || e instanceof FemaleTitanEntity
            || e instanceof BeastTitanEntity
            || e instanceof WarhammerTitanEntity
            || e instanceof OgreShifterTitanEntity
            || e instanceof TripleTTitanEntity
            || e instanceof TitanEntity
            || e instanceof SmallTitanEntity
            || e instanceof SmallTitan2Entity
            || e instanceof FritzTitanEntity
            || e instanceof YellowTitanEntity
            || e instanceof SadTitanEntity
            || e instanceof CrawlerTitanEntity
            || e instanceof OgreTitanEntity
            || e instanceof AbnormalTitanEntity
            || e instanceof ConnieFatherEntity
         : false;
   }

   public boolean isCargo() {
      return this.dataTracker.get(DATA_CARGO);
   }

   private void toggleCargo() {
      boolean newState = !this.isCargo();
      this.dataTracker.set(DATA_CARGO, newState);
      if (!newState) {
         this.dropCargo();
      }
   }

   @Override
   protected ActionResult interactMob(PlayerEntity player, Hand hand) {
      if (!this.isCargo()) {
         return super.interactMob(player, hand);
      } else {
         if (!this.getWorld().isClient() && player instanceof ServerPlayerEntity sp) {
            sp.openHandledScreen(
               new SimpleNamedScreenHandlerFactory(
                  (containerId, inv, p) -> GenericContainerScreenHandler.createGeneric9x6(containerId, inv, this.cargoContainer), Text.literal("Cargo")
               )
            );
         }

         return ActionResult.success(this.getWorld().isClient());
      }
   }

   private void dropCargo() {
      if (!this.getWorld().isClient()) {
         for (int i = 0; i < this.cargoContainer.size(); i++) {
            ItemStack stack = this.cargoContainer.getStack(i);
            if (!stack.isEmpty()) {
               ItemEntity item = new ItemEntity(this.getWorld(), this.getX(), this.getY() + 1.0, this.getZ(), stack.copy());
               item.setToDefaultPickupDelay();
               this.getWorld().spawnEntity(item);
               this.cargoContainer.setStack(i, ItemStack.EMPTY);
            }
         }
      }
   }

   @Override
   public void remove(RemovalReason reason) {
      if (reason.shouldDestroy() && !this.getWorld().isClient()) {
         this.dropCargo();
         if (this.isGrabbing()) {
            this.releaseGrabbedEntity();
         }
      }

      super.remove(reason);
   }

   @Override
   public void writeCustomDataToNbt(NbtCompound nbt) {
      super.writeCustomDataToNbt(nbt);
      nbt.putBoolean("Cargo", this.isCargo());
      nbt.put("CargoItems", this.cargoContainer.toNbtList());
   }

   @Override
   public void readCustomDataFromNbt(NbtCompound nbt) {
      super.readCustomDataFromNbt(nbt);
      this.dataTracker.set(DATA_CARGO, nbt.getBoolean("Cargo"));
      if (nbt.contains("CargoItems", 9)) {
         this.cargoContainer.readNbtList(nbt.getList("CargoItems", 10));
      }
   }

   private void startChomp() {
      boolean second = this.nextChompIsSecond;
      this.nextChompIsSecond = !this.nextChompIsSecond;
      this.dataTracker.set(DATA_CHOMP_SECOND, second);
      this.dataTracker.set(DATA_CHOMPING, true);
      this.chompTicks = 20;
      this.chompElapsed = 0;
      this.chompSoundPlayed = false;
      this.chompConnected = false;
   }

   public boolean isChomping() {
      return this.dataTracker.get(DATA_CHOMPING);
   }

   public boolean isChompSecond() {
      return this.dataTracker.get(DATA_CHOMP_SECOND);
   }

   @Override
   public void tick() {
      super.tick();
      if (!this.getWorld().isClient() && this.isChomping()) {
         this.chompElapsed++;
         if (!this.chompSoundPlayed && this.chompElapsed >= 13) {
            this.chompSoundPlayed = true;
            this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.BITE, SoundCategory.HOSTILE, 4.0F, 1.0F);
         }

         if (!this.chompConnected && this.chompElapsed >= 6 && this.chompElapsed <= 18 && this.chompBite()) {
            this.chompConnected = true;
         }

         if (--this.chompTicks <= 0) {
            this.dataTracker.set(DATA_CHOMPING, false);
            this.chompElapsed = 0;
         }
      }
   }

   @Override
   protected boolean usesKneelDismount() {
      return false;
   }

   @Override
   public boolean usesKeyframeFootsteps() {
      return false;
   }

   @Override
   protected int getSimpleStompIntervalTicks(boolean isRunning) {
      return isRunning ? 10 : 15;
   }

   @Override
   public void onPlayerShift(PlayerEntity player) {
      super.onPlayerShift(player);
      if (this.bossBar != null) {
         this.bossBar.clearPlayers();
         this.bossBar = null;
      }

      if (!this.getWorld().isClient() && this.getWorld().getGameRules().getBoolean(DannysAot.RULE_SHIFT_BOSS_BARS)) {
         Text title = Text.literal("Cart Titan").fillStyle(Style.EMPTY.withColor(TextColor.fromRgb(16119260)));
         this.bossBar = new ServerBossBar(title, Color.WHITE, net.minecraft.entity.boss.BossBar.Style.PROGRESS);
         this.bossBar.setPercent(1.0F);
      }
   }

   @Override
   public void registerControllers(ControllerRegistrar controllers) {
      controllers.add(new AnimationController(this, "movement", 6, this::cartMovementPredicate));
   }

   private RawAnimation resolveCartHangAnimation() {
      if (!this.isLatched()) {
         return null;
      } else {
         float nx = this.getLatchNormalX();
         float nz = this.getLatchNormalZ();
         if (nx == 0.0F && nz == 0.0F) {
            return null;
         } else {
            double faceX = -nx;
            double faceZ = -nz;
            double yawRad = Math.toRadians(this.getYaw());
            double forwardX = -Math.sin(yawRad);
            double forwardZ = Math.cos(yawRad);
            double dot = forwardX * faceX + forwardZ * faceZ;
            if (dot > Math.cos(Math.toRadians(45.0))) {
               return CART_HANG;
            } else {
               double cross = forwardX * faceZ - forwardZ * faceX;
               return cross > 0.0 ? CART_RIGHT_HANG : CART_LEFT_HANG;
            }
         }
      }
   }

   private PlayState cartMovementPredicate(AnimationState<CartShifterTitanEntity> state) {
      state.getController().setAnimationSpeed(1.0);
      if (this.isTransforming()) {
         return state.setAndContinue(CART_SHIFT);
      } else if (this.isDismounting()) {
         return state.setAndContinue(CART_DISMOUNT);
      } else if (this.isHangJumping()) {
         return state.setAndContinue(CART_HANG_JUMP);
      } else {
         RawAnimation hang = this.resolveCartHangAnimation();
         if (hang != null) {
            return state.setAndContinue(hang);
         } else if (this.isChomping()) {
            return state.setAndContinue(this.isChompSecond() ? CART_CHOMP2 : CART_CHOMP);
         } else if (this.isMoving()) {
            if (this.isSprinting()) {
               state.getController().setAnimationSpeed(1.15);
               return state.setAndContinue(CART_RUN);
            } else {
               state.getController().setAnimationSpeed(1.15);
               return state.setAndContinue(CART_WALK);
            }
         } else {
            return state.setAndContinue(CART_IDLE);
         }
      }
   }
}
