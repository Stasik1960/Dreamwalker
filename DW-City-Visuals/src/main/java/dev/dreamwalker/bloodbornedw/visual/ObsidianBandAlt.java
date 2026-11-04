package dev.dreamwalker.bloodbornedw.visual;

import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import dev.dreamwalker.bloodbornedw.composite.CompositeSpec;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/** Authored ALT tone of the source_obsidian ornamental band. */
public final class ObsidianBandAlt {
    private static final Identifier BLOCK = new Identifier("bloodborne_dw","source_obsidian");
    public static final Identifier SOURCE = new Identifier("bloodborne_dw","block/remaining/source/minecraft/block/obsidian");
    public static final Identifier TARGET = new Identifier("bloodborne_dw","block/city_altfix/obsidian_band");
    public static final int REFERENCE_RGB = 0x716a5e;

    private ObsidianBandAlt() {}

    public static boolean matches(BlockState state) {
        return state.getBlock() instanceof CompositeRootBlock
                && BLOCK.equals(Registries.BLOCK.getId(state.getBlock()))
                && CompositeSpec.poseVariant(state) == 0;
    }
}
