package dev.dreamwalker.bloodbornedw.visual;

import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
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

final class FacadeModel implements BakedModel, FabricBakedModel {
    private final BakedModel delegate;
    private final int fixedRole;
    private final boolean general;
    FacadeModel(BakedModel delegate,int fixedRole,boolean general) { this.delegate=delegate; this.fixedRole=fixedRole; this.general=general; }
    BakedModel delegate() { return delegate; }
    int role() { return fixedRole; }
    boolean general() { return general; }
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,Random random) {
        List<BakedQuad> quads=delegate.getQuads(state,side,random);
        return fixedRole==0&&!general?quads:quads.stream().map(q->FacadeMaterials.quad(q,fixedRole,general)).toList();
    }
    @Override public boolean isVanillaAdapter() {
        return fixedRole!=0 || !CityVisualClient.isAlt() ? ((FabricBakedModel) delegate).isVanillaAdapter() : false;
    }
    @Override public void emitBlockQuads(BlockRenderView world,BlockState state,BlockPos pos,Supplier<Random> random,RenderContext context) {
        FabricBakedModel fabric=(FabricBakedModel)delegate;
        int role=fixedRole!=0?fixedRole:FacadeMaterials.role(pos);
        if(role==0&&!general) { fabric.emitBlockQuads(world,state,pos,random,context); return; }
        context.pushTransform(quad->FacadeMaterials.transform(quad,role,general));
        try { fabric.emitBlockQuads(world,state,pos,random,context); } finally { context.popTransform(); }
    }
    @Override public void emitItemQuads(ItemStack stack,Supplier<Random> random,RenderContext context) { ((FabricBakedModel)delegate).emitItemQuads(stack,random,context); }
    @Override public boolean useAmbientOcclusion() { return delegate.useAmbientOcclusion(); }
    @Override public boolean hasDepth() { return delegate.hasDepth(); }
    @Override public boolean isSideLit() { return delegate.isSideLit(); }
    @Override public boolean isBuiltin() { return delegate.isBuiltin(); }
    @Override public Sprite getParticleSprite() { return delegate.getParticleSprite(); }
    @Override public ModelTransformation getTransformation() { return delegate.getTransformation(); }
    @Override public ModelOverrideList getOverrides() { return delegate.getOverrides(); }
}
