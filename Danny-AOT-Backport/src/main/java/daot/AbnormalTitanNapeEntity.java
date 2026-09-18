package daot;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.world.World;

public class AbnormalTitanNapeEntity extends FritzTitanNapeEntity {
   private static final double ABNORMAL_NAPE_HEIGHT_OFFSET = 8.433333333333334;
   private static final double ABNORMAL_NAPE_BACK_OFFSET = 0.8799999999999999;

   public AbnormalTitanNapeEntity(EntityType<? extends MobEntity> entityType, World level) {
      super(entityType, level);
   }

   @Override
   protected double getNapeHeightOffset() {
      return 8.433333333333334;
   }

   @Override
   protected double getNapeBackOffset() {
      return 0.8799999999999999;
   }
}
