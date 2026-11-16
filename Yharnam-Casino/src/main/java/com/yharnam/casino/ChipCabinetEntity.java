package com.yharnam.casino;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class ChipCabinetEntity extends BlockEntity implements Inventory {
    public static final int DENOMINATIONS = 7;
    public static final int LANES = 6;
    private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(54,ItemStack.EMPTY);
    private int[] visibleChips = new int[DENOMINATIONS];

    ChipCabinetEntity(BlockPos pos, BlockState state) {
        super(YharnamCasino.CHIP_CABINET_ENTITY,pos,state);
    }

    public int visibleCount(int denomination) {
        if (world != null && world.isClient) {
            return visibleChips[denomination];
        }
        int total = 0;
        for (int row=0; row<LANES; row++) {
            total += inventory.get(row*9+denomination).getCount();
        }
        return total;
    }

    @Override public int size() { return inventory.size(); }
    @Override public boolean isEmpty() { return inventory.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getStack(int slot) { return inventory.get(slot); }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        return slot >= 0 && slot < inventory.size() && slot%9 < DENOMINATIONS
                && stack.getItem() instanceof ChipItem chip && chip.value() == YharnamCasino.CHIP_VALUES[slot%9];
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack removed = Inventories.splitStack(inventory,slot,amount);
        if (!removed.isEmpty()) {
            markDirty();
        }
        return removed;
    }

    @Override
    public ItemStack removeStack(int slot) {
        ItemStack removed = Inventories.removeStack(inventory,slot);
        if (!removed.isEmpty()) {
            markDirty();
        }
        return removed;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        if (stack.isEmpty() || isValid(slot,stack)) {
            inventory.set(slot,stack);
            if (stack.getCount()>getMaxCountPerStack()) {
                stack.setCount(getMaxCountPerStack());
            }
            markDirty();
        }
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return world != null && player.getWorld() == world && world.getBlockEntity(pos) == this
                && player.squaredDistanceTo(Vec3d.ofCenter(pos)) <= 64;
    }

    @Override
    public void clear() {
        inventory.clear();
        markDirty();
    }

    @Override
    public void markDirty() {
        super.markDirty();
        if (world instanceof ServerWorld server && server.getBlockEntity(pos)==this) {
            BlockState state = getCachedState();
            server.updateListeners(pos,state,state,Block.NOTIFY_LISTENERS);
        }
    }

    @Override
    protected void writeNbt(NbtCompound tag) {
        super.writeNbt(tag);
        Inventories.writeNbt(tag,inventory);
    }

    @Override
    public void readNbt(NbtCompound tag) {
        super.readNbt(tag);
        if (tag.contains("VisibleChips",NbtElement.INT_ARRAY_TYPE)) {
            int[] counts = tag.getIntArray("VisibleChips");
            visibleChips = counts.length==DENOMINATIONS ? counts : new int[DENOMINATIONS];
        } else {
            inventory.clear();
            Inventories.readNbt(tag,inventory);
        }
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        NbtCompound tag = new NbtCompound();
        int[] counts = new int[DENOMINATIONS];
        for (int slot=0; slot<DENOMINATIONS; slot++) {
            counts[slot] = visibleCount(slot);
        }
        tag.putIntArray("VisibleChips",counts);
        return tag;
    }

    @Override
    public BlockEntityUpdateS2CPacket toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }
}
