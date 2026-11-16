package com.yharnam.casino;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import org.jetbrains.annotations.Nullable;

public final class DenominationMenu extends ScreenHandler {
    private final @Nullable SlotMachineEntity machine;
    private final PropertyDelegate settings;

    public DenominationMenu(int syncId, PlayerInventory inventory) {
        this(syncId,null,new ArrayPropertyDelegate(1));
    }

    DenominationMenu(int syncId, SlotMachineEntity machine) {
        this(syncId,machine,new PropertyDelegate() {
            @Override public int get(int index) { return machine.acceptedChipMask(); }
            @Override public void set(int index,int value) { }
            @Override public int size() { return 1; }
        });
    }

    private DenominationMenu(int syncId, @Nullable SlotMachineEntity machine, PropertyDelegate settings) {
        super(YharnamCasino.DENOMINATION_MENU,syncId);
        this.machine = machine;
        this.settings = settings;
        addProperties(settings);
    }

    public boolean accepts(int index) {
        return (settings.get(0) & (1 << index)) != 0;
    }

    public int denomination(int index) {
        return YharnamCasino.CHIP_VALUES[index];
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int index) {
        return machine != null && canUse(player) && machine.toggleDenomination(index,player);
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return machine == null || machine.canConfigure(player);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }
}
