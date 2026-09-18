package daot;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.DefaultAttributeContainer.Builder;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;

public class AbnormalTitanEntity extends FritzTitanEntity {
   public AbnormalTitanEntity(EntityType<? extends HostileEntity> entityType, World level) {
      super(entityType, level);
   }

   public static Builder createAttributes() {
      return FritzTitanEntity.createAttributes();
   }

   public static boolean checkAbnormalTitanSpawnRules(
      EntityType<AbnormalTitanEntity> entityType, ServerWorldAccess level, SpawnReason spawnType, BlockPos pos, Random random
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
   protected boolean hasEyeHitbox() {
      return false;
   }

   @Override
   protected double getChaseSpeed() {
      return super.getChaseSpeed() * 2.0 * fogSpeedFactor();
   }

   @Override
   protected double getWanderSpeed() {
      return super.getWanderSpeed() * fogSpeedFactor();
   }

   private static double fogSpeedFactor() {
      return FogEventState.isActive() ? 1.75 : 1.0;
   }

   @Override
   protected FritzTitanNapeEntity createNapeHitbox() {
      return new AbnormalTitanNapeEntity(DannysAot.ABNORMAL_TITAN_NAPE, this.getWorld());
   }
}
