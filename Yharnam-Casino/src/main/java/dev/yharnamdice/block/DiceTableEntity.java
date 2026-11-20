package dev.yharnamdice.block;

import dev.yharnamdice.DiceMod;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

public final class DiceTableEntity extends BlockEntity {
    private ItemStack disk = ItemStack.EMPTY;

    public DiceTableEntity(BlockPos pos, BlockState state) { super(DiceMod.TABLE_ENTITY, pos, state); }

    public ItemStack disk() { return disk; }

    public void disk(ItemStack item) {
        disk = item.copyWithCount(1);
        markDirty();
    }

    @Override protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        if (!disk.isEmpty()) nbt.put("RuleDisk", disk.writeNbt(new NbtCompound()));
    }

    @Override public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        disk = ItemStack.fromNbt(nbt.getCompound("RuleDisk"));
        if (!disk.isOf(DiceMod.DISK)) disk = ItemStack.EMPTY;
    }
}
