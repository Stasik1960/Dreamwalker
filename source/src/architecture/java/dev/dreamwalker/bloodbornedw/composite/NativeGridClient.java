package dev.dreamwalker.bloodbornedw.composite;

import java.io.*;
import java.util.*;
import java.util.function.*;
import net.fabricmc.api.*;
import net.fabricmc.fabric.api.client.model.loading.v1.*;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.*;
import net.fabricmc.fabric.api.renderer.v1.mesh.*;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.model.*;
import net.minecraft.client.render.model.json.*;
import net.minecraft.client.texture.*;
import net.minecraft.client.util.*;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

/** Shared, already clipped quads baked during resource loading. The normal
 * vanilla/Indigo/Sodium+Indium section renderer owns all terrain buffers. */
@Environment(EnvType.CLIENT)
public final class NativeGridClient implements ClientModInitializer {
    private static volatile Session session;
    private record RawQuad(int material, int cull, int face, int tint, int flags, int[] vertices) {}
    private record Quad(BakedQuad baked, RenderMaterial material, Direction cull, BlockState tintSource,
                        boolean remainingGrass, BlockPos tintOffset) {}
    private static final class SharedMesh {
        final List<BakedQuad>[] faces;
        final List<Quad> quads;
        final Mesh fixedMesh;
        final boolean vanilla, ambient;
        @SuppressWarnings("unchecked")
        SharedMesh(List<Quad> quads, boolean vanilla, boolean ambient) {
            this.quads = List.copyOf(quads);
            this.vanilla = vanilla;
            this.ambient = ambient;
            faces = (List<BakedQuad>[]) new List<?>[7];
            List<List<BakedQuad>> groups = new ArrayList<>();
            for (int i = 0; i < 7; i++) groups.add(new ArrayList<>());
            for (Quad quad : quads) groups.get(quad.cull() == null ? 6 : quad.cull().ordinal()).add(quad.baked());
            for (int i = 0; i < 7; i++) faces[i] = List.copyOf(groups.get(i));
            // Immutable FRAPI mesh can be submitted directly when no world tint is needed.
            if (!vanilla && quads.stream().noneMatch(quad -> quad.tintSource() != null)) {
                var builder = requireRenderer().meshBuilder();
                var emitter = builder.getEmitter();
                for (Quad quad : quads) { emitter.fromVanilla(quad.baked(), quad.material(), quad.cull()); emitter.emit(); }
                fixedMesh = builder.build();
            } else fixedMesh = null;
        }
    }
    private static net.fabricmc.fabric.api.renderer.v1.Renderer requireRenderer() {
        var renderer = RendererAccess.INSTANCE.getRenderer();
        if (renderer == null) throw new IllegalStateException("Native grid materials need Fabric Rendering API (Indium when Sodium is installed)");
        return renderer;
    }
    private static final class Session {
        final RawQuad[][] raw;
        final SharedMesh[] baked;
        final RenderLayer[] layers;
        final Sprite[] sprites;
        final Map<Integer, RenderMaterial> materials = new HashMap<>();
        final Map<Integer, SharedMesh> wholeItems = new HashMap<>();
        Session() throws IOException {
            int count = NativeGridCatalog.quadCounts().size();
            raw = new RawQuad[count][];
            baked = new SharedMesh[count];
            layers = new RenderLayer[count];
            sprites = new Sprite[NativeGridCatalog.materials().size()];
            try (InputStream stream = NativeGridClient.class.getResourceAsStream(NativeGridCatalog.DIRECTORY + "meshes.bin")) {
                if (stream == null) throw new FileNotFoundException("Missing compiled native grid meshes");
                try (DataInputStream input = new DataInputStream(new BufferedInputStream(stream, 1 << 20))) {
                    if (input.readInt() != 0x4e475131 || input.readInt() != count)
                        throw new IOException("Native grid mesh header/catalog mismatch");
                    for (int mesh = 0; mesh < count; mesh++) {
                        int quads = input.readInt();
                        if (quads != NativeGridCatalog.quadCounts().get(mesh)) throw new IOException("Native grid quad count mismatch");
                        raw[mesh] = new RawQuad[quads];
                        String layer = "solid";
                        for (int q = 0; q < quads; q++) {
                            int material = input.readInt(), cull = input.readByte(), face = input.readByte();
                            int tint = input.readInt(), flags = input.readUnsignedByte();
                            if (material < 0 || material >= sprites.length || cull < -1 || cull > 5 || face < 0 || face > 5 || (flags & ~7) != 0)
                                throw new IOException("Invalid compiled native grid quad metadata");
                            int[] data = new int[32];
                            for (int vertex = 0; vertex < 4; vertex++) {
                                int offset = vertex * 8;
                                for (int field = 0; field < 5; field++) {
                                    int bits = input.readInt();
                                    float value = Float.intBitsToFloat(bits);
                                    if (!Float.isFinite(value) || field < 3 && (value < -1e-6F || value > 1.000001F))
                                        throw new IOException("Native grid vertex is nonfinite or outside cell");
                                    data[offset + (field < 3 ? field : field + 1)] = bits;
                                }
                                data[offset + 3] = input.readInt();
                                data[offset + 6] = input.readInt();
                                data[offset + 7] = input.readInt();
                            }
                            raw[mesh][q] = new RawQuad(material, cull, face, tint, flags, data);
                            layer = mergeLayer(layer, NativeGridCatalog.materials().get(material).layer());
                        }
                        layers[mesh] = renderLayer(layer);
                    }
                    if (input.read() != -1) throw new IOException("Trailing compiled native grid mesh data");
                }
            }
        }
        synchronized SharedMesh bake(int mesh, Function<SpriteIdentifier, Sprite> textureGetter) {
            if (baked[mesh] != null) return baked[mesh];
            RawQuad[] source = Objects.requireNonNull(raw[mesh]);
            List<Quad> output = new ArrayList<>(source.length);
            boolean vanilla = true;
            boolean ambient = source.length == 0 || (source[0].flags() & 2) != 0;
            String firstLayer = source.length == 0 ? "solid" : NativeGridCatalog.materials().get(source[0].material()).layer();
            for (RawQuad quad : source) {
                var material = NativeGridCatalog.materials().get(quad.material());
                if (!firstLayer.equals(material.layer()) || (quad.flags() & 4) != 0
                        || ((quad.flags() & 2) != 0) != ambient || quad.tint() >= 0)
                    vanilla = false;
            }
            for (RawQuad quad : source) {
                var material = NativeGridCatalog.materials().get(quad.material());
                Sprite sprite = sprites[quad.material()];
                if (sprite == null) {
                    sprite = textureGetter.apply(new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, new Identifier(material.sprite())));
                    if (sprite.getContents().getId().getPath().equals("missingno"))
                        throw new IllegalStateException("Missing native grid sprite: " + material.sprite());
                    sprites[quad.material()] = sprite;
                }
                int[] data = quad.vertices();
                for (int vertex = 0; vertex < 4; vertex++) {
                    int offset = vertex * 8;
                    data[offset + 4] = Float.floatToRawIntBits(sprite.getFrameU(Float.intBitsToFloat(data[offset + 4])));
                    data[offset + 5] = Float.floatToRawIntBits(sprite.getFrameV(Float.intBitsToFloat(data[offset + 5])));
                }
                BakedQuad bakedQuad = new BakedQuad(data, quad.tint(), Direction.values()[quad.face()], sprite, (quad.flags() & 1) != 0);
                int key = quad.material() * 8 + quad.flags();
                RenderMaterial renderMaterial = materials.computeIfAbsent(key, ignored -> requireRenderer().materialFinder()
                        .blendMode(blend(material.layer())).emissive((quad.flags() & 4) != 0)
                        .disableColorIndex(true).disableDiffuse((quad.flags() & 4) != 0 || (quad.flags() & 1) == 0)
                        .ambientOcclusion((quad.flags() & 4) != 0 || (quad.flags() & 2) == 0 ? TriState.FALSE : TriState.DEFAULT).find());
                BlockState tintSource = null;
                if (quad.tint() >= 0 && !material.tintSource().isEmpty()) {
                    Identifier id = new Identifier(material.tintSource());
                    if (!Registries.BLOCK.containsId(id)) throw new IllegalStateException("Unknown native grid tint source: " + id);
                    tintSource = Registries.BLOCK.get(id).getDefaultState();
                    for (var property : material.tintProperties().entrySet()) {
                        Property<?> target = tintSource.getBlock().getStateManager().getProperty(property.getKey());
                        if (target == null) throw new IllegalStateException("Unknown native grid tint property: " + property.getKey());
                        tintSource = setProperty(tintSource, target, property.getValue());
                    }
                }
                if (quad.tint() >= 0 && tintSource == null)
                    throw new IllegalStateException("Unresolved native grid tint index: " + quad.tint());
                if (material.tintMode().equals("remaining_grass") && tintSource != null && !(tintSource.getBlock() instanceof CompositeRootBlock))
                    throw new IllegalStateException("Imported grass tint must identify its source artwork");
                output.add(new Quad(bakedQuad, renderMaterial, quad.cull() < 0 ? null : Direction.values()[quad.cull()], tintSource,
                        material.tintMode().equals("remaining_grass"), material.tintOffset()));
            }
            SharedMesh result = new SharedMesh(output, vanilla, ambient);
            baked[mesh] = result;
            raw[mesh] = null; // Vertex arrays now belong to BakedQuad; no raw duplicate survives the bake.
            return result;
        }
        synchronized void bakeWholeItem(NativeGridCatalog.WholeObject object, Function<SpriteIdentifier, Sprite> textureGetter) {
            if (wholeItems.containsKey(object.baseCell())) return;
            List<Quad> complete = new ArrayList<>();
            float[] minimum = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY};
            float[] maximum = {Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
            for (var member : object.cells()) {
                SharedMesh source = bake(NativeGridCatalog.cell(member.cell()).mesh(), textureGetter);
                BlockPos offset = member.offset();
                for (Quad quad : source.quads) {
                    int[] vertices = quad.baked().getVertexData().clone();
                    for (int vertex = 0; vertex < 4; vertex++) {
                        int at = vertex * 8;
                        vertices[at] = Float.floatToRawIntBits(Float.intBitsToFloat(vertices[at]) + offset.getX());
                        vertices[at + 1] = Float.floatToRawIntBits(Float.intBitsToFloat(vertices[at + 1]) + offset.getY());
                        vertices[at + 2] = Float.floatToRawIntBits(Float.intBitsToFloat(vertices[at + 2]) + offset.getZ());
                        for (int axis = 0; axis < 3; axis++) {
                            float coordinate = Float.intBitsToFloat(vertices[at + axis]);
                            minimum[axis] = Math.min(minimum[axis], coordinate);
                            maximum[axis] = Math.max(maximum[axis], coordinate);
                        }
                    }
                    BakedQuad baked = new BakedQuad(vertices, quad.baked().getColorIndex(), quad.baked().getFace(), quad.baked().getSprite(), quad.baked().hasShade());
                    complete.add(new Quad(baked, quad.material(), null, quad.tintSource(), quad.remainingGrass(), quad.tintOffset()));
                }
            }
            float extent = Math.max(maximum[0] - minimum[0], Math.max(maximum[1] - minimum[1], maximum[2] - minimum[2]));
            if (!(extent > 0) || !Float.isFinite(extent)) throw new IllegalStateException("Empty or invalid whole-object item mesh: " + object.key());
            // Fit the entire authored silhouette symmetrically into one item
            // cube. Uniform positive scale preserves aspect, normals and UVs.
            // These are dedicated item vertex copies; world meshes stay exact.
            for (Quad quad : complete) {
                int[] vertices = quad.baked().getVertexData();
                for (int vertex = 0; vertex < 4; vertex++) for (int axis = 0; axis < 3; axis++) {
                    int at = vertex * 8 + axis;
                    float coordinate = Float.intBitsToFloat(vertices[at]);
                    float center = (minimum[axis] + maximum[axis]) * 0.5F;
                    vertices[at] = Float.floatToRawIntBits(0.5F + (coordinate - center) / extent);
                }
            }
            // One complete normalized item mesh per template; never per stack/frame.
            wholeItems.put(object.baseCell(), new SharedMesh(complete, false, true));
        }
    }

    @Override public void onInitializeClient() {
        if (NativeGridCatalog.cells().isEmpty()) return;
        NativeGridBridgeClient.initialize();
        ModelLoadingPlugin.register(plugin -> {
            final Session loading;
            try { loading = new Session(); } catch (IOException failure) { throw new IllegalStateException("Cannot read native city geometry", failure); }
            session = loading;
            plugin.modifyModelAfterBake().register(ModelModifier.WRAP_PHASE, (marker, context) -> {
                if (!(context.id() instanceof ModelIdentifier id) || !id.getNamespace().equals("bloodborne_dw")
                        || !id.getPath().startsWith("native_grid_")) return marker;
                int bank = Integer.parseInt(id.getPath().substring("native_grid_".length()));
                int part = 0;
                boolean inventory = id.getVariant().equals("inventory");
                if (!inventory) for (String property : id.getVariant().split(",")) if (property.startsWith("part=")) part = Integer.parseInt(property.substring(5));
                int index = Math.min(bank * NativeGridCatalog.BANK_SIZE + part, NativeGridCatalog.cells().size() - 1);
                SharedMesh mesh = loading.bake(NativeGridCatalog.cell(index).mesh(), context.textureGetter());
                var membership = NativeGridCatalog.membership(index);
                if (membership != null && index == membership.object().baseCell()) loading.bakeWholeItem(membership.object(), context.textureGetter());
                return new Model(marker, loading, index, mesh, inventory);
            });
        });
    }
    public static RenderLayer layer(BlockState state) {
        Session current = session;
        return current == null ? RenderLayer.getSolid() : current.layers[NativeGridCatalog.cell(state).mesh()];
    }
    static void emitBridgeBase(int index,BlockRenderView world,BlockPos pos,RenderContext context){
        Session current=session;
        SharedMesh mesh=current==null?null:current.baked[NativeGridCatalog.cell(index).mesh()];
        if(mesh==null)throw new IllegalStateException("Native bridge shared mesh was not prepared during baking");
        if(mesh.fixedMesh!=null)context.meshConsumer().accept(mesh.fixedMesh);
        else emit(mesh,world,pos,context,false);
    }
    private static String mergeLayer(String a, String b) {
        if (a.equals("translucent") || b.equals("translucent")) return "translucent";
        if (a.equals("cutout") || b.equals("cutout")) return "cutout";
        if (a.equals("cutout_mipped") || b.equals("cutout_mipped")) return "cutout_mipped";
        return "solid";
    }
    private static RenderLayer renderLayer(String layer) {
        return switch (layer) { case "translucent" -> RenderLayer.getTranslucent(); case "cutout" -> RenderLayer.getCutout(); case "cutout_mipped" -> RenderLayer.getCutoutMipped(); default -> RenderLayer.getSolid(); };
    }
    private static BlendMode blend(String layer) {
        return switch (layer) { case "translucent" -> BlendMode.TRANSLUCENT; case "cutout" -> BlendMode.CUTOUT; case "cutout_mipped" -> BlendMode.CUTOUT_MIPPED; default -> BlendMode.SOLID; };
    }
    private static <T extends Comparable<T>> BlockState setProperty(BlockState state, Property<T> property, String text) {
        return state.with(property, property.parse(text).orElseThrow(() -> new IllegalArgumentException("Invalid native grid tint property value: " + text)));
    }
    private static final class Model implements BakedModel, FabricBakedModel {
        private final BakedModel marker;
        private final Session loading;
        private final int index;
        private final SharedMesh mesh;
        private final boolean inventory;
        Model(BakedModel marker, Session loading, int index, SharedMesh mesh, boolean inventory) {
            this.marker = marker; this.loading = loading; this.index = index; this.mesh = mesh; this.inventory = inventory;
        }
        @Override public boolean isVanillaAdapter() { return !inventory && mesh.vanilla; }
        @Override public List<BakedQuad> getQuads(BlockState state, Direction side, Random random) { return mesh.faces[side == null ? 6 : side.ordinal()]; }
        @Override public void emitBlockQuads(BlockRenderView world, BlockState state, BlockPos pos, Supplier<Random> random, RenderContext context) {
            if (mesh.vanilla) { context.bakedModelConsumer().accept(this, state); return; }
            if (mesh.fixedMesh != null) { context.meshConsumer().accept(mesh.fixedMesh); return; }
            emit(mesh, world, pos, context, false);
        }
        @Override public void emitItemQuads(ItemStack stack, Supplier<Random> random, RenderContext context) {
            int selected = index;
            var tag = stack.getSubNbt("BlockStateTag");
            if (tag != null && tag.contains("part", 8)) try {
                int part = Integer.parseInt(tag.getString("part"));
                int candidate = index / NativeGridCatalog.BANK_SIZE * NativeGridCatalog.BANK_SIZE + part;
                if (part >= 0 && part < NativeGridCatalog.BANK_SIZE && candidate < NativeGridCatalog.cells().size()) selected = candidate;
            } catch (NumberFormatException ignored) {}
            var membership = NativeGridCatalog.membership(selected);
            SharedMesh itemMesh = membership == null ? loading.baked[NativeGridCatalog.cell(selected).mesh()]
                    : loading.wholeItems.get(membership.object().baseCell());
            if (itemMesh == null) throw new IllegalStateException("Native grid item mesh was not prepared by model baking");
            if (membership != null && itemMesh.fixedMesh != null) { context.meshConsumer().accept(itemMesh.fixedMesh); return; }
            emit(itemMesh, null, null, context, true);
        }
        @Override public boolean useAmbientOcclusion() { return mesh.ambient; }
        @Override public boolean hasDepth() { return true; }
        @Override public boolean isSideLit() { return true; }
        @Override public boolean isBuiltin() { return false; }
        @Override public Sprite getParticleSprite() { return mesh.quads.isEmpty() ? marker.getParticleSprite() : mesh.quads.get(0).baked().getSprite(); }
        @Override public ModelTransformation getTransformation() { return marker.getTransformation(); }
        @Override public ModelOverrideList getOverrides() { return marker.getOverrides(); }
    }
    private static void emit(SharedMesh mesh, BlockRenderView world, BlockPos pos, RenderContext context, boolean item) {
        var emitter = context.getEmitter();
        for (Quad quad : mesh.quads) {
            emitter.fromVanilla(quad.baked(), quad.material(), item ? null : quad.cull());
            if (quad.tintSource() != null) {
                BlockPos tintPos = pos == null ? null : pos.add(quad.tintOffset());
                int tint = quad.remainingGrass() ? RemainingSourceTint.color((CompositeRootBlock) quad.tintSource().getBlock(), world, tintPos)
                        : MinecraftClient.getInstance().getBlockColors().getColor(quad.tintSource(), world, tintPos, quad.baked().getColorIndex());
                int[] raw = quad.baked().getVertexData();
                int[] colors = new int[4];
                for (int vertex = 0; vertex < 4; vertex++) {
                    int color = raw[vertex * 8 + 3];
                    colors[vertex] = color & 0xff000000 | (color & 255) * (tint >> 16 & 255) / 255
                            | (color >> 8 & 255) * (tint >> 8 & 255) / 255 << 8
                            | (color >> 16 & 255) * (tint & 255) / 255 << 16;
                }
                emitter.color(colors[0], colors[1], colors[2], colors[3]);
            }
            emitter.emit();
        }
    }
    public static Map<String, Object> metrics() {
        Session current = session;
        long quads = 0, meshes = 0, vanilla = 0;
        if (current != null) for (SharedMesh mesh : current.baked) if (mesh != null) { meshes++; quads += mesh.quads.size(); if (mesh.vanilla) vanilla++; }
        return Map.of("compiledCells", NativeGridCatalog.cells().size(), "sharedMeshes", meshes,
                "sharedQuads", quads, "vanillaAdapterMeshes", vanilla, "perInstanceRenderEntries", 0,
                "wholeObjectTemplates", NativeGridCatalog.wholeObjects().size());
    }
}
