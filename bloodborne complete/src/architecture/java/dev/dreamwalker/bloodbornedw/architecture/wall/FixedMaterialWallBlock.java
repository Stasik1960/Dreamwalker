package dev.dreamwalker.bloodbornedw.architecture.wall;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.state.StateManager;

/** A separately offered artistic type without a redundant eight-material property product. */
public final class FixedMaterialWallBlock extends PrototypeWallBlock {
    private final int material;
    public FixedMaterialWallBlock(Settings settings,int material) { super(settings);this.material=material;setDefaultState(canonicalForm(getDefaultState())); }
    public int material() { return material; }
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder) { appendFixedProperties(builder); }
}
