package daot;

import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animation.RawAnimation;

public class TitanBeardEntity extends FritzTitanEntity {
   private static final TrackedData<Integer> DATA_TEXTURE_VARIANT = DataTracker.registerData(TitanBeardEntity.class, TrackedDataHandlerRegistry.INTEGER);
   private static final TrackedData<Integer> DATA_ABNORMAL_RUN = DataTracker.registerData(TitanBeardEntity.class, TrackedDataHandlerRegistry.INTEGER);
   public static final int TEXTURE_VARIANT_COUNT = 3;
   private static final RawAnimation RUN_ABNORMAL_1 = RawAnimation.begin().thenLoop("run_abnormal");
   private static final RawAnimation RUN_ABNORMAL_2 = RawAnimation.begin().thenLoop("run_abnormal2");
   private static final RawAnimation RUN_ABNORMAL_3 = RawAnimation.begin().thenLoop("run_abnormal3");

   public TitanBeardEntity(EntityType<? extends HostileEntity> entityType, World level) {
      super(entityType, level);
   }

   public static boolean checkTitanBeardSpawnRules(
      EntityType<TitanBeardEntity> entityType, ServerWorldAccess level, SpawnReason spawnType, BlockPos pos, Random random
   ) {
      int playerCount = level.toServerWorld().getPlayers().size();
      int maxTitans = TitanEntity.getEffectiveMaxTitans(playerCount);
      if (TitanEntity.getLoadedTitanCount() >= maxTitans) {
         return false;
      } else if (!BreachManager.isNaturalSpawnAllowed(entityType, level, pos.getX(), pos.getZ())) {
         return false;
      } else {
         return random.nextFloat() >= 0.03F ? false : TitanEntity.tryClaimSpawnTick(level);
      }
   }

   @Override
   protected void initDataTracker() {
      super.initDataTracker();
      this.dataTracker.startTracking(DATA_TEXTURE_VARIANT, 0);
      this.dataTracker.startTracking(DATA_ABNORMAL_RUN, 0);
   }

   public int getTextureVariant() {
      return this.dataTracker.get(DATA_TEXTURE_VARIANT);
   }

   public void setTextureVariant(int variant) {
      this.dataTracker.set(DATA_TEXTURE_VARIANT, variant);
   }

   public boolean isAbnormal() {
      return this.dataTracker.get(DATA_ABNORMAL_RUN) > 0;
   }

   public int getAbnormalRunVariant() {
      return this.dataTracker.get(DATA_ABNORMAL_RUN);
   }

   public void setAbnormalRunVariant(int variant) {
      this.dataTracker.set(DATA_ABNORMAL_RUN, variant);
   }

   @Nullable
   @Override
   public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason, @Nullable EntityData entityData, net.minecraft.nbt.NbtCompound entityNbt) {
      EntityData result = super.initialize(world, difficulty, spawnReason, entityData, null);
      if (!this.getWorld().isClient()) {
         this.setTextureVariant(this.random.nextInt(3));
         if (this.random.nextInt(5) == 0) {
            this.setAbnormalRunVariant(1 + this.random.nextInt(3));
         }
      }

      return result;
   }

   @Override
   protected double getChaseSpeed() {
      double base = super.getChaseSpeed();
      return this.isAbnormal() ? base * 2.0 : base;
   }

   @Override
   protected RawAnimation getRunAnimation() {
      int variant = this.getAbnormalRunVariant();
      if (variant == 0 && BloodmoonState.isActive()) {
         variant = 1 + Math.floorMod(this.getId(), 3);
      }
      return switch (variant) {
         case 1 -> RUN_ABNORMAL_1;
         case 2 -> RUN_ABNORMAL_2;
         case 3 -> RUN_ABNORMAL_3;
         default -> super.getRunAnimation();
      };
   }

   @Override
   public void writeCustomDataToNbt(NbtCompound nbt) {
      super.writeCustomDataToNbt(nbt);
      nbt.putInt("TextureVariant", this.getTextureVariant());
      nbt.putInt("AbnormalRun", this.getAbnormalRunVariant());
   }

   @Override
   public void readCustomDataFromNbt(NbtCompound nbt) {
      super.readCustomDataFromNbt(nbt);
      if (nbt.contains("TextureVariant")) {
         this.setTextureVariant(nbt.getInt("TextureVariant"));
      }

      if (nbt.contains("AbnormalRun")) {
         this.setAbnormalRunVariant(nbt.getInt("AbnormalRun"));
      }
   }

   @Override
   protected FritzTitanNapeEntity createNapeHitbox() {
      return new TitanBeardNapeEntity(DannysAot.TITAN_BEARD_NAPE, this.getWorld());
   }

   @Override
   protected FritzTitanEyeEntity createEyeHitbox() {
      return new TitanBeardEyeEntity(DannysAot.TITAN_BEARD_EYE, this.getWorld());
   }
}
