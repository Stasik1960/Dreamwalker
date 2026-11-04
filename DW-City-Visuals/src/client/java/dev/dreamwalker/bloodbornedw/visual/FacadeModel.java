package dev.dreamwalker.bloodbornedw.visual;

import java.util.List;
import java.util.ArrayList;
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
import dev.dreamwalker.bloodbornedw.editor.EditorCells;
import dev.dreamwalker.bloodbornedw.editor.EditorRenderScope;
import dev.dreamwalker.bloodbornedw.editor.RgbProfiles;

final class FacadeModel implements BakedModel, FabricBakedModel {
    private final BakedModel delegate;
    private final int fixedRole;
    private final boolean forceBase;
    private final boolean manualStyle;
    /** Model selection already resolved the root's appearance from this render snapshot. */
    private final boolean resolved;
    private final RgbProfiles.Profile rgbProfile;
    private final int rgbColor;
    private final long rgbGeneration;
    FacadeModel(BakedModel delegate,int fixedRole) { this(delegate,fixedRole,false,false); }
    FacadeModel(BakedModel delegate,int fixedRole,boolean forceBase) { this(delegate,fixedRole,forceBase,false); }
    FacadeModel(BakedModel delegate,int fixedRole,boolean forceBase,boolean manualStyle) { this(delegate,fixedRole,forceBase,manualStyle,false); }
    FacadeModel(BakedModel delegate,int fixedRole,boolean forceBase,boolean manualStyle,boolean resolved) {
        this(delegate,fixedRole,forceBase,manualStyle,resolved,null,0,0);
    }
    FacadeModel(BakedModel delegate,RgbProfiles.Profile profile,int color,long generation) {
        this(delegate,0,false,false,true,profile,color,generation);
    }
    private FacadeModel(BakedModel delegate,int fixedRole,boolean forceBase,boolean manualStyle,boolean resolved,
                        RgbProfiles.Profile profile,int color,long generation) {
        this.delegate=delegate;
        this.fixedRole=fixedRole;
        this.forceBase=forceBase;
        this.manualStyle=manualStyle;
        this.resolved=resolved;
        this.rgbProfile=profile;
        this.rgbColor=color;
        this.rgbGeneration=generation;
    }
    BakedModel delegate() { return delegate; }
    int role() { return fixedRole; }
    boolean manualStyle() { return manualStyle; }
    boolean resolved() { return resolved; }
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,Random random) {
        List<BakedQuad> quads=delegate.getQuads(state,side,random);
        if(!CityVisualClient.isAlt())return quads;
        boolean rgb = rgbProfile != null && CityFacadesClient.currentRgbGeneration(rgbGeneration);
        if (!rgb && fixedRole == 0) return quads;
        List<BakedQuad> changed=null;
        for(int i=0;i<quads.size();i++) {
            BakedQuad source = quads.get(i);
            BakedQuad result = rgb ? FacadeMaterials.rgbQuad(source,rgbColor,rgbProfile)
                    : FacadeMaterials.quad(source,fixedRole,manualStyle);
            if(result!=source) {
                if(changed==null)changed=new ArrayList<>(quads);
                changed.set(i,result);
            }
        }
        return changed==null?quads:changed;
    }
    @Override public boolean isVanillaAdapter() {
        return fixedRole!=0 || !CityVisualClient.isAlt() ? ((FabricBakedModel) delegate).isVanillaAdapter() : false;
    }
    @Override public void emitBlockQuads(BlockRenderView world,BlockState state,BlockPos pos,Supplier<Random> random,RenderContext context) {
        FabricBakedModel fabric=(FabricBakedModel)delegate;
        if(!CityVisualClient.isAlt()) { fabric.emitBlockQuads(world,state,pos,random,context); return; }
        if (rgbProfile != null && CityFacadesClient.currentRgbGeneration(rgbGeneration)) {
            emitRgb(fabric,world,state,pos,random,context,rgbProfile,rgbColor);
            return;
        }
        int painted=resolved?-1:EditorRenderScope.style(world,pos);
        if (EditorCells.isRgb(painted)) {
            RgbProfiles.Profile profile = CityFacadesClient.activeRgb(RgbProfiles.at(world,pos,state));
            if (profile != null) {
                emitRgb(fabric,world,state,pos,random,context,profile,painted & 0xffffff);
                return;
            }
            painted = -1;
        }
        int role=resolved?fixedRole:forceBase?0:(painted>=0?painted:(fixedRole!=0?fixedRole:ShutterSubstrate.source(state)?ShutterSubstrate.role(world,pos):FacadeMaterials.role(pos)));
        if(role==0) { fabric.emitBlockQuads(world,state,pos,random,context); return; }
        boolean manual=painted>=0||manualStyle;
        context.pushTransform(quad->FacadeMaterials.transform(quad,role,manual));
        try { fabric.emitBlockQuads(world,state,pos,random,context); } finally { context.popTransform(); }
    }
    private static void emitRgb(FabricBakedModel fabric,BlockRenderView world,BlockState state,BlockPos pos,
                                Supplier<Random> random,RenderContext context,RgbProfiles.Profile profile,int color) {
        context.pushTransform(quad -> FacadeMaterials.transformRgb(quad,color,profile));
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
