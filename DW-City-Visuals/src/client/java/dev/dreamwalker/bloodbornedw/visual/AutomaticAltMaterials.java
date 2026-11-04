package dev.dreamwalker.bloodbornedw.visual;

import dev.dreamwalker.bloodbornedw.composite.CornerQuadParentScope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import dev.dreamwalker.bloodbornedw.composite.NativeVisuals;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.model.SpriteFinder;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.ModelOverrideList;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import dev.dreamwalker.bloodbornedw.visual.ObsidianBandAlt;

/**
 * Position/profile scoped ALT sprite substitutions. This deliberately does
 * not alter an atlas sprite or shared source model, so unrelated consumers of
 * the same source material retain their existing appearance.
 */
public final class AutomaticAltMaterials {
    private static final Identifier OVERWORLD = new Identifier("minecraft", "overworld");
    private static final Identifier STATIC_15 = id("static_architecture_15");
    private static final Identifier STATIC_0 = id("static_architecture_0");
    private static final Identifier STATIC_1 = id("static_architecture_1");
    private static final Identifier STATIC_43 = id("static_architecture_43");
    private static final Identifier STATIC_38 = id("static_architecture_38");
    private static final Identifier PHYSICS_1 = id("native_physics_1");
    private static final Identifier PHYSICS_11 = id("native_physics_11");
    private static final Identifier SCENE05_A = id("block/city_altfix/scene05_06_stone_a");
    private static final Identifier SCENE05_B = id("block/city_altfix/scene05_06_stone_b");
    private static final Identifier SCENE08 = id("block/city_altfix/scene08_triangle");
    private static final Identifier SCENE10 = id("block/city_altfix/scene10_masonry");
    private static final Identifier SCENE11 = id("block/city_altfix/scene11_hanging");
    private static final Identifier SCENE07_FRONT = id("block/city_altfix/scene07_front");
    private static final Identifier SCENE07_BODY = id("block/city_altfix/scene07_body");
    private static final Identifier SCENE14_STONE = id("block/city_altfix/scene14_stone");
    private static final Identifier SCENE14_TRIM = id("block/city_altfix/scene14_trim");
    private record WrapperKey(BakedModel model, Identifier source, Identifier target) {}
    private static final Map<WrapperKey,BakedModel> WRAPPERS = new LinkedHashMap<>(16, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<WrapperKey,BakedModel> eldest) { return size() > 16; }
    };
    private AutomaticAltMaterials() {}

    /** Wrap one model for an exact source-to-authored-ALT sprite mapping. */
    public static synchronized void reload() { WRAPPERS.clear(); }

    public static synchronized BakedModel wrap(BakedModel model, Identifier source, Identifier target) {
        for (BakedModel current = model; current instanceof RemapModel remap; current = remap.delegate)
            if (remap.source.equals(source) && remap.target.equals(target)) return model;
        WrapperKey key = new WrapperKey(model, source, target);
        return WRAPPERS.computeIfAbsent(key, ignored -> new RemapModel(model, source, target));
    }

    /** Exact automatic ALT selectors. Explicit legacy/RGB values always win. */
    public static BakedModel apply(BakedModel model, BlockState state, BlockRenderView world,
                                   BlockPos pos, Identifier part, int painted) {
        if (!CityVisualClient.isAlt() || painted != -1 || state == null) return model;
        if (ObsidianBandAlt.matches(state)) return wrap(model, ObsidianBandAlt.SOURCE, ObsidianBandAlt.TARGET);
        if (world == null || pos == null || !scenePosition(pos)) return model;
        var clientWorld = MinecraftClient.getInstance().world;
        if (clientWorld == null || !OVERWORLD.equals(clientWorld.getRegistryKey().getValue())) return model;
        // Static-architecture geometry passes the source pose state here;
        // the block state at root is the carrier that identifies the exact
        // reviewed city cell and part property.
        BlockState targetState = world.getBlockState(pos);
        Identifier block = Registries.BLOCK.getId(targetState.getBlock());
        String modelPath = part == null ? "" : part.getPath();
        if (cell(pos,targetState,block,STATIC_15,67,modelPath,"remaining/source/minecraft/block/_wall_dec_out")
                || cell(pos,targetState,block,STATIC_0,55,modelPath,"remaining/source/minecraft/block/_wall_dec_out")) {
            model = wrap(model, id("block/remaining/source/minecraft/block/addon/spirelamp_001"), SCENE05_A);
            return wrap(model, id("block/remaining/source/minecraft/block/addon/spirelamp_009"), SCENE05_B);
        }
        if (cell(pos,targetState,block,STATIC_1,49,modelPath,"remaining/source/minecraft/block/_wall_dec_top_1"))
            return wrap(model, id("block/remaining/source/minecraft/block/addon/spirelamp_0028"), SCENE08);
        if (cell(pos,targetState,block,STATIC_43,50,modelPath,"remaining/source/minecraft/block/oak_stairs_out"))
            return wrap(model, id("block/remaining/source/minecraft/block/addon/spirelamp_0018"), SCENE10);
        if (cell(pos,targetState,block,STATIC_38,89,modelPath,"remaining/source/minecraft/block/_wall_dec_bottom"))
            return wrap(model, id("block/remaining/source/minecraft/block/addon/spirelamp_002"), SCENE11);
        if (position(pos,65,31,-929) && block.equals(PHYSICS_1) && partValue(targetState,26)
                && NativeVisuals.index(world,pos) == 833) {
            model = wrap(model, id("block/remaining/source/minecraft/block/cobbled_deepslate_we"), SCENE07_FRONT);
            return wrap(model, id("block/remaining/source/minecraft/block/cobbled_deepslate"), SCENE07_BODY);
        }
        if (position(pos,29,-10,-1002) && block.equals(PHYSICS_11) && partValue(targetState,48)
                && NativeVisuals.index(world,pos) == 89925) {
            model = wrap(model, id("block/remaining/source/minecraft/block/addon/spirelamp_0065"), SCENE14_STONE);
            return wrap(model, id("block/remaining/source/minecraft/block/addon/spirelamp_0037"), SCENE14_TRIM);
        }
        return model;
    }

    private static Identifier id(String value) { return new Identifier("bloodborne_dw", value); }
    private static boolean position(BlockPos pos,int x,int y,int z) { return pos.getX()==x && pos.getY()==y && pos.getZ()==z; }
    private static boolean scenePosition(BlockPos pos) {
        return position(pos,206,61,-961) || position(pos,190,60,-965) || position(pos,88,21,-856)
                || position(pos,12,-5,-1032) || position(pos,14,-6,-1032)
                || position(pos,65,31,-929) || position(pos,29,-10,-1002);
    }
    private static boolean cell(BlockPos pos,BlockState state,Identifier actual,Identifier blockId,int part,
                                String actualModel,String modelPath) {
        return actual.equals(blockId) && positionMatches(pos,blockId,part) && partValue(state,part)
                && (actualModel.isEmpty() || actualModel.equals(modelPath));
    }
    private static boolean positionMatches(BlockPos pos,Identifier blockId,int part) {
        if (blockId.equals(STATIC_15)) return position(pos,206,61,-961);
        if (blockId.equals(STATIC_0)) return position(pos,190,60,-965);
        if (blockId.equals(STATIC_1)) return position(pos,88,21,-856);
        if (blockId.equals(STATIC_43)) return position(pos,12,-5,-1032);
        if (blockId.equals(STATIC_38)) return position(pos,14,-6,-1032);
        return false;
    }
    private static boolean partValue(BlockState state,int expected) {
        for (Property<?> property : state.getProperties())
            if (property.getName().equals("part")) return Objects.toString(state.get(property)).equals(Integer.toString(expected));
        return false;
    }

    private static SpriteAtlasTexture atlas() {
        return (SpriteAtlasTexture) MinecraftClient.getInstance().getTextureManager()
                .getTexture(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE);
    }

    private static boolean loaded(Sprite sprite, Identifier id) {
        return sprite != null && id.equals(sprite.getContents().getId());
    }

    private static float map(float value, float fromMin, float fromMax, float toMin, float toMax) {
        return toMin + (value - fromMin) * (toMax - toMin) / (fromMax - fromMin);
    }

    private static final class RemapModel implements BakedModel, FabricBakedModel {
        private final BakedModel delegate;
        private final Identifier source;
        private final Identifier target;

        RemapModel(BakedModel delegate, Identifier source, Identifier target) {
            this.delegate = delegate;
            this.source = source;
            this.target = target;
        }

        @Override public List<BakedQuad> getQuads(BlockState state, Direction side, Random random) {
            if (!CityVisualClient.isAlt()) return delegate.getQuads(state, side, random);
            SpriteAtlasTexture atlas = atlas();
            Sprite to = atlas.getSprite(target);
            if (!loaded(to, target)) return delegate.getQuads(state, side, random);
            List<BakedQuad> quads = delegate.getQuads(state, side, random);
            List<BakedQuad> changed = null;
            for (int i = 0; i < quads.size(); i++) {
                BakedQuad original = quads.get(i);
                if (!source.equals(original.getSprite().getContents().getId())) continue;
                BakedQuad replacement = remap(original, to);
                if (replacement == original) continue;
                if (changed == null) changed = new ArrayList<>(quads);
                changed.set(i, replacement);
            }
            return changed == null ? quads : changed;
        }

        private BakedQuad remap(BakedQuad original, Sprite to) {
            Sprite from = original.getSprite();
            if (!source.equals(from.getContents().getId())) return original;
            int[] data = original.getVertexData().clone();
            for (int vertex = 0; vertex < 4; vertex++) {
                int offset = vertex * 8;
                data[offset + 4] = Float.floatToRawIntBits(map(Float.intBitsToFloat(data[offset + 4]),
                        from.getMinU(), from.getMaxU(), to.getMinU(), to.getMaxU()));
                data[offset + 5] = Float.floatToRawIntBits(map(Float.intBitsToFloat(data[offset + 5]),
                        from.getMinV(), from.getMaxV(), to.getMinV(), to.getMaxV()));
            }
            BakedQuad result = new BakedQuad(data, original.getColorIndex(), original.getFace(), to, original.hasShade());
            if (CornerQuadParentScope.parentFor(original) != null) CornerQuadParentScope.alias(original, result);
            return result;
        }

        @Override public boolean isVanillaAdapter() { return ((FabricBakedModel) delegate).isVanillaAdapter(); }
        @Override public void emitBlockQuads(BlockRenderView world, BlockState state, BlockPos pos,
                Supplier<Random> random, RenderContext context) {
            if (!CityVisualClient.isAlt()) {
                ((FabricBakedModel) delegate).emitBlockQuads(world, state, pos, random, context);
                return;
            }
            SpriteAtlasTexture atlas = atlas();
            Sprite to = atlas.getSprite(target);
            if (!loaded(to, target)) {
                ((FabricBakedModel) delegate).emitBlockQuads(world, state, pos, random, context);
                return;
            }
            context.pushTransform(quad -> transform(quad, atlas, to));
            try { ((FabricBakedModel) delegate).emitBlockQuads(world, state, pos, random, context); }
            finally { context.popTransform(); }
        }

        private boolean transform(MutableQuadView quad, SpriteAtlasTexture atlas, Sprite to) {
            Sprite from = SpriteFinder.get(atlas).find(quad);
            if (!source.equals(from.getContents().getId())) return true;
            for (int vertex = 0; vertex < 4; vertex++) {
                quad.uv(vertex, map(quad.u(vertex), from.getMinU(), from.getMaxU(), to.getMinU(), to.getMaxU()),
                        map(quad.v(vertex), from.getMinV(), from.getMaxV(), to.getMinV(), to.getMaxV()));
            }
            return true;
        }

        @Override public void emitItemQuads(ItemStack stack, Supplier<Random> random, RenderContext context) {
            ((FabricBakedModel) delegate).emitItemQuads(stack, random, context);
        }
        @Override public boolean useAmbientOcclusion() { return delegate.useAmbientOcclusion(); }
        @Override public boolean hasDepth() { return delegate.hasDepth(); }
        @Override public boolean isSideLit() { return delegate.isSideLit(); }
        @Override public boolean isBuiltin() { return delegate.isBuiltin(); }
        @Override public Sprite getParticleSprite() { return delegate.getParticleSprite(); }
        @Override public ModelTransformation getTransformation() { return delegate.getTransformation(); }
        @Override public ModelOverrideList getOverrides() { return delegate.getOverrides(); }
    }
}
