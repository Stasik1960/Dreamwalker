package daot.mixin;

import daot.RealisticResourceCaps;
import daot.RealisticResourceUseTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public abstract class RealisticSlotPlaceMixin {
   @Inject(method = "canInsert", at = @At("HEAD"), cancellable = true)
   private void dannysRealisticMayPlace(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
      Slot self = (Slot)(Object)this;
      if (self.inventory instanceof PlayerInventory inv) {
         PlayerEntity p = inv.player;
         if (p != null) {
            if (RealisticResourceUseTracker.isEnabled(p.getWorld())) {
               if (!p.isCreative() && !p.isSpectator()) {
                  boolean blade = RealisticResourceCaps.countsTowardBlades(stack);
                  boolean canister = RealisticResourceCaps.countsTowardCanisters(stack);
                  if (blade || canister) {
                     int cap = canister ? 2 : 4;
                     int current = canister ? RealisticResourceCaps.countCanisters(inv) : RealisticResourceCaps.countBlades(inv);
                     if (current >= cap) {
                        cir.setReturnValue(false);
                     }
                  }
               }
            }
         }
      }
   }
}

