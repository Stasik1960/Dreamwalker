package dev.dreamwalker.bloodbornedw.composite;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.StringIdentifiable;

/** Artistic variant and mounting plane are independent saved properties. */
public final class ThinWindowRootBlock extends CompositeRootBlock {
    public enum Mount implements StringIdentifiable {
        VERTICAL("vertical"), FLOOR("floor"), CEILING("ceiling");
        private final String value;
        Mount(String value) { this.value=value; }
        public String asString() { return value; }
    }
    public static final EnumProperty<Mount> MOUNT=EnumProperty.of("mount",Mount.class);
    public ThinWindowRootBlock(CompositeSpec spec) { super(spec); setDefaultState(getDefaultState().with(MOUNT,Mount.VERTICAL)); }
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder) { super.appendProperties(builder); builder.add(MOUNT); }
    public static Mount mount(BlockState state) { return state.contains(MOUNT)?state.get(MOUNT):Mount.VERTICAL; }
    @Override public net.minecraft.item.ItemStack art(BlockState state,net.minecraft.nbt.NbtCompound payload) { return GlazingTypes.picked(state,payload); }
    /** The clicked surface is an initial mount anchor, never a continuing support. */
    @Override public BlockState getStateForNeighborUpdate(BlockState state,net.minecraft.util.math.Direction side,BlockState neighbor,
            net.minecraft.world.WorldAccess world,net.minecraft.util.math.BlockPos pos,net.minecraft.util.math.BlockPos neighborPos) { return state; }
}
