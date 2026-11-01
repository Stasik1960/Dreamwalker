package dev.dreamwalker.bloodbornedw.composite;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.chunk.BlockBufferBuilderStorage;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

public final class RetainedFrameVanillaEmitter {
    private RetainedFrameVanillaEmitter() {}
    public static void render(BlockPos origin, ChunkRendererRegion region, MatrixStack matrices, Set<RenderLayer> layers,
                       Random random, BlockRenderManager manager, BlockBufferBuilderStorage storage) {
        Map<Integer, Integer> frames = ((RetainedFrameVanillaSnapshot)(Object)region).dw$frameSection(origin);
        if (frames.isEmpty()) return;
        BufferBuilder buffer = storage.get(RenderLayer.getCutout());
        if (layers.add(RenderLayer.getCutout())) buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL);
        Map<Integer, BakedModel> models = new HashMap<>();
        for (var entry : frames.entrySet()) {
            int packed = entry.getKey();
            BlockPos pos = origin.add(packed & 15, (packed >> 8) & 15, (packed >> 4) & 15);
            if (RetainedFrameState.matches(region.getBlockState(pos))) continue;
            BlockState state = RetainedFrames.state(entry.getValue());
            BakedModel model = models.computeIfAbsent(entry.getValue(), ignored -> new RetainedFrameModel(manager.getModel(state)));
            matrices.push();
            matrices.translate(packed & 15, (packed >> 8) & 15, (packed >> 4) & 15);
            manager.getModelRenderer().render(region, model, state, pos, matrices, buffer, false, random,
                state.getRenderingSeed(pos), OverlayTexture.DEFAULT_UV);
            matrices.pop();
        }
    }
}

