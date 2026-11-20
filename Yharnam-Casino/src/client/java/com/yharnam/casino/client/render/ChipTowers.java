package com.yharnam.casino.client.render;

import com.yharnam.casino.YharnamCasino;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;

/** Each stack has one textured rim and one face regardless of the number of chips it represents. */
final class ChipTowers {
    private static final RenderLayer LAYER=RenderLayer.getEntityCutout(YharnamCasino.id("textures/entity/cards/towers.png"));
    private static final float[] X=new float[9], Z=new float[9];
    private static final int[] COL={0,1,0,-1,1,-1}, ROW={0,0,1,0,1,1};
    static {
        for(int i=0;i<=8;i++) {
            X[i]=(float)Math.cos((i+.5)*Math.PI/4);
            Z[i]=(float)Math.sin((i+.5)*Math.PI/4);
        }
    }
    static int color(long value) {
        return value>=1000?0xac91c2:value>=100?0x86a9c6:value>=10?0x82ad94:0xd39883;
    }
    static void draw(long value,int chipCount,float x,float y,float z,float diameter,int tint,
                     MatrixStack matrices,VertexConsumerProvider out,int light) {
        if(value<=0 && chipCount<=0) return;
        // Preserve small changes. A nonlinear scale keeps a busy table within its printed betting fields.
        int visible=(int)Math.min(72,Math.max(chipCount,value<=12?value:
                12+Math.ceil(8*Math.log1p((value-12)/20.0))));
        VertexConsumer b=out.getBuffer(LAYER);
        for(int stack=0;visible>0;stack++) {
            int height=Math.min(12,visible);
            prism(b,matrices,x+COL[stack]*diameter,y+.001f,z+ROW[stack]*diameter,diameter,height,tint,light);
            visible-=height;
        }
    }
    private static void prism(VertexConsumer b,MatrixStack m,float x,float y,float z,float d,int layers,int color,int light) {
        float r=d*.51f,top=y+d*.112f*layers;
        for(int i=0;i<8;i++) {
            float xx=x+r*X[i],zz=z+r*Z[i],xn=x+r*X[i+1],zn=z+r*Z[i+1];
            float nx=(X[i]+X[i+1])*.5412f,nz=(Z[i]+Z[i+1])*.5412f;
            float u=(i%2)*.25f+.008f,v=1-layers/16f;
            vertex(b,m,xx,y,zz,u,.998f,nx,nz,color,light);
            vertex(b,m,xx,top,zz,u,v+.002f,nx,nz,color,light);
            vertex(b,m,xn,top,zn,u+.234f,v+.002f,nx,nz,color,light);
            vertex(b,m,xn,y,zn,u+.234f,.998f,nx,nz,color,light);
        }
        CardTableRenderer.top(b,m,x,top+.0001f,z,d,d,.503f,.003f,.997f,.250f,color,light);
    }
    private static void vertex(VertexConsumer b,MatrixStack m,float x,float y,float z,float u,float v,float nx,float nz,int color,int light) {
        b.vertex(m.peek().getPositionMatrix(),x,y,z).color(color>>16,(color>>8)&255,color&255,255)
                .texture(u,v).overlay(OverlayTexture.DEFAULT_UV).light(light)
                .normal(m.peek().getNormalMatrix(),nx,0,nz).next();
    }
    private ChipTowers() {}
}
