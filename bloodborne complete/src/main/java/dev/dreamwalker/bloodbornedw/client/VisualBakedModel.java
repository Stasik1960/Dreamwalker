package dev.dreamwalker.bloodbornedw.client;

import dev.dreamwalker.bloodbornedw.block.DwBlocks;
import dev.dreamwalker.bloodbornedw.block.Visual;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.ModelOverrideList;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.texture.Sprite;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

import java.util.List;
import java.util.function.Supplier;

/** Client-only block-render-time selector.  The guard lets paired wrapped models delegate safely. */
final class VisualBakedModel implements BakedModel, FabricBakedModel {
    private static final ThreadLocal<Boolean> SELECTING = ThreadLocal.withInitial(() -> false);
    private final BakedModel delegate;

    VisualBakedModel(BakedModel delegate) { this.delegate = delegate; }

    @Override public boolean isVanillaAdapter() { return false; }
    @Override public void emitBlockQuads(BlockRenderView view, BlockState state, BlockPos pos, Supplier<Random> random, RenderContext context) {
        if (SELECTING.get() || !state.contains(DwBlocks.VISUAL)) { emit(delegate, view, state, pos, random, context); return; }
        String id = BloodborneDwClient.id(state);
        if (id == null) { emit(delegate, view, state, pos, random, context); return; }
        Visual visual = BloodborneDwClient.effective(pos, id, state.get(DwBlocks.VISUAL));
        BlockState desired = state.with(DwBlocks.VISUAL, visual);
        BakedModel paired = MinecraftClient.getInstance().getBlockRenderManager().getModel(desired);
        SELECTING.set(true);
        try { emit(paired, view, desired, pos, random, context); }
        finally { SELECTING.remove(); }
    }
    private static void emit(BakedModel model, BlockRenderView view, BlockState state, BlockPos pos, Supplier<Random> random, RenderContext context) {
        ((FabricBakedModel) model).emitBlockQuads(view, state, pos, random, context);
    }
    @Override public void emitItemQuads(ItemStack stack, Supplier<Random> random, RenderContext context) { ((FabricBakedModel) delegate).emitItemQuads(stack, random, context); }
    @Override public List<BakedQuad> getQuads(BlockState state, Direction face, Random random) { return delegate.getQuads(state, face, random); }
    @Override public boolean useAmbientOcclusion() { return delegate.useAmbientOcclusion(); }
    @Override public boolean hasDepth() { return delegate.hasDepth(); }
    @Override public boolean isSideLit() { return delegate.isSideLit(); }
    @Override public boolean isBuiltin() { return delegate.isBuiltin(); }
    @Override public Sprite getParticleSprite() { return delegate.getParticleSprite(); }
    @Override public ModelTransformation getTransformation() { return delegate.getTransformation(); }
    @Override public ModelOverrideList getOverrides() { return delegate.getOverrides(); }
}
