package com.yharnam.casino;

import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;

final class ChipPool {
    private final SimpleInventory safe;
    private long change;

    ChipPool(SimpleInventory safe) {
        this.safe = safe;
    }

    long balance() {
        long value = change;
        for (int slot = 0; slot < safe.size(); slot++) {
            ItemStack stack = safe.getStack(slot);
            if (!stack.isEmpty() && stack.getItem() instanceof ChipItem chip) {
                value += (long)chip.value() * stack.getCount();
            }
        }
        return value;
    }

    boolean canStore(ItemStack chip) {
        for (int slot = 0; slot < safe.size(); slot++) {
            ItemStack stack = safe.getStack(slot);
            if (stack.isEmpty() || (ItemStack.canCombine(chip,stack) && stack.getCount() < stack.getMaxCount())) {
                return true;
            }
        }
        return false;
    }

    boolean storeStake(ItemStack chip) {
        return canStore(chip) && safe.addStack(chip).isEmpty();
    }

    long pay(long amount) {
        if (amount <= 0) {
            return 0;
        }
        long fromChange = Math.min(amount,change);
        long remaining = amount-fromChange;
        change -= fromChange;
        int[] values = YharnamCasino.CHIP_VALUES;
        for (int index = values.length-1; index >= 0 && remaining > 0; index--) {
            ChipItem chip = YharnamCasino.chip(values[index]);
            for (int slot = 0; slot < safe.size() && remaining >= chip.value(); slot++) {
                ItemStack stack = safe.getStack(slot);
                if (stack.isOf(chip)) {
                    int count = (int)Math.min(stack.getCount(),remaining/chip.value());
                    safe.removeStack(slot,count);
                    remaining -= (long)count*chip.value();
                }
            }
        }
        if (remaining > 0) {
            remaining = breakChipForChange(remaining);
        }
        return amount-remaining;
    }

    private long breakChipForChange(long remaining) {
        int bestSlot = -1;
        int bestValue = Integer.MAX_VALUE;
        for (int slot = 0; slot < safe.size(); slot++) {
            ItemStack stack = safe.getStack(slot);
            if (!stack.isEmpty() && stack.getItem() instanceof ChipItem chip &&
                    chip.value() > remaining && chip.value() < bestValue) {
                bestSlot = slot;
                bestValue = chip.value();
            }
        }
        if (bestSlot < 0) {
            return remaining;
        }
        safe.removeStack(bestSlot,1);
        change += bestValue-remaining;
        return 0;
    }

    // A full safe can temporarily lack room for change from a large chip.
    // Its exact remainder stays in this same pool and goes into slots as they free up.
    void fillSlotsWithChange() {
        int[] values = YharnamCasino.CHIP_VALUES;
        for (int index = values.length-1; index >= 0 && change > 0; index--) {
            ChipItem chip = YharnamCasino.chip(values[index]);
            while (change >= chip.value()) {
                int quantity = (int)Math.min(chip.getMaxCount(),change/chip.value());
                ItemStack left = safe.addStack(new ItemStack(chip,quantity));
                int accepted = quantity-left.getCount();
                if (accepted == 0) {
                    break;
                }
                change -= (long)accepted*chip.value();
            }
        }
    }

    void addChange(long amount) {
        change += amount;
    }

    void readChange(long amount) {
        change = Math.max(0,amount);
    }

    long savedChange() {
        return change;
    }

    long removeChange() {
        long amount = change;
        change = 0;
        return amount;
    }
}
