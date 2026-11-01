package dev.dreamwalker.bloodbornedw.composite;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;

/** Keep shutter frames cutout and only their glass blended in cached owner/item rendering. */
public final class ShutterGlassLayers {
    private static final Identifier GLASS = new Identifier("bloodborne_dw", "block/interactions/shutter_glass");
    private static Sprite sprite;
    private ShutterGlassLayers() {}
    public static void clear() { sprite = null; }
    public static VertexConsumer consumer(VertexConsumerProvider provider, RenderLayer fallback, CompositeRootBlock block) {
        if (!block.spec.id.getPath().equals("source_shutter_window")) return provider.getBuffer(fallback);
        if (sprite == null) sprite = MinecraftClient.getInstance().getSpriteAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).apply(GLASS);
        return new Routed(provider.getBuffer(RenderLayer.getCutout()), provider.getBuffer(RenderLayer.getTranslucent()), sprite);
    }
    private static final class Routed implements VertexConsumer {
        private final VertexConsumer frame, glass;
        private final float minU, maxU, minV, maxV;
        private double x,y,z; private float u,v,nx,ny,nz;
        private int r=255,g=255,b=255,a=255,overlay,light; private boolean fixed;
        Routed(VertexConsumer frame, VertexConsumer glass, Sprite sprite) {
            this.frame=frame;this.glass=glass;minU=sprite.getMinU();maxU=sprite.getMaxU();minV=sprite.getMinV();maxV=sprite.getMaxV();
        }
        private VertexConsumer select(float U,float V) { return U>=minU&&U<=maxU&&V>=minV&&V<=maxV?glass:frame; }
        @Override public void vertex(float X,float Y,float Z,float R,float G,float B,float A,float U,float V,int O,int L,float NX,float NY,float NZ) {
            select(U,V).vertex(X,Y,Z,R,G,B,A,U,V,O,L,NX,NY,NZ);
        }
        @Override public VertexConsumer vertex(double X,double Y,double Z){x=X;y=Y;z=Z;return this;}
        @Override public VertexConsumer color(int R,int G,int B,int A){if(!fixed){r=R;g=G;b=B;a=A;}return this;}
        @Override public VertexConsumer texture(float U,float V){u=U;v=V;return this;}
        @Override public VertexConsumer overlay(int U,int V){overlay=U|V<<16;return this;}
        @Override public VertexConsumer light(int U,int V){light=U|V<<16;return this;}
        @Override public VertexConsumer normal(float X,float Y,float Z){nx=X;ny=Y;nz=Z;return this;}
        @Override public void next(){select(u,v).vertex((float)x,(float)y,(float)z,r/255f,g/255f,b/255f,a/255f,u,v,overlay,light,nx,ny,nz);}
        @Override public void fixedColor(int R,int G,int B,int A){r=R;g=G;b=B;a=A;fixed=true;}
        @Override public void unfixColor(){fixed=false;}
    }
}
