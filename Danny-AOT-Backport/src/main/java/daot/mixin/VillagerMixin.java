package daot.mixin;

import daot.ConnieFatherEntity;
import daot.CrawlerTitanEntity;
import daot.FritzTitanEntity;
import daot.OgreTitanEntity;
import daot.SadTitanEntity;
import daot.SmallTitan2Entity;
import daot.SmallTitanEntity;
import daot.TitanEntity;
import daot.YellowTitanEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.FleeEntityGoal;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.village.VillagerType;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerEntity.class)
public class VillagerMixin {
   @Inject(method = "<init>(Lnet/minecraft/entity/EntityType;Lnet/minecraft/world/World;Lnet/minecraft/village/VillagerType;)V", at = @At("TAIL"))
   private void addTitanAvoidGoal(EntityType<? extends VillagerEntity> entityType, World level, VillagerType villagerType, CallbackInfo ci) {
      VillagerEntity villager = (VillagerEntity)(Object)this;
      GoalSelector goals = ((MobGoalSelectorAccessor)villager).getGoalSelector();
      goals.add(1, new FleeEntityGoal<>(villager, TitanEntity.class, 24.0F, 0.8, 1.0));
      goals.add(1, new FleeEntityGoal<>(villager, SmallTitanEntity.class, 24.0F, 0.8, 1.0));
      goals.add(1, new FleeEntityGoal<>(villager, SmallTitan2Entity.class, 24.0F, 0.8, 1.0));
      goals.add(1, new FleeEntityGoal<>(villager, FritzTitanEntity.class, 24.0F, 0.8, 1.0));
      goals.add(1, new FleeEntityGoal<>(villager, YellowTitanEntity.class, 24.0F, 0.8, 1.0));
      goals.add(1, new FleeEntityGoal<>(villager, ConnieFatherEntity.class, 24.0F, 0.8, 1.0));
      goals.add(1, new FleeEntityGoal<>(villager, CrawlerTitanEntity.class, 24.0F, 0.8, 1.0));
      goals.add(1, new FleeEntityGoal<>(villager, OgreTitanEntity.class, 24.0F, 0.8, 1.0));
      goals.add(1, new FleeEntityGoal<>(villager, SadTitanEntity.class, 24.0F, 0.8, 1.0));
   }
}

