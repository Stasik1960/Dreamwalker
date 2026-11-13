package dev.dreamwalker.bloodbornedw.building;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;

/** Utilities whose existing placement survives ordinary gameplay. */
public final class CreativeOnlyUtilities {
    private CreativeOnlyUtilities() {}

    public static boolean isWhiteLight(BlockState state) {
        var id = Registries.BLOCK.getId(state.getBlock());
        return "bloodborne_dw".equals(id.getNamespace()) && "dw_whiteness".equals(id.getPath());
    }

    public static boolean includes(BlockState state) {
        var id = Registries.BLOCK.getId(state.getBlock());
        if (!"bloodborne_dw".equals(id.getNamespace())) return false;
        return switch (id.getPath()) {
            case "dw_darkness", "dw_whiteness", "dw_faerie_veil", "dw_light", "dw_barrier" -> true;
            default -> false;
        };
    }

    public static boolean blocksReplacement(BlockState state, PlayerEntity player) {
        return (player == null || !player.isCreative()) && includes(state);
    }
}
