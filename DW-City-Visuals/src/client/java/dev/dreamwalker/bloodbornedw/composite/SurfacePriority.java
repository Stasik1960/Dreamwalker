package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance;
import dev.dreamwalker.bloodbornedw.tool.DebugAdjustments;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

/** Separates close surfaces when their geometry is written to independent meshes. */
public final class SurfacePriority {
    // Sodium 0.5.13 encodes a terrain position in units of 1/32768 block.
    private static final float STEP = 3.0F / 32768.0F;
    // A composite and native terrain can both render from the same cell.
    private static final float COMPOSITE_OFFSET = 0.5F * STEP;
    private static final int RECORDED_VERTEX_STRIDE = 12;

    private SurfacePriority() {}

    public static float offset(BlockPos pos, BlockState state) {
        if (!Registries.BLOCK.getId(state.getBlock()).getNamespace().equals("bloodborne_dw")) return 0;
        var world = MinecraftClient.getInstance().world;
        boolean raised = world != null && DebugAdjustments.renderPriority(world, pos, state) != 0;
        return rankOffset(pos.getX(), pos.getY(), pos.getZ(), raised);
    }

    private static float rankOffset(int x, int y, int z, boolean raised) {
        // Distinct roots within a 4 by 4 by 4 neighborhood have distinct ranks.
        int rank = 1 + (x & 3) + 4 * (y & 3) + 16 * (z & 3) + (raised ? 96 : 0);
        return rank * STEP;
    }

    public static void composite(float[] vertices, CompositeRootBlock block, BlockPos pos, BlockState state,
                                 ObjectInstance.Owner owner) {
        if (block.spec.translucent) return;
        var world = MinecraftClient.getInstance().world;
        boolean raised = world != null && (owner == null
                ? DebugAdjustments.renderPriority(world, pos, state) != 0 : DebugAdjustments.renderPriority(world, owner));
        float offset = rankOffset(pos.getX(), pos.getY(), pos.getZ(), raised) + COMPOSITE_OFFSET;
        int stride = RECORDED_VERTEX_STRIDE;
        for (int a = 0; a + 4 * stride <= vertices.length; a += 4 * stride) {
            int b = a + stride;
            int c = b + stride;
            int d = c + stride;
            float ax = vertices[c] - vertices[a];
            float ay = vertices[c + 1] - vertices[a + 1];
            float az = vertices[c + 2] - vertices[a + 2];
            float bx = vertices[d] - vertices[b];
            float by = vertices[d + 1] - vertices[b + 1];
            float bz = vertices[d + 2] - vertices[b + 2];
            float nx = ay * bz - az * by;
            float ny = az * bx - ax * bz;
            float nz = ax * by - ay * bx;
            float length = nx * nx + ny * ny + nz * nz;
            if (!(length > 1.0e-12F) || !Float.isFinite(length)) continue;
            float scale = offset / (float) Math.sqrt(length);
            for (int vertex = a; vertex <= d; vertex += stride) {
                vertices[vertex] += nx * scale;
                vertices[vertex + 1] += ny * scale;
                vertices[vertex + 2] += nz * scale;
            }
        }
    }
}
