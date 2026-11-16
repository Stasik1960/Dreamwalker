package com.yharnam.casino;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;

final class ChipCabinetMenu extends GenericContainerScreenHandler {
    ChipCabinetMenu(int syncId, PlayerInventory inventory, ChipCabinetEntity cabinet) {
        super(ScreenHandlerType.GENERIC_9X6,syncId,inventory,cabinet,6);
    }

    @Override
    protected Slot addSlot(Slot slot) {
        if (slot.inventory instanceof ChipCabinetEntity cabinet) {
            return super.addSlot(new Slot(cabinet,slot.getIndex(),slot.x,slot.y) {
                @Override
                public boolean canInsert(ItemStack stack) {
                    return cabinet.isValid(getIndex(),stack);
                }

                @Override
                public boolean canTakeItems(PlayerEntity player) {
                    return getIndex()%9<ChipCabinetEntity.DENOMINATIONS;
                }
            });
        }
        return super.addSlot(slot);
    }
}
