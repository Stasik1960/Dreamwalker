package com.yharnam.casino.client.render;

import com.yharnam.casino.BrokenSlotMachineBlock;
import com.yharnam.casino.SlotMachineBlock;
import com.yharnam.casino.SlotStyle;
import com.yharnam.casino.YharnamCasino;
import net.fabricmc.fabric.api.client.model.loading.v1.PreparableModelLoadingPlugin;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.MeshBuilder;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.Baker;
import net.minecraft.client.render.model.ModelBakeSettings;
import net.minecraft.client.render.model.UnbakedModel;
import net.minecraft.client.render.model.json.ModelOverrideList;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/** Loads the heavy cabinet geometry once and emits it into normal chunk meshes. */
public final class MachineModelLoader {
    private static boolean registered;

    private MachineModelLoader() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        PreparableModelLoadingPlugin.register(
                (resources, executor) -> CompletableFuture.supplyAsync(() -> load(resources), executor),
                MachineModelLoader::registerModels
        );
    }

    private static Models load(ResourceManager resources) {
        Map<SlotStyle, MachineMesh> staticMeshes = new EnumMap<>(SlotStyle.class);
        Map<SlotStyle, MachineMesh> brokenMeshes = new EnumMap<>(SlotStyle.class);
        for (SlotStyle style : SlotStyle.values()) {
            staticMeshes.put(style, MachineMesh.load(resources, meshId(style.id() + "_static")));
            brokenMeshes.put(style, MachineMesh.load(resources, meshId(style.id() + "_broken")));
        }
        return new Models(Map.copyOf(staticMeshes), Map.copyOf(brokenMeshes));
    }

    private static Identifier meshId(String name) {
        return YharnamCasino.id("models/machine/" + name + ".mesh");
    }

    private static void registerModels(Models models, net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin.Context context) {
        for (SlotStyle style : SlotStyle.values()) {
            Block working = Registries.BLOCK.get(YharnamCasino.id(style.id()));
            if (working != Blocks.AIR) {
                context.registerBlockStateResolver(working, resolver -> {
                    for (BlockState state : working.getStateManager().getStates()) {
                        String half = state.get(SlotMachineBlock.HALF) == DoubleBlockHalf.UPPER ? "upper" : "lower";
                        resolver.setModel(state, new MachineUnbakedModel(
                                models.staticMeshes().get(style).part(half),
                                new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE,
                                        YharnamCasino.id("machine/" + style.id())),
                                state.get(SlotMachineBlock.FACING)
                        ));
                    }
                });
            }

            Block broken = Registries.BLOCK.get(YharnamCasino.id(style.id() + "_broken"));
            if (broken != Blocks.AIR) {
                context.registerBlockStateResolver(broken, resolver -> {
                    for (BlockState state : broken.getStateManager().getStates()) {
                        String half = state.get(BrokenSlotMachineBlock.HALF) == DoubleBlockHalf.UPPER ? "upper" : "lower";
                        resolver.setModel(state, new MachineUnbakedModel(
                                models.brokenMeshes().get(style).part(half),
                                new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE,
                                        YharnamCasino.id("machine/" + style.id() + "_broken")),
                                state.get(BrokenSlotMachineBlock.FACING)
                        ));
                    }
                });
            }
        }
    }

    private record Models(Map<SlotStyle, MachineMesh> staticMeshes,
                          Map<SlotStyle, MachineMesh> brokenMeshes) {
    }

    private record MachineUnbakedModel(MachineMesh.Part part, SpriteIdentifier texture,
                                       Direction facing) implements UnbakedModel {
        @Override
        public Collection<Identifier> getModelDependencies() {
            return Collections.emptyList();
        }

        @Override
        public void setParents(Function<Identifier, UnbakedModel> modelLoader) {
        }

        @Override
        public BakedModel bake(Baker baker, Function<SpriteIdentifier, Sprite> textureGetter,
                               ModelBakeSettings rotationContainer, Identifier modelId) {
            return new MachineBakedModel(part, textureGetter.apply(texture), facing);
        }
    }

    private static final class MachineBakedModel implements BakedModel, FabricBakedModel {
        private final Sprite particle;
        private final List<BakedQuad> vanillaQuads;
        @Nullable
        private final Mesh mesh;

        private MachineBakedModel(MachineMesh.Part part, Sprite particle, Direction facing) {
            this.particle = particle;
            Renderer renderer = RendererAccess.INSTANCE.getRenderer();
            float radians = (float)Math.toRadians(-facing.asRotation());
            float sin = (float)Math.sin(radians);
            float cos = (float)Math.cos(radians);
            float[] data = part.vertices();
            List<BakedQuad> quads = new ArrayList<>(data.length / 24);
            MeshBuilder builder = renderer == null ? null : renderer.meshBuilder();
            QuadEmitter emitter = builder == null ? null : builder.getEmitter();
            RenderMaterial material = renderer == null ? null
                    : renderer.materialFinder().blendMode(BlendMode.CUTOUT).find();
            for (int offset = 0; offset < data.length; offset += 24) {
                float nx = data[offset + 5] * cos + data[offset + 7] * sin;
                float nz = -data[offset + 5] * sin + data[offset + 7] * cos;
                Direction nominalFace = Direction.getFacing(nx, data[offset + 6], nz);
                quads.add(vanillaQuad(data, offset, sin, cos, particle, nominalFace));
                if (emitter != null) {
                    emitVertex(emitter, 0, data, offset, sin, cos);
                    emitVertex(emitter, 1, data, offset + 8, sin, cos);
                    emitVertex(emitter, 2, data, offset + 16, sin, cos);
                    emitVertex(emitter, 3, data, offset + 16, sin, cos);
                    emitter.color(-1, -1, -1, -1)
                            .nominalFace(nominalFace)
                            .cullFace(null)
                            .material(material)
                            .spriteBake(particle, MutableQuadView.BAKE_NORMALIZED)
                            .emit();
                }
            }
            vanillaQuads = List.copyOf(quads);
            mesh = builder == null ? null : builder.build();
        }

        private static BakedQuad vanillaQuad(float[] data, int offset, float sin, float cos,
                                             Sprite sprite, Direction face) {
            int[] packed = new int[32];
            packVanillaVertex(packed, 0, data, offset, sin, cos, sprite);
            packVanillaVertex(packed, 1, data, offset + 8, sin, cos, sprite);
            packVanillaVertex(packed, 2, data, offset + 16, sin, cos, sprite);
            packVanillaVertex(packed, 3, data, offset + 16, sin, cos, sprite);
            return new BakedQuad(packed, -1, face, sprite, true);
        }

        private static void packVanillaVertex(int[] packed, int vertex, float[] data, int offset,
                                              float sin, float cos, Sprite sprite) {
            int base = vertex * 8;
            float x = data[offset];
            float z = data[offset + 2];
            float nx = data[offset + 5] * cos + data[offset + 7] * sin;
            float nz = -data[offset + 5] * sin + data[offset + 7] * cos;
            packed[base] = Float.floatToRawIntBits(0.5f + x * cos + z * sin);
            packed[base + 1] = Float.floatToRawIntBits(data[offset + 1]);
            packed[base + 2] = Float.floatToRawIntBits(0.5f - x * sin + z * cos);
            packed[base + 3] = -1;
            packed[base + 4] = Float.floatToRawIntBits(sprite.getFrameU(data[offset + 3] * 16.0));
            packed[base + 5] = Float.floatToRawIntBits(sprite.getFrameV(data[offset + 4] * 16.0));
            packed[base + 6] = 0;
            packed[base + 7] = packNormal(nx, data[offset + 6], nz);
        }

        private static int packNormal(float x, float y, float z) {
            int px = Math.round(x * 127.0f) & 0xFF;
            int py = Math.round(y * 127.0f) & 0xFF;
            int pz = Math.round(z * 127.0f) & 0xFF;
            return px | py << 8 | pz << 16;
        }

        private static void emitVertex(QuadEmitter emitter, int vertex, float[] data, int offset,
                                       float sin, float cos) {
            float x = data[offset];
            float z = data[offset + 2];
            float nx = data[offset + 5];
            float nz = data[offset + 7];
            emitter.pos(vertex, 0.5f + x * cos + z * sin, data[offset + 1],
                            0.5f - x * sin + z * cos)
                    .uv(vertex, data[offset + 3], data[offset + 4])
                    .normal(vertex, nx * cos + nz * sin, data[offset + 6], -nx * sin + nz * cos);
        }

        @Override
        public boolean isVanillaAdapter() {
            return mesh == null;
        }

        @Override
        public void emitBlockQuads(BlockRenderView blockView, BlockState state, BlockPos pos,
                                   Supplier<Random> randomSupplier, RenderContext context) {
            if (mesh != null) {
                mesh.outputTo(context.getEmitter());
            }
        }

        @Override
        public void emitItemQuads(ItemStack stack, Supplier<Random> randomSupplier, RenderContext context) {
            if (mesh != null) {
                mesh.outputTo(context.getEmitter());
            }
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction face, Random random) {
            return face == null ? vanillaQuads : Collections.emptyList();
        }

        @Override
        public boolean useAmbientOcclusion() {
            return true;
        }

        @Override
        public boolean hasDepth() {
            return true;
        }

        @Override
        public boolean isSideLit() {
            return true;
        }

        @Override
        public boolean isBuiltin() {
            return false;
        }

        @Override
        public Sprite getParticleSprite() {
            return particle;
        }

        @Override
        public ModelTransformation getTransformation() {
            return ModelTransformation.NONE;
        }

        @Override
        public ModelOverrideList getOverrides() {
            return ModelOverrideList.EMPTY;
        }
    }
}
