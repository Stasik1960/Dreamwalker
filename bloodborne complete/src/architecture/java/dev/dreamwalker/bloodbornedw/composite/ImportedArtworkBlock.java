package dev.dreamwalker.bloodbornedw.composite;

import net.minecraft.block.*;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;

/** NEW assembly connection/art states beyond sixteen; legacy blocks keep their state space. */
public final class ImportedArtworkBlock extends CompositeRootBlock {
    public static final IntProperty ART_BANK=IntProperty.of("art_bank",0,7);
    public static boolean isBankedPath(String path){return java.util.Set.of("source_assembly_brick_wall","source_assembly_iron_bars").contains(path);}
    public ImportedArtworkBlock(CompositeSpec spec){super(spec);setDefaultState(getDefaultState().with(ART_BANK,0));}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder){super.appendProperties(builder);builder.add(ART_BANK);}
    public static int variant(BlockState state){return state.get(ART_BANK)*16+state.get(VARIANT);}
}
