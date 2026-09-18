package daot;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.util.GeckoLibUtil;

public class FemaleCrystalShellEntity extends Entity implements GeoEntity {
   private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
   private static final TrackedData<Optional<UUID>> DATA_SHIFTER_UUID = DataTracker.registerData(
      FemaleCrystalShellEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID
   );
   private static final TrackedData<Integer> DATA_REMAINING_TICKS = DataTracker.registerData(FemaleCrystalShellEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private boolean shattering = false;

   public boolean isShattering() {
      return this.shattering;
   }

   public FemaleCrystalShellEntity(EntityType<?> entityType, World level) {
      super(entityType, level);
      this.noClip = false;
      this.setNoGravity(false);
   }

   @Override
   protected void initDataTracker() {
      this.dataTracker.startTracking(DATA_SHIFTER_UUID, Optional.empty());
      this.dataTracker.startTracking(DATA_REMAINING_TICKS, ModConfig.get().femaleCrystalDurationSeconds * 20);
   }

   public UUID getShifterUUID() {
      return this.dataTracker.get(DATA_SHIFTER_UUID).orElse(null);
   }

   public void setShifterUUID(UUID uuid) {
      this.dataTracker.set(DATA_SHIFTER_UUID, Optional.ofNullable(uuid));
   }

   public int getRemainingTicks() {
      return this.dataTracker.get(DATA_REMAINING_TICKS);
   }

   public void setRemainingTicks(int ticks) {
      this.dataTracker.set(DATA_REMAINING_TICKS, ticks);
   }

   @Override
   public void tick() {
      super.tick();
      if (!this.hasNoGravity()) {
         this.setVelocity(this.getVelocity().add(0.0, -0.04, 0.0));
      }

      this.move(MovementType.SELF, this.getVelocity());
      if (this.isOnGround()) {
         this.setVelocity(this.getVelocity().multiply(0.5, 0.0, 0.5));
      } else {
         this.setVelocity(this.getVelocity().multiply(0.98, 0.98, 0.98));
      }

      if (!this.getWorld().isClient()) {
         UUID shifterUUID = this.getShifterUUID();
         if (shifterUUID == null) {
            return;
         }

         int remaining = this.getRemainingTicks();
         ServerWorld serverLevel = (ServerWorld)this.getWorld();
         ServerPlayerEntity player = serverLevel.getServer().getPlayerManager().getPlayer(shifterUUID);
         if (player != null) {
            if (player.getVehicle() != this) {
               player.startRiding(this, true);
            }

            player.setInvulnerable(true);
            player.setInvisible(true);
            int seconds = Math.max(0, (remaining + 19) / 20);
            player.sendMessage(Text.literal("Crystallization (" + seconds + ")").styled(style -> style.withColor(5614335)), true);
         }

         this.setRemainingTicks(--remaining);
         if (remaining > 0 && remaining <= 200) {
            int crackInterval = remaining <= 60 ? 10 : 30;
            if (remaining % crackInterval == 0) {
               float pitch = 0.6F + (1.0F - remaining / 200.0F) * 0.8F;
               this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, 1.5F, pitch);
               if (this.getWorld() instanceof ServerWorld sl) {
                  sl.spawnParticles(ParticleTypes.SNOWFLAKE, this.getX(), this.getY() + 1.5, this.getZ(), 5, 0.5, 0.8, 0.5, 0.01);
               }
            }
         }

         if (remaining <= 0) {
            this.shatter();
         }
      }
   }

   private void shatter() {
      if (!this.getWorld().isClient()) {
         ServerWorld serverLevel = (ServerWorld)this.getWorld();
         this.shattering = true;
         UUID shifterUUID = this.getShifterUUID();
         if (shifterUUID != null) {
            ServerPlayerEntity player = serverLevel.getServer().getPlayerManager().getPlayer(shifterUUID);
            if (player != null) {
               player.stopRiding();
               player.setInvulnerable(false);
               player.setInvisible(false);
               player.requestTeleport(this.getX(), this.getY(), this.getZ());
               player.sendMessage(Text.literal("The crystal shatters!"), true);
            }
         }

         this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, 3.0F, 0.5F);
         this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, 3.0F, 0.7F);
         serverLevel.spawnParticles(ParticleTypes.SNOWFLAKE, this.getX(), this.getY() + 1.5, this.getZ(), 60, 1.5, 2.0, 1.5, 0.05);
         serverLevel.spawnParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 1.0, this.getZ(), 30, 1.0, 1.5, 1.0, 0.08);
         this.discard();
      }
   }

   @Override
   protected boolean canAddPassenger(Entity passenger) {
      return this.getPassengerList().isEmpty();
   }

   @Override
   public Vec3d updatePassengerForDismount(LivingEntity passenger) {
      return this.getPos();
   }

   @Override
   protected void updatePassengerPosition(Entity passenger, PositionUpdater updater) {
      if (this.hasPassenger(passenger)) updater.accept(passenger, this.getX(), this.getY()+1.0, this.getZ());
   }

   @Override
   public boolean canHit() {
      return false;
   }

   @Override
   public boolean isPushable() {
      return false;
   }

   @Override
   public boolean damage(DamageSource source, float amount) {
      return false;
   }

   @Override
   public boolean isInvulnerable() {
      return true;
   }

   @Override
   public boolean shouldRender(double distance) {
      return distance < 65536.0;
   }

   @Override
   protected void readCustomDataFromNbt(NbtCompound nbt) {
      if (nbt.contains("ShifterUUID")) {
         this.setShifterUUID(nbt.getUuid("ShifterUUID"));
      }

      if (nbt.contains("RemainingTicks")) {
         this.setRemainingTicks(nbt.getInt("RemainingTicks"));
      }
   }

   @Override
   protected void writeCustomDataToNbt(NbtCompound nbt) {
      UUID shifter = this.getShifterUUID();
      if (shifter != null) {
         nbt.putUuid("ShifterUUID", shifter);
      }

      nbt.putInt("RemainingTicks", this.getRemainingTicks());
   }

   public void registerControllers(ControllerRegistrar controllers) {
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.geoCache;
   }
}
