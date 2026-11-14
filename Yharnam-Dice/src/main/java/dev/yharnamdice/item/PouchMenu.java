package dev.yharnamdice.item;

import dev.yharnamdice.DiceMod;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

public final class PouchMenu extends ScreenHandler {
    public static final int WIDTH = 224, HEIGHT = 210;
    private final PlayerInventory player;
    private final int pouchIndex;
    private final ItemStack pouch;
    private final SimpleInventory bag = new SimpleInventory(DicePouchItem.SIZE) {
        @Override public int getMaxCountPerStack() { return 1; }
    };

    public PouchMenu(int sync, PlayerInventory inventory, PacketByteBuf buf) {
        this(sync, inventory, buf.readVarInt(), ItemStack.EMPTY);
    }

    public PouchMenu(int sync, PlayerInventory inventory, int pouchIndex, ItemStack pouch) {
        super(DiceMod.POUCH_MENU, sync);
        this.player = inventory;
        this.pouchIndex = pouchIndex;
        this.pouch = pouch;
        ItemStack[] initial = DicePouchItem.contents(pouch);
        for (int i = 0; i < initial.length; i++) {
            bag.setStack(i, initial[i]);
            addSlot(new Slot(bag, i, 68 + i % 5 * 18, 36 + i / 5 * 18) {
                @Override public boolean canInsert(ItemStack stack) { return DiceMod.skin(stack) >= 0; }
                @Override public int getMaxItemCount(ItemStack stack) { return 1; }
            });
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addPlayerSlot(col + row * 9 + 9, 32 + col * 18, 123 + row * 18);
        for (int i = 0; i < 9; i++) addPlayerSlot(i, 32 + i * 18, 181);
        bag.addListener(inventoryChanged -> persist());
    }

    private void addPlayerSlot(int index, int x, int y) {
        addSlot(new Slot(player, index, x, y) {
            @Override public boolean canInsert(ItemStack stack) { return index != pouchIndex; }
            @Override public boolean canTakeItems(PlayerEntity owner) { return index != pouchIndex; }
        });
    }

    private void persist() {
        if (!pouch.isEmpty() && player.getStack(pouchIndex) == pouch) {
            DicePouchItem.store(pouch, bag);
            player.markDirty();
        }
    }

    @Override public boolean canUse(PlayerEntity owner) {
        return owner.getWorld().isClient || player.getStack(pouchIndex) == pouch && !pouch.isEmpty();
    }

    @Override public void onSlotClick(int slot, int button, SlotActionType mode, PlayerEntity owner) {
        if (!canUse(owner)) return;
        if (mode == SlotActionType.SWAP && button == pouchIndex) return;
        super.onSlotClick(slot, button, mode, owner);
        persist();
    }

    @Override public ItemStack quickMove(PlayerEntity owner, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasStack() || !slot.canTakeItems(owner)) return ItemStack.EMPTY;
        ItemStack stack = slot.getStack();
        ItemStack old = stack.copy();
        if (index < DicePouchItem.SIZE) {
            if (!insertItem(stack, DicePouchItem.SIZE, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (DiceMod.skin(stack) < 0) return ItemStack.EMPTY;
            for (int i = 0; i < DicePouchItem.SIZE && !stack.isEmpty(); i++) {
                Slot target = slots.get(i);
                if (!target.hasStack()) target.setStack(stack.split(1));
            }
            if (stack.getCount() == old.getCount()) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setStack(ItemStack.EMPTY);
        else slot.markDirty();
        persist();
        return old;
    }
}
