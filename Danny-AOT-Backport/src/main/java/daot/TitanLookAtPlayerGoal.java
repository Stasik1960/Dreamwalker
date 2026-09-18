package daot;

import java.util.EnumSet;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.Goal.Control;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;

public class TitanLookAtPlayerGoal extends Goal {
   private final MobEntity mob;
   private final double range;
   private LivingEntity target;

   public TitanLookAtPlayerGoal(MobEntity mob, double range) {
      this.mob = mob;
      this.range = range;
      this.setControls(EnumSet.of(Control.LOOK));
   }

   private boolean held() {
      return this.mob instanceof EyeHurtTitan eh && eh.isEyeHurt()
         ? true
         : this.mob.getCommandTags().contains("dannysaot_commanded_stop") || VillagerTransformTracker.isRegrouping(this.mob);
   }

   @Override
   public boolean canStart() {
      if (this.held()) {
         return false;
      } else {
         this.target = this.pickLookTarget();
         return this.target != null;
      }
   }

   @Override
   public boolean shouldContinue() {
      if (this.held()) {
         return false;
      } else {
         this.target = this.pickLookTarget();
         return this.target != null;
      }
   }

   @Override
   public boolean shouldRunEveryTick() {
      return true;
   }

   @Override
   public void tick() {
      if (this.target != null) {
         this.mob.getLookControl().lookAt(this.target, this.mob.getMaxHeadRotation(), this.mob.getMaxLookPitchChange());
      }
   }

   private LivingEntity pickLookTarget() {
      LivingEntity combatTarget = this.mob.getTarget();
      return combatTarget != null
            && combatTarget.isAlive()
            && this.mob.squaredDistanceTo(combatTarget) <= this.range * this.range
            && this.mob.getVisibilityCache().canSee(combatTarget)
         ? combatTarget
         : this.nearestVisibleTarget();
   }

   private LivingEntity nearestVisibleTarget() {
      LivingEntity nearest = null;
      double best = this.range * this.range;

      for (PlayerEntity p : this.mob.getWorld().getPlayers()) {
         if (p.isAlive() && !p.isSpectator() && !VanishManager.isVanished(p.getUuid())) {
            double d = this.mob.squaredDistanceTo(p);
            if (d <= best && this.mob.getVisibilityCache().canSee(p)) {
               best = d;
               nearest = p;
            }
         }
      }

      Box box = this.mob.getBoundingBox().expand(this.range);

      for (MerchantEntity v : this.mob.getWorld().getEntitiesByClass(MerchantEntity.class, box, LivingEntity::isAlive)) {
         double d = this.mob.squaredDistanceTo(v);
         if (d <= best && this.mob.getVisibilityCache().canSee(v)) {
            best = d;
            nearest = v;
         }
      }

      return nearest;
   }
}
