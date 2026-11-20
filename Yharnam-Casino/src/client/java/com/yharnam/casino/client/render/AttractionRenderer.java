package com.yharnam.casino.client.render;

import com.yharnam.casino.*;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

public final class AttractionRenderer implements BlockEntityRenderer<AttractionEntity> {
    private static final RenderLayer WHEEL=layer("roulette_table"), DERBY=layer("rat_derby");
    private static final RenderLayer GLASS=RenderLayer.getEntityTranslucent(YharnamCasino.id("textures/machine/rat_derby.png"));
    private static final RenderLayer CHIP=RenderLayer.getEntityCutout(YharnamCasino.id("textures/entity/cards/counter.png"));
    private static final int[] COLORS={0xbc5962,0x699ad8,0x76aa81,0xdfad60,0xbd8bcc,0xe6d4ae};
    private static final double[] LANES={.350,.046,-.258,-.562};
    private final MachineMesh table=MachineMesh.load("roulette_table_moving"), derby=MachineMesh.load("rat_derby_moving");
    private final TextRenderer font;
    public AttractionRenderer(BlockEntityRendererFactory.Context ctx) { font=ctx.getTextRenderer(); }
    private static RenderLayer layer(String id) {
        return RenderLayer.getEntityCutout(YharnamCasino.id("textures/machine/"+id+".png"));
    }
    @Override public void render(AttractionEntity game,float delta,MatrixStack m,VertexConsumerProvider out,int light,int overlay) {
        m.push();
        m.translate(.5,0,.5);
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-game.getCachedState().get(GamingFurnitureBlock.FACING).asRotation()));
        m.translate(.5,0,.5);
        if (game.derby()) renderDerby(game,delta,m,out,light);
        else renderWheel(game,delta,m,out,light);
        m.pop();
    }
    private void renderWheel(AttractionEntity game,float delta,MatrixStack m,VertexConsumerProvider out,int light) {
        var ball=game.ball(delta);
        m.push();
        m.translate(RouletteSpin.CX,RouletteSpin.ROTOR_Y,RouletteSpin.CZ);
        m.multiply(RotationAxis.POSITIVE_Y.rotation((float)-ball.rotor()));
        SlotMachineRenderer.renderPart(table.part("rotor"),0,m,out.getBuffer(WHEEL),light);
        m.pop();
        m.push();
        m.translate(ball.x(),ball.height(),ball.z());
        SlotMachineRenderer.renderPart(table.part("ball"),0,m,out.getBuffer(WHEEL),light);
        m.pop();
        var book=game.ledger();
        for (int i=0;i<=AttractionEntity.DEALER;i++) {
            float x=(float)AttractionEntity.seatX(i), z=(float)AttractionEntity.seatZ(i);
            boolean dealer=i==AttractionEntity.DEALER;
            boolean end=i==4;
            m.push();
            m.translate(x,0,z);
            m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(end ? 90 : i<2 ? 0 : 180));
            // The croupier's nameplate sits on the rail, clear of the ball track.
            float h=end?.912f:.855f;
            long cash=dealer?book.house():book.bankroll(i);
            chip(m,out,-.10f,h,0,.073f,COLORS[i],1,light);
            String name=game.name(i).isEmpty() ? (dealer?"КРУПЬЕ":"МЕСТО "+(i+1)) : game.name(i);
            label(name,.055,h+.001,0,.31f,.0075f,COLORS[i],m,out,light);
            if (cash>0) {
                chip(m,out,-.10f,h+.008f,0,.067f,COLORS[i],Math.min(4,Long.toString(cash).length()),light);
                label(Long.toString(cash),.055,h+.002,.038,.22f,.004f,0xffefd2,m,out,light);
            }
            m.pop();
        }
        for (var pile:game.piles()) {
            int i=pile.seat();
            float x=(float)pile.target().x()+(float)(.016*Math.sin(i*1.2566));
            float z=(float)pile.target().z()+(float)(.016*Math.cos(i*1.2566));
            chip(m,out,x,.855f+i*.0006f,z,.063f,COLORS[i],Math.min(4,pile.count()),light);
        }
        String line=game.moving()?"СТАВКИ ЗАКРЫТЫ":book.result()>=0?"ВЫПАЛО  "+book.result():"";
        if (!line.isEmpty()) label(line,.56,.856,.570,.72f,.006f,0xf2d7a0,m,out,light);
    }
    private void renderDerby(AttractionEntity game,float delta,MatrixStack m,VertexConsumerProvider out,int light) {
        double tick=game.elapsed(delta);
        for (int i=0;i<4;i++) {
            double progress=game.ratProgress(i,delta);
            m.push();
            m.translate(-1.13+2.083*progress,.9415,LANES[i]);
            if (progress>0 && progress<1) m.multiply(RotationAxis.POSITIVE_Z.rotation((float)(.025*Math.sin(tick*1.2+i))));
            SlotMachineRenderer.renderPart(derby.part("rat"+i),0,m,out.getBuffer(DERBY),light);
            m.pop();
        }
        m.push();
        m.translate(1.12,.943,.70);
        SlotMachineRenderer.renderPart(derby.part("button_base"),0,m,out.getBuffer(DERBY),light);
        m.translate(0,game.moving() && tick<9 ? .006 : .020,0);
        SlotMachineRenderer.renderPart(derby.part("button_cap"),0,m,out.getBuffer(DERBY),light);
        m.pop();
        m.push();
        m.translate(1.39,1.018,.69);
        if (game.moving() && tick<12) m.multiply(RotationAxis.POSITIVE_Z.rotation((float)(-.75*Math.sin(Math.PI*tick/12))));
        SlotMachineRenderer.renderPart(derby.part("lever"),0,m,out.getBuffer(DERBY),light);
        m.pop();
        if (game.lastLane()>=0) label("ПОБЕДИЛА  №"+(game.lastLane()+1),.3,.961,.699,.70f,.007f,0xffe0a2,m,out,light);
        SlotMachineRenderer.renderPart(derby.part("glass"),0,m,out.getBuffer(GLASS),light);
    }
    private static void chip(MatrixStack m,VertexConsumerProvider out,float x,float y,float z,float w,int color,int n,int light) {
        var b=out.getBuffer(CHIP);
        for (int j=0;j<n;j++)
            CardTableRenderer.top(b,m,x,y+j*.004f,z,w,w,0,0,1,1,color,light);
    }
    private void label(String text,double x,double y,double z,float width,float size,int color,MatrixStack m,VertexConsumerProvider out,int light) {
        m.push();
        m.translate(x,y,z);
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
        float scale=Math.min(size,width/Math.max(1,font.getWidth(text)));
        m.scale(scale,scale,scale);
        font.draw(text,-font.getWidth(text)/2f,-4,0xff000000|color,false,m.peek().getPositionMatrix(),out,TextRenderer.TextLayerType.NORMAL,0,light);
        m.pop();
    }
    @Override public boolean rendersOutsideBoundingBox(AttractionEntity game) { return true; }
    @Override public int getRenderDistance() { return 28; }
}
