package daot.mixin;

import daot.SmallTitanEntity;
import daot.TitanEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PillagerEntity.class)
public class PillagerMixin {
   @Inject(method = "<init>(Lnet/minecraft/entity/EntityType;Lnet/minecraft/world/World;)V", at = @At("TAIL"))
   private void addTitanTargetGoal(EntityType<? extends PillagerEntity> entityType, World level, CallbackInfo ci) {
      PillagerEntity pillager = (PillagerEntity)(Object)this;
      ((MobGoalSelectorAccessor)pillager).getTargetSelector().add(2, new ActiveTargetGoal<>(pillager, TitanEntity.class, true));
      ((MobGoalSelectorAccessor)pillager).getTargetSelector().add(2, new ActiveTargetGoal<>(pillager, SmallTitanEntity.class, true));
   }
}

