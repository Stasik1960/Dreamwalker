package dev.dreamwalker.bloodborneblocks.mixin;

import dev.dreamwalker.bloodborneblocks.FunctionalFurniture;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(LivingEntity.class)
abstract class DecorativeClimbMixin {
 @Shadow private Optional<BlockPos> climbingPos;

 @Inject(method="isClimbing",at=@At("HEAD"),cancellable=true)
 private void bloodborneBlocks$decorativeLadder(CallbackInfoReturnable<Boolean> result){
  LivingEntity entity=(LivingEntity)(Object)this;
  // Decorative climbing is a player interaction. Avoid extra world queries for every mob.
  if(!(entity instanceof net.minecraft.entity.player.PlayerEntity)||entity.isSpectator())return;
  BlockPos pos=FunctionalFurniture.climbablePos(entity);
  if(pos!=null){climbingPos=Optional.of(pos);result.setReturnValue(true);}
 }
}
