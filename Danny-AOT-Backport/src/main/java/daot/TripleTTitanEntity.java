package daot;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer.Builder;
import net.minecraft.entity.boss.BossBar.Color;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.World;

public class TripleTTitanEntity extends AttackTitanEntity {
   public TripleTTitanEntity(EntityType<? extends HostileEntity> entityType, World level) {
      super(entityType, level);
   }

   @Override
   public void spawnHitboxes() {
      super.spawnHitboxes();
      if (this.bossBar != null) {
         this.bossBar.setColor(Color.YELLOW);
         if (this.getFirstPassenger() instanceof PlayerEntity player) {
            this.bossBar.setName(Text.literal("Triple T - " + player.getName().getString()));
         } else {
            this.bossBar.setName(Text.literal("Triple T"));
         }
      }
   }

   @Override
   public void onPlayerShift(PlayerEntity player) {
      super.onPlayerShift(player);
      if (this.bossBar != null) {
         this.bossBar.setColor(Color.YELLOW);
         String bossBarName = player.getCommandTags().contains("titan_stealth") ? "Triple T" : "Triple T - " + player.getName().getString();
         this.bossBar.setName(Text.literal(bossBarName));
      }
   }

   public static Builder createAttributes() {
      return AttackTitanEntity.createAttributes();
   }
}
