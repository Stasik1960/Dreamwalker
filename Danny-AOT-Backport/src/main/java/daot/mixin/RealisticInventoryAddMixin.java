package daot.mixin;

import daot.RealisticResourceCaps;
import daot.RealisticResourceUseTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerInventory.class)
public abstract class RealisticInventoryAddMixin {
   @Unique
   private static final ThreadLocal<Integer> dannysRealisticSurplus = ThreadLocal.withInitial(() -> 0);

   @Inject(method = "insertStack(ILnet/minecraft/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
   private void dannysRealisticAddHead(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
      if (slot == -1) {
         PlayerInventory inv = (PlayerInventory)(Object)this;
         PlayerEntity p = inv.player;
         if (p != null) {
            if (RealisticResourceUseTracker.isEnabled(p.getWorld())) {
               if (!p.isCreative() && !p.isSpectator()) {
                  if (RealisticResourceCaps.countsTowardBlades(stack) || RealisticResourceCaps.countsTowardCanisters(stack)) {
                     int allowed = RealisticResourceCaps.allowedToAccept(inv, stack);
                     if (allowed <= 0) {
                        cir.setReturnValue(false);
                     } else if (allowed < stack.getCount()) {
                        int surplus = stack.getCount() - allowed;
                        stack.setCount(allowed);
                        dannysRealisticSurplus.set(surplus);
                     }
                  }
               }
            }
         }
      }
   }

   @Inject(method = "insertStack(ILnet/minecraft/item/ItemStack;)Z", at = @At("RETURN"), cancellable = true)
   private void dannysRealisticAddReturn(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
      if (slot == -1) {
         int surplus = dannysRealisticSurplus.get();
         if (surplus > 0) {
            dannysRealisticSurplus.set(0);
            stack.setCount(stack.getCount() + surplus);
            cir.setReturnValue(false);
         }
      }
   }
}

