package daot;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer.Builder;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import software.bernie.geckolib.core.animation.RawAnimation;

public class CrawlingAbnormalTitanEntity extends AbnormalTitanEntity {
   private static final double CRAWLING_RUN_CYCLE = 1.0;
   private static final double[] CRAWLING_RUN_STOMPS = new double[]{0.0, 0.25, 0.5, 0.75};

   public CrawlingAbnormalTitanEntity(EntityType<? extends HostileEntity> entityType, World level) {
      super(entityType, level);
   }

   public static Builder createAttributes() {
      return AbnormalTitanEntity.createAttributes();
   }

   public static boolean checkCrawlingAbnormalTitanSpawnRules(
      EntityType<CrawlingAbnormalTitanEntity> entityType, ServerWorldAccess level, SpawnReason spawnType, BlockPos pos, Random random
   ) {
      int playerCount = level.toServerWorld().getPlayers().size();
      int maxTitans = TitanEntity.getEffectiveMaxTitans(playerCount);
      if (TitanEntity.getLoadedTitanCount() >= maxTitans) {
         return false;
      } else if (!BreachManager.isNaturalSpawnAllowed(entityType, level, pos.getX(), pos.getZ())) {
         return false;
      } else {
         return random.nextFloat() >= 0.01F ? false : TitanEntity.tryClaimSpawnTick(level);
      }
   }

   @Override
   protected boolean hasHoldPhase() {
      return false;
   }

   @Override
   protected RawAnimation getSwoopAnimation() {
      return RUN_ANIM;
   }

   @Override
   protected RawAnimation getPullToEatAnimation() {
      return RUN_ANIM;
   }

   @Override
   protected RawAnimation getEatAnimation() {
      return EAT_ANIM;
   }

   @Override
   protected RawAnimation getMissedGrabAnimation() {
      return IDLE_ANIM;
   }

   @Override
   protected double getChaseSpeed() {
      return super.getChaseSpeed() * 2.0;
   }

   @Override
   protected double getRunCycleSeconds() {
      return 1.0;
   }

   @Override
   protected double[] getRunStompKeyframes() {
      return CRAWLING_RUN_STOMPS;
   }

   @Override
   protected boolean usesAlternatingStompFoot() {
      return true;
   }

   @Override
   protected void initGoals() {
      super.initGoals();
      this.targetSelector.add(3, new ActiveTargetGoal<>(this, AbstractHorseEntity.class, true));
   }

   @Override
   protected float getEatingDamageMultiplier() {
      return 2.0F;
   }

   public Vec3d getPassengerRidingPos(Entity passenger) {
      Vec3d base = this.getPos().add(0, this.getMountedHeightOffset() + passenger.getHeightOffset(), 0);
      float yawRad = (float)Math.toRadians(this.getYaw());
      double forwardX = -Math.sin(yawRad) * 1.5;
      double forwardZ = Math.cos(yawRad) * 1.5;
      return base.add(forwardX, 8.0, forwardZ);
   }

   @Override
   protected FritzTitanNapeEntity createNapeHitbox() {
      return new CrawlingAbnormalTitanNapeEntity(DannysAot.CRAWLING_ABNORMAL_TITAN_NAPE, this.getWorld());
   }

   @Override public void updatePassengerPosition(Entity passenger, PositionUpdater positionUpdater) {
      if (!this.hasPassenger(passenger)) return;
      Vec3d bpRidingPos = this.getPassengerRidingPos(passenger);
      positionUpdater.accept(passenger, bpRidingPos.x, bpRidingPos.y, bpRidingPos.z);
   }
}
