package dev.dreamwalker.bloodbornedw.composite;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.StringIdentifiable;

/** The same imported rung ladder offers its authored one/two-cell lengths as states. */
public final class ImportedLadderBlock extends CompositeRootBlock implements NewStateStorage {
    public static final EnumProperty<Height> HEIGHT = EnumProperty.of("height", Height.class);
    public enum Height implements StringIdentifiable {
        ONE("one"), TWO("two");
        private final String name;
        Height(String name) { this.name = name; }
        @Override public String asString() { return name; }
    }
    public ImportedLadderBlock(CompositeSpec spec) { super(spec); setDefaultState(getDefaultState().with(HEIGHT, Height.ONE).with(VARIANT, 0)); }
    public static boolean isLadderPath(String path) { return ImportedStairShapes.isLadderPath(path); }
    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { super.appendProperties(builder); builder.add(HEIGHT); }
    public static int variant(BlockState state) { return state.get(HEIGHT) == Height.TWO ? 1 : 0; }
    public static BlockState normalize(BlockState state) { return state.getBlock() instanceof ImportedLadderBlock ? state.with(VARIANT, variant(state)) : state; }
    public static BlockState placementState(BlockState state, ItemPlacementContext context) {
        if (!(state.getBlock() instanceof ImportedLadderBlock)) return state;
        NbtCompound tag = context.getStack().getSubNbt("BlockStateTag");
        boolean two = tag != null && ("two".equals(tag.getString("height")) || "1".equals(tag.getString("variant")));
        return normalize(state.with(HEIGHT, two ? Height.TWO : Height.ONE));
    }
    public static CompositeSpec.Bounds bounds(BlockState state) {
        if (!(state.getBlock() instanceof CompositeRootBlock block)) return null;
        CompositeSpec.Bounds old = block.spec.importedClimbingBounds;
        if (!(block instanceof ImportedLadderBlock) || old == null || state.get(HEIGHT) == Height.ONE) return old;
        return new CompositeSpec.Bounds(old.from(), new CompositeSpec.Vec(old.to().x(), old.from().y() + 32, old.to().z()));
    }
    @Override public ItemStack art(BlockState state, NbtCompound payload) {
        ItemStack stack = super.art(normalize(state), payload);
        stack.getOrCreateSubNbt("BlockStateTag").putString("height", state.get(HEIGHT).asString());
        stack.getOrCreateNbt().putInt("ImportedTypeVersion", 1);
        return stack;
    }
}
