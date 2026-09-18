package daot.mixin;

import daot.GeassManager;
import daot.HandcuffsTracker;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerInteractionManager.class)
public class HandcuffedBlockInteractionMixin {
   @Shadow
   @Final
   protected ServerPlayerEntity player;

   @Inject(method = "tryBreakBlock", at = @At("HEAD"), cancellable = true)
   private void blockBreakIfCuffed(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
      if (HandcuffsTracker.isCuffed(this.player.getUuid()) || GeassManager.isFrozen(this.player.getUuid())) {
         cir.setReturnValue(false);
      }
   }

   @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
   private void blockPlaceIfCuffed(
      ServerPlayerEntity player, World level, ItemStack stack, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir
   ) {
      if (HandcuffsTracker.isCuffed(player.getUuid()) || GeassManager.isFrozen(player.getUuid())) {
         cir.setReturnValue(ActionResult.FAIL);
      }
   }

   @Inject(method = "interactItem", at = @At("HEAD"), cancellable = true)
   private void blockUseItemIfCuffed(ServerPlayerEntity player, World level, ItemStack stack, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
      if (HandcuffsTracker.isCuffed(player.getUuid()) || GeassManager.isFrozen(player.getUuid())) {
         cir.setReturnValue(ActionResult.FAIL);
      }
   }
}
