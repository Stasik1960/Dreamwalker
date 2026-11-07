package dev.dreamwalker.bloodbornedw.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

/** A position-specific wrapper used only for edited roots. */
public final class DebugModels {
    private DebugModels() {}

    public static BakedModel apply(BakedModel model, BlockPos root, BlockState state) {
        var world = MinecraftClient.getInstance().world;
        if (world == null || model == null || model instanceof ShiftedModel) return model;
        int settings = DebugAdjustments.renderPose(world, root, state);
        return DebugAdjustments.hasPose(settings) ? new ShiftedModel(model, settings) : model;
    }

    private static final class ShiftedModel extends ForwardingBakedModel {
        private final float dx, dy, dz, cos, sin;

        ShiftedModel(BakedModel model, int settings) {
            wrapped = model;
            dx = (float)DebugAdjustments.x(settings);
            dy = (float)DebugAdjustments.y(settings);
            dz = (float)DebugAdjustments.z(settings);
            double radians = DebugAdjustments.turns(settings) * Math.PI / 4;
            cos = (float)Math.cos(radians);
            sin = (float)Math.sin(radians);
        }

        private float rotateX(float x, float z) { return cos * x - sin * z; }
        private float rotateZ(float x, float z) { return sin * x + cos * z; }
        private float poseX(float x, float z) { return rotateX(x - 0.5F, z - 0.5F) + 0.5F + dx; }
        private float poseZ(float x, float z) { return rotateZ(x - 0.5F, z - 0.5F) + 0.5F + dz; }

        private Direction face(Direction original) {
            if (original == null || original.getAxis() == Direction.Axis.Y) return original;
            return Direction.getFacing(rotateX(original.getOffsetX(), original.getOffsetZ()), 0,
                    rotateZ(original.getOffsetX(), original.getOffsetZ()));
        }

        @Override public List<BakedQuad> getQuads(BlockState state, Direction side, Random random) {
            if (side != null) return List.of();
            var result = new ArrayList<BakedQuad>();
            add(result, wrapped.getQuads(state, null, random));
            for (Direction face : Direction.values()) add(result, wrapped.getQuads(state, face, random));
            return result;
        }

        private void add(List<BakedQuad> result, List<BakedQuad> source) {
            for (BakedQuad quad : source) {
                int[] data = quad.getVertexData().clone();
                int stride = data.length / 4;
                for (int vertex = 0; vertex < 4; vertex++) {
                    int at = vertex * stride;
                    float x = Float.intBitsToFloat(data[at]);
                    float z = Float.intBitsToFloat(data[at + 2]);
                    data[at] = Float.floatToRawIntBits(poseX(x, z));
                    data[at + 1] = Float.floatToRawIntBits(Float.intBitsToFloat(data[at + 1]) + dy);
                    data[at + 2] = Float.floatToRawIntBits(poseZ(x, z));
                    if (stride == 8 && data[at + 7] != 0) {
                        int normal = data[at + 7];
                        int nx = (byte)normal;
                        int nz = (byte)(normal >>> 16);
                        data[at + 7] = (normal & 0xFF00FF00)
                                | (Math.round(rotateX(nx, nz)) & 0xFF)
                                | (Math.round(rotateZ(nx, nz)) & 0xFF) << 16;
                    }
                }
                result.add(new BakedQuad(data, quad.getColorIndex(), face(quad.getFace()), quad.getSprite(), quad.hasShade()));
            }
        }

        @Override public boolean isVanillaAdapter() { return false; }

        @Override public void emitBlockQuads(BlockRenderView world, BlockState state, BlockPos pos,
                Supplier<Random> random, RenderContext context) {
            context.pushTransform(this::transform);
            try {
                ((FabricBakedModel)wrapped).emitBlockQuads(world, state, pos, random, context);
            } finally {
                context.popTransform();
            }
        }

        private boolean transform(MutableQuadView quad) {
            quad.cullFace(null);
            quad.nominalFace(face(quad.nominalFace()));
            for (int i = 0; i < 4; i++) {
                float x = quad.x(i), z = quad.z(i);
                quad.pos(i, poseX(x, z), quad.y(i) + dy, poseZ(x, z));
                if (quad.hasNormal(i)) {
                    float nx = quad.normalX(i), nz = quad.normalZ(i);
                    quad.normal(i, rotateX(nx, nz), quad.normalY(i), rotateZ(nx, nz));
                }
            }
            return true;
        }
    }
}
