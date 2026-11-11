package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance;
import dev.dreamwalker.bloodbornedw.tool.DebugAdjustments;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

/** A depth ordering carried alongside the vertices. Their positions stay exact. */
public final class SurfacePriority {
    private static final int SKY_LIGHT_HIGH_BYTE = 0xff000000;

    private SurfacePriority() {}

    public static int rank(BlockPos pos, BlockState state) {
        if (!Registries.BLOCK.getId(state.getBlock()).getNamespace().equals("bloodborne_dw")) return 0;
        var world = MinecraftClient.getInstance().world;
        boolean raised = world != null && DebugAdjustments.renderPriority(world, pos, state) != 0;
        return rank(pos, raised);
    }

    private static int rank(BlockPos pos, boolean raised) {
        // Distinct roots within a 4 by 4 by 4 neighborhood have distinct automatic ranks.
        int automatic = 1 + (pos.getX() & 3) + 4 * (pos.getY() & 3) + 16 * (pos.getZ() & 3);
        return raised ? Math.min(127, automatic + 64) : automatic;
    }

    public static void composite(int[] lights, CompositeRootBlock block, BlockPos pos, BlockState state,
                                 ObjectInstance.Owner owner) {
        if (block.spec.translucent || !SurfaceDepthShaders.compositeEnabled()) return;
        var world = MinecraftClient.getInstance().world;
        boolean raised = world != null && (owner == null
                ? DebugAdjustments.renderPriority(world, pos, state) != 0 : DebugAdjustments.renderPriority(world, owner));
        int rank = rank(pos, raised);
        // Sky light coordinates fit in one byte, even after smooth lighting.
        int metadata = rank << 24;
        for (int vertex = 0; vertex < lights.length; vertex++) {
            if ((lights[vertex] & SKY_LIGHT_HIGH_BYTE) == 0) lights[vertex] |= metadata;
        }
    }
}
