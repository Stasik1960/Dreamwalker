package daot.mixin;

import daot.BladeComponentItem;
import daot.BladeItem;
import daot.GasCanisterItem;
import daot.RealisticResourceCaps;
import daot.RealisticResourceUseTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenHandler.class)
public abstract class RealisticMenuSwapMixin {
   @Inject(method = "internalOnSlotClick", at = @At("HEAD"), cancellable = true)
   private void dannysRealisticSwap(int slotId, int button, SlotActionType clickType, PlayerEntity player, CallbackInfo ci) {
      if (clickType == SlotActionType.SWAP) {
         if (RealisticResourceUseTracker.isEnabled(player.getWorld())) {
            if (!player.isCreative() && !player.isSpectator()) {
               if (button >= 0 && (button < 9 || button == 40)) {
                  ScreenHandler self = (ScreenHandler)(Object)this;
                  if (slotId >= 0 && slotId < self.slots.size()) {
                     Slot hoveredSlot = self.slots.get(slotId);
                     ItemStack hovered = hoveredSlot.getStack();
                     if (!hovered.isEmpty()) {
                        if (!(hoveredSlot.inventory instanceof PlayerInventory)) {
                           boolean blade = RealisticResourceCaps.countsTowardBlades(hovered);
                           boolean canister = RealisticResourceCaps.countsTowardCanisters(hovered);
                           if (blade || canister) {
                              PlayerInventory inv = player.getInventory();
                              ItemStack outgoing = button == 40 ? player.getOffHandStack() : inv.getStack(button);
                              int cap = canister ? 2 : 4;
                              int current = canister ? RealisticResourceCaps.countCanisters(inv) : RealisticResourceCaps.countBlades(inv);
                              int incoming = capWeight(hovered, blade, canister);
                              int leaving = capWeight(outgoing, blade, canister);
                              if (current - leaving + incoming > cap) {
                                 ci.cancel();
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @Unique
   private static int capWeight(ItemStack stack, boolean wantBlade, boolean wantCanister) {
      if (stack.isEmpty()) {
         return 0;
      } else if (wantCanister) {
         return stack.getItem() instanceof GasCanisterItem ? stack.getCount() : 0;
      } else if (stack.getItem() instanceof BladeComponentItem) {
         return stack.getCount();
      } else {
         return stack.getItem() instanceof BladeItem && BladeItem.getBladeState(stack) != BladeItem.BladeState.EMPTY ? 1 : 0;
      }
   }
}

