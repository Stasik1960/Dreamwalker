package dev.dreamwalker.bloodbornedw.visual;

import java.util.*;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.model.SpriteFinder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

final class FacadeMaterials {
    private static volatile FacadeConfig config;
    private static final Map<BakedQuad,BakedQuad[]> CACHE = new IdentityHashMap<>();
    static synchronized void reload() { config=null; CACHE.clear(); CityFacadesClient.clearCache(); }
    static FacadeConfig config() { FacadeConfig c=config; if(c==null) synchronized(FacadeMaterials.class) { if(config==null)config=FacadeConfig.load(); c=config; } return c; }
    static int role(BlockPos pos) {
        if(!CityVisualClient.isAlt())return 0;
        var world=MinecraftClient.getInstance().world; if(world==null)return 0;
        FacadeConfig c=config(); return world.getRegistryKey().getValue().equals(c.dimension)?c.role(pos):0;
    }
    private static SpriteAtlasTexture atlas() { return (SpriteAtlasTexture) MinecraftClient.getInstance().getTextureManager().getTexture(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE); }
    static synchronized BakedQuad quad(BakedQuad source,int role) {
        if(role==0)return source;
        Sprite sprite=source.getSprite(); Identifier id=sprite.getContents().getId();
        FacadeConfig c=config();
        if(role==4&&!c.brickWindow.containsKey(id))return source;
        // A facade correction must never recolour horizontal pavements or
        // floors even when they happen to share a sprite inside the house box.
        if(role==4&&(source.getFace()==Direction.UP||source.getFace()==Direction.DOWN))return source;
        if(!c.bricks.contains(id)&&!(role==1&&c.rose.containsKey(id))&&!(role==4&&c.brickWindow.containsKey(id)))return source;
        // roof_1 is shared with the moving wooden shutter and glass islands.
        // Regional wall colours must affect only its upper masonry half.
        if(id.getPath().endsWith("/roof_1")&&!wallHalf(source.getVertexData(),sprite))return source;
        if(id.getPath().endsWith("/spirelamp_0010")&&(source.getFace()==Direction.UP||source.getFace()==Direction.DOWN))return source;
        BakedQuad[] variants=CACHE.computeIfAbsent(source,k->new BakedQuad[5]);if(variants[role]!=null)return variants[role];
        int slot=role;
        Sprite target=role==4?atlas().getSprite(c.brickWindow.get(id)):role==1&&c.rose.containsKey(id)?atlas().getSprite(c.rose.get(id)):
            role>1&&c.accentBase.containsKey(id)?atlas().getSprite(c.accentBase.get(id)):sprite;
        int[] data=source.getVertexData().clone();
        for(int i=0;i<4;i++) { int p=i*8; if(target!=sprite) { data[p+4]=Float.floatToRawIntBits(map(Float.intBitsToFloat(data[p+4]),sprite.getMinU(),sprite.getMaxU(),target.getMinU(),target.getMaxU())); data[p+5]=Float.floatToRawIntBits(map(Float.intBitsToFloat(data[p+5]),sprite.getMinV(),sprite.getMaxV(),target.getMinV(),target.getMaxV())); } if(role>1&&role<4) { int color=data[p+3]; float[] m=c.multipliers[role]; data[p+3]=(color&0xff000000)|((int)((color&255)*m[0]))|((int)(((color>>>8)&255)*m[1])<<8)|((int)(((color>>>16)&255)*m[2])<<16); } }
        return variants[slot]=new BakedQuad(data,source.getColorIndex(),source.getFace(),target,source.hasShade());
    }
    static boolean transform(MutableQuadView quad,int role) {
        Sprite sprite=SpriteFinder.get(atlas()).find(quad); Identifier id=sprite.getContents().getId();
        if(role==0)return true; FacadeConfig c=config(); if(!c.bricks.contains(id)&&!(role==1&&c.rose.containsKey(id))&&!(role==4&&c.brickWindow.containsKey(id)))return true;
        if(role==4&&!c.brickWindow.containsKey(id))return true;
        if(role==4&&(quad.nominalFace()==Direction.UP||quad.nominalFace()==Direction.DOWN||horizontal(quad)))return true;
        if(id.getPath().endsWith("/roof_1")) { float v=0; for(int i=0;i<4;i++)v+=quad.v(i); if(v/4>sprite.getMinV()+(sprite.getMaxV()-sprite.getMinV())*.5F)return true; }
        if(id.getPath().endsWith("/spirelamp_0010")&&(quad.nominalFace()==Direction.UP||quad.nominalFace()==Direction.DOWN))return true;
        Identifier targetId=role==4?c.brickWindow.get(id):role==1?c.rose.get(id):c.accentBase.get(id);
        if(targetId!=null) { Sprite target=atlas().getSprite(targetId); for(int i=0;i<4;i++)quad.uv(i,map(quad.u(i),sprite.getMinU(),sprite.getMaxU(),target.getMinU(),target.getMaxU()),map(quad.v(i),sprite.getMinV(),sprite.getMaxV(),target.getMinV(),target.getMaxV())); }
        if(role>1&&role<4) { float[] m=c.multipliers[role]; for(int i=0;i<4;i++){ int color=quad.color(i); quad.color(i,(color&0xff000000)|((int)(((color>>>16)&255)*m[0])<<16)|((int)(((color>>>8)&255)*m[1])<<8)|(int)((color&255)*m[2])); } }
        return true;
    }
    private static float map(float v,float a,float b,float c,float d) { return c+(v-a)*(d-c)/(b-a); }
    private static boolean wallHalf(int[] vertices,Sprite sprite) { float v=0;for(int i=0;i<4;i++)v+=Float.intBitsToFloat(vertices[i*8+5]);return v/4<=sprite.getMinV()+(sprite.getMaxV()-sprite.getMinV())*.5F; }
    private static boolean horizontal(MutableQuadView quad) {float y=quad.y(0);for(int i=1;i<4;i++)if(Math.abs(quad.y(i)-y)>.00001F)return false;return true;}
}
