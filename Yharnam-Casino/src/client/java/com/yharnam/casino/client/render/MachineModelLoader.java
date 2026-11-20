package com.yharnam.casino.client.render;

import com.yharnam.casino.BrokenSlotMachineBlock;
import com.yharnam.casino.GamingFurnitureBlock;
import com.yharnam.casino.GachaponBlock;
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
        Map<String, MachineMesh> furniture = new java.util.HashMap<>();
        for (String id : List.of("poker_table", "black_jack_table", "fortune_wheel", "roulette_table", "rat_derby")) {
            furniture.put(id, MachineMesh.load(resources, meshId(id)));
        }
        furniture.put("gachapon", MachineMesh.load(resources, meshId("gachapon")));
        return new Models(Map.copyOf(staticMeshes), Map.copyOf(brokenMeshes), Map.copyOf(furniture));
    }

    private static Identifier meshId(String name) {
        return YharnamCasino.id("models/machine/" + name + ".mesh");
    }

    private static void registerModels(Models models, net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin.Context context) {
        models.furniture().forEach((id, mesh) -> {
            if (id.equals("gachapon") && Registries.BLOCK.get(YharnamCasino.id(id)) instanceof GachaponBlock dispenser) {
                var sprite = new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE,
                        YharnamCasino.id("machine/gachapon"));
                context.registerBlockStateResolver(dispenser, resolver -> {
                    var shared = new java.util.HashMap<String, MachineUnbakedModel>();
                    for (BlockState state : dispenser.getStateManager().getStates()) {
                        String half = state.get(GachaponBlock.HALF) == DoubleBlockHalf.UPPER ? "upper" : "lower";
                        Direction facing = state.get(GachaponBlock.FACING);
                        resolver.setModel(state, shared.computeIfAbsent(half + "_" + facing,
                                ignored -> new MachineUnbakedModel(mesh.part(half), sprite, facing, mesh.part(half+"_glass"), mesh.part(half+"_core"))));
                    }
                });
                return;
            }
            if (Registries.BLOCK.get(YharnamCasino.id(id)) instanceof GamingFurnitureBlock block) {
                var sprite = new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, YharnamCasino.id("machine/" + id));
                context.registerBlockStateResolver(block, resolver -> {
                    var shared = new java.util.HashMap<String, MachineUnbakedModel>();
                    for (BlockState state : block.getStateManager().getStates()) {
                        int part = state.get(GamingFurnitureBlock.PART);
                        var facing = state.get(GamingFurnitureBlock.FACING);
                        int index = part % block.parts();
                        resolver.setModel(state, shared.computeIfAbsent(index + "_" + facing, ignored ->
                            new MachineUnbakedModel(mesh.part(Integer.toString(index)), sprite, facing)));
                    }
                });
            }
        });
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
                          Map<SlotStyle, MachineMesh> brokenMeshes, Map<String, MachineMesh> furniture) {
    }

    private record MachineUnbakedModel(MachineMesh.Part part, SpriteIdentifier texture,
                                       Direction facing, @Nullable MachineMesh.Part glass, @Nullable MachineMesh.Part core) implements UnbakedModel {
        private MachineUnbakedModel(MachineMesh.Part part,SpriteIdentifier texture,Direction facing) {
            this(part,texture,facing,null,null);
        }
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
            return new MachineBakedModel(part, textureGetter.apply(texture), facing,glass,core);
        }
    }

    private static final class MachineBakedModel implements BakedModel, FabricBakedModel {
        private final Sprite particle;
        private volatile List<BakedQuad> vanillaQuads;
        private float[] sourceVertices, glassVertices, coreVertices;
        private final float rotationSin;
        private final float rotationCos;
        @Nullable
        private final Mesh mesh;

        private MachineBakedModel(MachineMesh.Part part, Sprite particle, Direction facing,MachineMesh.Part glass,MachineMesh.Part core) {
            glassVertices=glass==null?null:glass.vertices();
            coreVertices=core==null?null:core.vertices();
            this.particle = particle;
            float radians = (float)Math.toRadians(-facing.asRotation());
            rotationSin = (float)Math.sin(radians);
            rotationCos = (float)Math.cos(radians);
            float[] data = part.vertices();
            Renderer renderer = RendererAccess.INSTANCE.getRenderer();
            if (renderer == null) {
                mesh = null;
                vanillaQuads = fallback(data);
                return;
            }

            // All four facings share the same source array until a vanilla caller needs this copy.
            sourceVertices = data;
            MeshBuilder builder = renderer.meshBuilder();
            QuadEmitter emitter = builder.getEmitter();
            RenderMaterial material = renderer.materialFinder().blendMode(BlendMode.CUTOUT).find();
            emitPart(emitter,material,data);
            if (glassVertices!=null) emitPart(emitter,renderer.materialFinder().blendMode(BlendMode.TRANSLUCENT).find(),glassVertices);
            if (coreVertices!=null) emitPart(emitter,renderer.materialFinder().blendMode(BlendMode.SOLID).emissive(true)
                    .disableDiffuse(true).disableAo(0,true).find(),coreVertices);
            mesh=builder.build();
        }

        private void emitPart(QuadEmitter emitter, RenderMaterial material, float[] data) {
            for (int offset = 0; offset < data.length; offset += 24) {
                float nx = data[offset + 5] * rotationCos + data[offset + 7] * rotationSin;
                float nz = -data[offset + 5] * rotationSin + data[offset + 7] * rotationCos;
                emitVertex(emitter, 0, data, offset, rotationSin, rotationCos);
                emitVertex(emitter, 1, data, offset + 8, rotationSin, rotationCos);
                emitVertex(emitter, 2, data, offset + 16, rotationSin, rotationCos);
                emitVertex(emitter, 3, data, offset + 16, rotationSin, rotationCos);
                emitter.color(-1, -1, -1, -1)
                        .nominalFace(Direction.getFacing(nx, data[offset + 6], nz))
                        .cullFace(null)
                        .material(material)
                        .spriteBake(particle, MutableQuadView.BAKE_NORMALIZED)
                        .emit();
            }

        }

        private List<BakedQuad> fallback(float[] data) {
            if (glassVertices==null && coreVertices==null) return buildVanillaQuads(data,false);
            var all=new ArrayList<>(buildVanillaQuads(data,false));
            if (coreVertices!=null) all.addAll(buildVanillaQuads(coreVertices,true));
            if (glassVertices!=null) all.addAll(buildVanillaQuads(glassVertices,false));
            glassVertices=null;
            coreVertices=null;
            return List.copyOf(all);
        }

        private List<BakedQuad> buildVanillaQuads(float[] data,boolean emissive) {
            List<BakedQuad> quads = new ArrayList<>(data.length / 24);
            for (int offset = 0; offset < data.length; offset += 24) {
                float nx = data[offset + 5] * rotationCos + data[offset + 7] * rotationSin;
                float nz = -data[offset + 5] * rotationSin + data[offset + 7] * rotationCos;
                quads.add(vanillaQuad(data, offset, rotationSin, rotationCos, particle,
                        Direction.getFacing(nx, data[offset + 6], nz),emissive));
            }
            return List.copyOf(quads);
        }

        private static BakedQuad vanillaQuad(float[] data, int offset, float sin, float cos,
                                             Sprite sprite, Direction face,boolean emissive) {
            int[] packed = new int[32];
            packVanillaVertex(packed, 0, data, offset, sin, cos, sprite);
            packVanillaVertex(packed, 1, data, offset + 8, sin, cos, sprite);
            packVanillaVertex(packed, 2, data, offset + 16, sin, cos, sprite);
            packVanillaVertex(packed, 3, data, offset + 16, sin, cos, sprite);
            if (emissive) for (int v=0;v<4;v++) packed[v*8+6]=net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE;
            return new BakedQuad(packed, -1, face, sprite, !emissive);
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
            if (face != null) {
                return Collections.emptyList();
            }
            List<BakedQuad> cached = vanillaQuads;
            if (cached == null) {
                synchronized (this) {
                    cached = vanillaQuads;
                    if (cached == null) {
                        cached = fallback(sourceVertices);
                        sourceVertices = null;
                        vanillaQuads = cached;
                    }
                }
            }
            return cached;
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
