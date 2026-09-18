package daot;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

public final class RealisticResourceCaps {
   public static final int BLADE_CAP = 4;
   public static final int CANISTER_CAP = 2;

   private RealisticResourceCaps() {
   }

   public static boolean countsTowardBlades(ItemStack stack) {
      if (stack.isEmpty()) {
         return false;
      } else if (stack.getItem() instanceof BladeComponentItem) {
         return true;
      } else {
         return stack.getItem() instanceof BladeItem ? BladeItem.getBladeState(stack) != BladeItem.BladeState.EMPTY : false;
      }
   }

   public static boolean countsTowardCanisters(ItemStack stack) {
      return !stack.isEmpty() && stack.getItem() instanceof GasCanisterItem;
   }

   public static int countBlades(PlayerInventory inv) {
      int total = 0;

      for (int i = 0; i < inv.size(); i++) {
         ItemStack s = inv.getStack(i);
         if (!s.isEmpty()) {
            if (s.getItem() instanceof BladeComponentItem) {
               total += s.getCount();
            } else if (s.getItem() instanceof BladeItem && BladeItem.getBladeState(s) != BladeItem.BladeState.EMPTY) {
               total++;
            }
         }
      }

      return total;
   }

   public static int countCanisters(PlayerInventory inv) {
      int total = 0;

      for (int i = 0; i < inv.size(); i++) {
         ItemStack s = inv.getStack(i);
         if (!s.isEmpty() && s.getItem() instanceof GasCanisterItem) {
            total += s.getCount();
         }
      }

      return total;
   }

   public static int allowedToAccept(PlayerInventory inv, ItemStack incoming) {
      if (countsTowardBlades(incoming)) {
         int cur = countBlades(inv);
         int free = 4 - cur;
         if (free <= 0) {
            return 0;
         } else {
            int incomingCount = incoming.getItem() instanceof BladeComponentItem ? incoming.getCount() : 1;
            return Math.min(free, incomingCount);
         }
      } else if (countsTowardCanisters(incoming)) {
         int cur = countCanisters(inv);
         int free = 2 - cur;
         return free <= 0 ? 0 : Math.min(free, incoming.getCount());
      } else {
         return incoming.getCount();
      }
   }
}
