package com.yharnam.casino.client.render;

import com.yharnam.casino.*;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

public final class GachaponRenderer implements BlockEntityRenderer<GachaponEntity> {
    private static final Identifier TEX = YharnamCasino.id("textures/machine/gachapon.png");
    private static final RenderLayer OPAQUE = RenderLayer.getEntityCutout(TEX);
    private static final RenderLayer GLASS = RenderLayer.getEntityTranslucentCull(TEX);
    private static final Identifier PARTS = YharnamCasino.id("models/machine/gachapon_moving.mesh");
    private static MachineMesh parts;
    public GachaponRenderer(BlockEntityRendererFactory.Context context) {}

    public static void registerItems() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            public Identifier getFabricId() { return YharnamCasino.id("capsule_mesh"); }
            public void reload(ResourceManager resources) { parts=MachineMesh.load(resources,PARTS,true); }
        });
        for (var rarity:GachaRarity.values()) {
            BuiltinItemRendererRegistry.INSTANCE.register(YharnamCasino.capsule(rarity),(stack,mode,m,out,light,overlay)->{
                m.push();
                m.translate(.5,.5,.5);
                m.scale(4.2f,4.2f,4.2f);
                m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(68));
                m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-18));
                var client=MinecraftClient.getInstance();
                float time=client.world==null?0:(client.world.getTime()%96)+client.getTickDelta();
                capsule(rarity,m,out,light,time);
                m.pop();
            });
        }
    }
    private static void part(String name,MatrixStack m,VertexConsumerProvider out,RenderLayer layer,int light) {
        if (parts==null) parts=MachineMesh.load("gachapon_moving");
        SlotMachineRenderer.renderPart(parts.part(name),0,m,out.getBuffer(layer),light);
    }
    private static void capsule(GachaRarity color,MatrixStack m,VertexConsumerProvider out,int light,float time) {
        String id=color.id();
        part(id+"_shell",m,out,OPAQUE,light);
        m.push();
        float pulse=1f+.035f*(float)Math.sin(time*Math.PI/48);
        m.translate(0,.029,0);
        m.scale(pulse,pulse,pulse);
        m.translate(0,-.029,0);
        part(id+"_core",m,out,OPAQUE,LightmapTextureManager.MAX_LIGHT_COORDINATE);
        m.pop();
        part(id+"_glass",m,out,GLASS,light);
    }
    @Override public void render(GachaponEntity g,float delta,MatrixStack m,VertexConsumerProvider out,int light,int overlay) {
        m.push();
        m.translate(.5,0,.5);
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-g.getCachedState().get(GachaponBlock.FACING).asRotation()));
        float t=g.elapsed(delta);
        boolean turning=g.phase()==GachaponEntity.Phase.TURNING;
        m.push();
        m.translate(.139,.781,.397);
        float turn=Math.min(1,t/20f);
        if (turning) m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-360*turn*turn*(3-2*turn)));
        part("crank",m,out,OPAQUE,light);
        m.pop();
        if (g.result()!=null && (g.phase()==GachaponEntity.Phase.TRAY || turning && t>=19)) {
            float u=turning?Math.min(1,(t-19)/(GachaponEntity.TURN_TICKS-19)):1;
            double bounce=.035*Math.abs(Math.sin(u*Math.PI*2))*(1-u);
            m.push();
            m.translate(.026,.344+.105*(1-u*u)+bounce,.125+.171*u);
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(105-48*u));
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-17));
            capsule(g.result(),m,out,light,(g.getWorld().getTime()%96)+delta);
            m.pop();
        }
        m.pop();
    }
    @Override public int getRenderDistance() { return 28; }
}
