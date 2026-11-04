package dev.dreamwalker.bloodbornedw.visual;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.render.model.json.ModelOverrideList;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

/** Client-only emissive material wrapper; it does not add light to the world. */
public final class WhitenessClient implements ClientModInitializer {
    private static final String NAMESPACE = "bloodborne_dw";
    private static volatile RenderMaterial emissiveMaterial;

    @Override
    public void onInitializeClient() {
        ModelLoadingPlugin.register(context -> context.modifyModelAfterBake().register(
                ModelModifier.WRAP_PHASE,
                (model, bakeContext) -> model != null && isWhitenessModel(bakeContext.id())
                        ? new EmissiveModel(model)
                        : model));
    }

    private static boolean isWhitenessModel(Identifier id) {
        return id != null && NAMESPACE.equals(id.getNamespace())
                && ("dw_whiteness".equals(id.getPath()) || "block/dw_whiteness".equals(id.getPath()));
    }

    /** Preserves the base model's cube geometry while making both FRAPI and vanilla paths emissive. */
    private static final class EmissiveModel implements BakedModel, FabricBakedModel {
        private final BakedModel base;
        private final java.util.Map<Direction, List<BakedQuad>> quadsByFace;
        private final List<BakedQuad> unculledQuads;

        private EmissiveModel(BakedModel base) {
            this.base = base;
            EnumMap<Direction, List<BakedQuad>> quads = new EnumMap<>(Direction.class);
            for (Direction face : Direction.values()) {
                quads.put(face, fullBright(base.getQuads(null, face, Random.create(0L))));
            }
            quadsByFace = java.util.Map.copyOf(quads);
            unculledQuads = fullBright(base.getQuads(null, null, Random.create(0L)));
        }

        @Override
        public boolean isVanillaAdapter() {
            return RendererAccess.INSTANCE.getRenderer() == null;
        }

        @Override
        public void emitBlockQuads(BlockRenderView view, BlockState state, BlockPos pos,
                                   Supplier<Random> randomSupplier, RenderContext context) {
            emitQuads(context.getEmitter());
        }

        @Override
        public void emitItemQuads(ItemStack stack, Supplier<Random> randomSupplier, RenderContext context) {
            emitQuads(context.getEmitter());
        }

        private void emitQuads(QuadEmitter emitter) {
            RenderMaterial material = emissiveMaterial();
            if (material == null) return;
            for (Direction face : Direction.values()) {
                emitFace(face, material, emitter);
            }
            emitFace(null, material, emitter);
        }

        private void emitFace(Direction face, RenderMaterial material, QuadEmitter emitter) {
            List<BakedQuad> quads = face == null ? unculledQuads : quadsByFace.get(face);
            for (BakedQuad quad : quads) {
                emitter.fromVanilla(quad, material, face).emit();
            }
        }

        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction face, Random random) {
            return face == null ? unculledQuads : quadsByFace.get(face);
        }

        private static List<BakedQuad> fullBright(List<BakedQuad> quads) {
            if (quads.isEmpty()) return List.of();
            List<BakedQuad> result = new ArrayList<>(quads.size());
            for (BakedQuad quad : quads) {
                int[] vertexData = quad.getVertexData().clone();
                for (int vertex = 0; vertex < 4; vertex++) {
                    vertexData[vertex * 8 + 6] = 0x00F000F0;
                }
                result.add(new BakedQuad(vertexData, quad.getColorIndex(), quad.getFace(), quad.getSprite(), false));
            }
            return List.copyOf(result);
        }

        private static RenderMaterial emissiveMaterial() {
            RenderMaterial material = emissiveMaterial;
            if (material != null) return material;
            Renderer renderer = RendererAccess.INSTANCE.getRenderer();
            if (renderer == null) return null;
            synchronized (EmissiveModel.class) {
                material = emissiveMaterial;
                if (material == null) {
                    material = renderer.materialFinder()
                            .emissive(true)
                            .disableDiffuse(true)
                            .ambientOcclusion(TriState.FALSE)
                            .find();
                    emissiveMaterial = material;
                }
            }
            return material;
        }

        @Override public boolean useAmbientOcclusion() { return false; }
        @Override public boolean hasDepth() { return base.hasDepth(); }
        @Override public boolean isSideLit() { return false; }
        @Override public boolean isBuiltin() { return base.isBuiltin(); }
        @Override public Sprite getParticleSprite() { return base.getParticleSprite(); }
        @Override public ModelTransformation getTransformation() { return base.getTransformation(); }
        @Override public ModelOverrideList getOverrides() { return base.getOverrides(); }
    }
}
