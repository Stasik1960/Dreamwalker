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
        volatile BakedModel adapter;
        final List<BakedQuad>[] faces;
        final List<Quad> quads;
        private volatile Mesh fixedMesh; private final boolean fixedMeshEligible;
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
            fixedMeshEligible = !vanilla && quads.stream().noneMatch(quad -> quad.tintSource() != null);
        }
        Mesh fixedMesh() {
            Mesh result = fixedMesh; if (result != null || !fixedMeshEligible) return result;
            synchronized (this) { if (fixedMesh != null) return fixedMesh;
                var builder = requireRenderer().meshBuilder(); var emitter = builder.getEmitter();
                for (Quad quad : quads) { emitter.fromVanilla(quad.baked(), quad.material(), quad.cull()); emitter.emit(); }
                fixedMesh = builder.build(); return fixedMesh; } }
    }
    private static net.fabricmc.fabric.api.renderer.v1.Renderer requireRenderer() {
        var renderer = RendererAccess.INSTANCE.getRenderer();
        if (renderer == null) throw new IllegalStateException("Native grid materials need Fabric Rendering API (Indium when Sodium is installed)");
        return renderer;
    }
    private static final class Session {
        final byte[][] raw;
        final SharedMesh[] baked;
        final RenderLayer[] layers;
        final Sprite[] sprites;
        final Map<Integer, RenderMaterial> materials = new HashMap<>();
        final Map<Integer, SharedMesh> wholeItems = new HashMap<>();
        Function<SpriteIdentifier,Sprite> textureGetter;
        Session() throws IOException {
            int count = NativeGridCatalog.quadCounts().size();
            raw = new byte[count+NativeVisualRepairs.groups().size()][];
            baked = new SharedMesh[raw.length];
            layers = new RenderLayer[raw.length];
            sprites = new Sprite[NativeGridCatalog.materials().size()];
            try (InputStream stream = NativeGridClient.class.getResourceAsStream(NativeGridCatalog.DIRECTORY + "meshes.bin")) {
                if (stream == null) throw new FileNotFoundException("Missing compiled native grid meshes");
                try (DataInputStream input = new DataInputStream(new BufferedInputStream(stream, 1 << 20))) {
                    if (input.readInt() != 0x4e475131 || input.readInt() != count)
                        throw new IOException("Native grid mesh header/catalog mismatch");
                    for (int mesh = 0; mesh < count; mesh++) {
                        int quads = input.readInt();
                        if (quads != NativeGridCatalog.quadCounts().get(mesh)) throw new IOException("Native grid quad count mismatch");
                        byte[] encoded=input.readNBytes(Math.multiplyExact(quads,139));
                        if(encoded.length!=quads*139)throw new EOFException("Truncated native mesh");
                        raw[mesh]=encoded;var metadata=java.nio.ByteBuffer.wrap(encoded);
                        String layer = "solid";
                        for (int q = 0; q < quads; q++) {
                            metadata.position(q*139);
                            int material = metadata.getInt(), cull = metadata.get(), face = metadata.get();
                            int tint = metadata.getInt(), flags = Byte.toUnsignedInt(metadata.get());
                            if (material < 0 || material >= sprites.length || cull < -1 || cull > 5 || face < 0 || face > 5 || (flags & ~7) != 0)
                                throw new IOException("Invalid compiled native grid quad metadata");
                            layer = mergeLayer(layer, NativeGridCatalog.materials().get(material).layer());
                        }
                        layers[mesh] = renderLayer(layer);
                    }
                    if (input.read() != -1) throw new IOException("Trailing compiled native grid mesh data");
                }
            }
            for(int i=0;i<NativeVisualRepairs.groups().size();i++){
                var group=NativeVisualRepairs.groups().get(i);byte[] original=raw[group.originalMesh()],extra=group.extra();
                byte[] combined=Arrays.copyOf(original,original.length+extra.length);System.arraycopy(extra,0,combined,original.length,extra.length);raw[count+i]=combined;
                var metadata=java.nio.ByteBuffer.wrap(combined);String layer="solid";
                for(int q=0;q<combined.length/139;q++){int material=metadata.getInt(q*139);if(material<0||material>=sprites.length)throw new IOException("Visual repair material bounds");layer=mergeLayer(layer,NativeGridCatalog.materials().get(material).layer());}
                layers[count+i]=renderLayer(layer);
            }
        }
        private RawQuad[] decode(int mesh){
            byte[] encoded=Objects.requireNonNull(raw[mesh]);var input=java.nio.ByteBuffer.wrap(encoded);
            RawQuad[] result=new RawQuad[encoded.length/139];
            for(int q=0;q<result.length;q++){
                int material=input.getInt(),cull=input.get(),face=input.get(),tint=input.getInt(),flags=Byte.toUnsignedInt(input.get());int[] data=new int[32];
                for(int vertex=0;vertex<4;vertex++){int at=vertex*8;for(int field=0;field<5;field++){int bits=input.getInt();float value=Float.intBitsToFloat(bits);if(!Float.isFinite(value)||field<3&&(value< -1e-6F||value>1.000001F))throw new IllegalArgumentException("Native grid vertex bounds");data[at+(field<3?field:field+1)]=bits;}data[at+3]=input.getInt();data[at+6]=input.getInt();data[at+7]=input.getInt();}
                result[q]=new RawQuad(material,cull,face,tint,flags,data);
            }
            return result;
        }
        synchronized SharedMesh mesh(int visual){return bake(NativeVisualRepairs.mesh(visual),Objects.requireNonNull(textureGetter,"Native atlas getter"));}
        synchronized SharedMesh bake(int mesh, Function<SpriteIdentifier, Sprite> textureGetter) {
            if (baked[mesh] != null) return baked[mesh];
            RawQuad[] source = decode(mesh);
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
            raw[mesh] = null; // Release this mesh's encoded bytes after its first bake.
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
        NativeVisualClient.initialize();
        ModelLoadingPlugin.register(plugin -> {
            final Session loading;
            try { loading = new Session(); } catch (IOException failure) { throw new IllegalStateException("Cannot read native city geometry", failure); }
            session = loading;
            plugin.modifyModelAfterBake().register(ModelModifier.WRAP_PHASE, (marker, context) -> {
                if (!(context.id() instanceof ModelIdentifier id) || !id.getNamespace().equals("bloodborne_dw")) return marker;
                if(id.getPath().startsWith("native_physics_")){
                    loading.textureGetter=context.textureGetter();int bank=Integer.parseInt(id.getPath().substring("native_physics_".length()));int part=0;
                    boolean inventory=id.getVariant().equals("inventory");if(!inventory)for(String property:id.getVariant().split(","))if(property.startsWith("part="))part=Integer.parseInt(property.substring(5));
                    return new PhysicalModel(marker,loading,Math.min(bank*128+part,NativeVisualLayout.count()-1),inventory);
                }
                if(!id.getPath().startsWith("native_grid_"))return marker;
                loading.textureGetter=context.textureGetter();
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
        if(state.getBlock() instanceof NativePhysicsBlock block)return renderLayer(NativeVisualLayout.layer(block.physics(state)));
        Session current = session;
        return current == null ? RenderLayer.getSolid() : current.layers[NativeGridCatalog.cell(state).mesh()];
    }
    static void emitBridgeBase(int index,BlockRenderView world,BlockPos pos,RenderContext context){
        Session current=session;
        SharedMesh mesh=current==null?null:current.mesh(index);
        if(mesh==null)throw new IllegalStateException("Native bridge shared mesh was not prepared during baking");
        Mesh fixed=mesh.fixedMesh(); if(fixed!=null)context.meshConsumer().accept(fixed);
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
    public static Sprite particle(int visual){var mesh=session.mesh(visual);return mesh.quads.isEmpty()?MinecraftClient.getInstance().getBakedModelManager().getMissingModel().getParticleSprite():mesh.quads.get(0).baked().getSprite();}
    /** Resolve before Indigo chooses its normal vanilla adapter fast path.
     * Other Fabric renderers can use PhysicalModel.emitBlockQuads instead. */
    public static BakedModel terrainModel(BlockRenderView world,BlockState state,BlockPos pos,BakedModel fallback){
        if(!(state.getBlock() instanceof NativePhysicsBlock physical))return fallback;
        int visual=NativeVisuals.forView(world,pos,state);if(NativeVisualLayout.physics(visual)!=physical.physics(state))throw new IllegalStateException("Native terrain snapshot mismatch at "+pos);
        var current=session;var mesh=current.mesh(visual);var model=mesh.adapter;
        if(model==null)synchronized(mesh){model=mesh.adapter;if(model==null){var marker=fallback instanceof PhysicalModel p?p.marker:fallback;mesh.adapter=model=new Model(marker,current,visual,mesh,false);}}
        return model;
    }
    private static final class PhysicalModel implements BakedModel,FabricBakedModel {
        private final BakedModel marker;private final Session loading;private final int physics;private final boolean inventory;
        PhysicalModel(BakedModel marker,Session loading,int physics,boolean inventory){this.marker=marker;this.loading=loading;this.physics=physics;this.inventory=inventory;}
        @Override public boolean isVanillaAdapter(){return false;}
        @Override public List<BakedQuad> getQuads(BlockState state,Direction side,Random random){return List.of();}
        @Override public void emitBlockQuads(BlockRenderView world,BlockState state,BlockPos pos,Supplier<Random> random,RenderContext context){
            int visual=NativeVisuals.forView(world,pos,state);if(NativeVisualLayout.physics(visual)!=((NativePhysicsBlock)state.getBlock()).physics(state))throw new IllegalStateException("Native render physics/index mismatch at "+pos);
            var mesh=loading.mesh(visual);
            if(mesh.vanilla){BakedModel adapter=mesh.adapter;if(adapter==null){synchronized(mesh){adapter=mesh.adapter;if(adapter==null)mesh.adapter=adapter=new Model(marker,loading,visual,mesh,false);}}context.bakedModelConsumer().accept(adapter,state);return;}
            Mesh fixed=mesh.fixedMesh();if(fixed!=null)context.meshConsumer().accept(fixed);else emit(mesh,world,pos,context,false);
        }
        @Override public void emitItemQuads(ItemStack stack,Supplier<Random> random,RenderContext context){int visual=stack.hasNbt()&&stack.getNbt().contains(NativeVisuals.ITEM_KEY,3)?stack.getNbt().getInt(NativeVisuals.ITEM_KEY):NativeVisualLayout.representative(physics);if(visual<0||visual>=NativeGridCatalog.cells().size()||NativeVisualLayout.physics(visual)<0)throw new IllegalArgumentException("Invalid visual item");emit(loading.mesh(visual),null,null,context,true);}
        @Override public boolean useAmbientOcclusion(){return NativeVisualLayout.ambient(physics);}
        @Override public boolean hasDepth(){return true;}
        @Override public boolean isSideLit(){return true;}
        @Override public boolean isBuiltin(){return false;}
        @Override public Sprite getParticleSprite(){return particle(NativeVisualLayout.representative(physics));}
        @Override public ModelTransformation getTransformation(){return marker.getTransformation();}
        @Override public ModelOverrideList getOverrides(){return marker.getOverrides();}
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
            Mesh fixed = mesh.fixedMesh(); if (fixed != null) { context.meshConsumer().accept(fixed); return; }
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
            Mesh fixed = membership == null ? null : itemMesh.fixedMesh(); if (fixed != null) { context.meshConsumer().accept(fixed); return; }
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
