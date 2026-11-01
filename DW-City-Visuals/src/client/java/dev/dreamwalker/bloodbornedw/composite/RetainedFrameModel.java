package dev.dreamwalker.bloodbornedw.composite;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.ModelOverrideList;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

/** Render every face without neighbour culling, just in front of the host block face. */
final class RetainedFrameModel implements BakedModel {
    private static final float OFFSET = 1.0f / 1024.0f;
    private final BakedModel base;
    RetainedFrameModel(BakedModel base) { this.base = base; }
    @Override public List<BakedQuad> getQuads(BlockState state, Direction side, Random random) {
        if (side != null) return List.of();
        List<BakedQuad> result = new ArrayList<>();
        add(result, base.getQuads(state, null, random));
        for (Direction direction : Direction.values()) add(result, base.getQuads(state, direction, random));
        return result;
    }
    private static void add(List<BakedQuad> result, List<BakedQuad> source) {
        for (BakedQuad quad : source) {
            int[] data = quad.getVertexData().clone();
            int stride = data.length / 4;
            if (stride < 3 || data.length % 4 != 0) throw new IllegalStateException("Unexpected frame vertex layout");
            Direction face = quad.getFace();
            for (int vertex = 0; vertex < 4; vertex++) {
                int start = vertex * stride;
                data[start] = Float.floatToRawIntBits(Float.intBitsToFloat(data[start]) + face.getOffsetX() * OFFSET);
                data[start + 1] = Float.floatToRawIntBits(Float.intBitsToFloat(data[start + 1]) + face.getOffsetY() * OFFSET);
                data[start + 2] = Float.floatToRawIntBits(Float.intBitsToFloat(data[start + 2]) + face.getOffsetZ() * OFFSET);
            }
            result.add(new BakedQuad(data, quad.getColorIndex(), face, quad.getSprite(), quad.hasShade()));
        }
    }
    @Override public boolean useAmbientOcclusion() { return base.useAmbientOcclusion(); }
    @Override public boolean hasDepth() { return base.hasDepth(); }
    @Override public boolean isSideLit() { return base.isSideLit(); }
    @Override public boolean isBuiltin() { return base.isBuiltin(); }
    @Override public Sprite getParticleSprite() { return base.getParticleSprite(); }
    @Override public ModelTransformation getTransformation() { return base.getTransformation(); }
    @Override public ModelOverrideList getOverrides() { return base.getOverrides(); }
}
