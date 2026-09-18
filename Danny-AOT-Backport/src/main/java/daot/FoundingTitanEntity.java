package daot;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer.Builder;
import net.minecraft.entity.boss.BossBar.Color;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

public class FoundingTitanEntity extends AttackTitanEntity {
   public FoundingTitanEntity(EntityType<? extends HostileEntity> entityType, World level) {
      super(entityType, level);
   }

   @Override
   public void spawnHitboxes() {
      super.spawnHitboxes();
      if (this.bossBar != null) {
         this.bossBar.setColor(Color.PURPLE);
         if (this.getFirstPassenger() instanceof PlayerEntity player) {
            this.bossBar.setName(Text.literal("Founding Titan - " + player.getName().getString()));
         } else {
            this.bossBar.setName(Text.literal("Founding Titan"));
         }
      }
   }

   @Override
   public void onPlayerShift(PlayerEntity player) {
      super.onPlayerShift(player);
      if (this.bossBar != null) {
         this.bossBar.setColor(Color.PURPLE);
         String bossBarName = player.getCommandTags().contains("titan_stealth") ? "Founding Titan" : "Founding Titan - " + player.getName().getString();
         this.bossBar.setName(Text.literal(bossBarName));
      }
   }

   @Override
   public void triggerAbility(int abilityNumber) {
      if (abilityNumber == 6) {
         if (this.getFirstPassenger() instanceof ServerPlayerEntity rider) {
            rider.sendMessage(Text.literal("The Founding Titan cannot harden.").formatted(Formatting.RED), true);
         }
      } else {
         super.triggerAbility(abilityNumber);
      }
   }

   public static Builder createAttributes() {
      return AttackTitanEntity.createAttributes();
   }

   @Override
   protected double getHealthMultiplier() {
      return 2.0;
   }

   @Override
   protected float getAttackDamageMultiplier() {
      return 2.0F;
   }
}
