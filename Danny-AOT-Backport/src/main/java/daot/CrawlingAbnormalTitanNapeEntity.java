package daot;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.world.World;

public class CrawlingAbnormalTitanNapeEntity extends AbnormalTitanNapeEntity {
   private static final double CRAWLING_NAPE_HEIGHT_OFFSET = 4.6000000000000005;
   private static final double CRAWLING_NAPE_BACK_OFFSET = 0.48;

   public CrawlingAbnormalTitanNapeEntity(EntityType<? extends MobEntity> entityType, World level) {
      super(entityType, level);
   }

   @Override
   protected double getNapeHeightOffset() {
      return 4.6000000000000005;
   }

   @Override
   protected double getNapeBackOffset() {
      return 0.48;
   }
}
