package dev.dreamwalker.bloodbornedw.visual;

import java.util.*;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.model.SpriteFinder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

final class FacadeMaterials {
    private static volatile FacadeConfig config;
    private static final Map<BakedQuad,BakedQuad[]> CACHE = new IdentityHashMap<>();
    private static final Map<BakedModel,Boolean> GENERAL = new IdentityHashMap<>();
    static synchronized void reload() { config=null; CACHE.clear(); GENERAL.clear(); CityFacadesClient.clearCache(); }
    static FacadeConfig config() { FacadeConfig c=config; if(c==null) synchronized(FacadeMaterials.class) { if(config==null)config=FacadeConfig.load(); c=config; } return c; }
    static int role(BlockPos pos) {
        if(!CityVisualClient.isAlt())return 0;
        var world=MinecraftClient.getInstance().world; if(world==null)return 0;
        FacadeConfig c=config(); return world.getRegistryKey().getValue().equals(c.dimension)?c.role(pos):0;
    }
    private static SpriteAtlasTexture atlas() { return (SpriteAtlasTexture) MinecraftClient.getInstance().getTextureManager().getTexture(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE); }
    private static int material(Sprite sprite) {
        Identifier id=sprite.getContents().getId();
        if(!id.getNamespace().equals("bloodborne_dw"))return 0;
        return switch(id.getPath()) {
            case "block/remaining/source/minecraft/block/addon/spirelamp_0045", "block/remaining/source/minecraft/block/addon/spirelamp_0028",
                 "block/remaining/source/minecraft/block/addon/spirelamp_0010" -> 1;
            default -> 0;
        };
    }
    private static float tone(Sprite sprite,boolean alt) { return material(sprite)==1 ? (alt?.76f:.84f) : 1f; }
    private static int darken(int color,float f) { return (color&0xff000000)|((int)((color&255)*f))|((int)(((color>>>8)&255)*f)<<8)|((int)(((color>>>16)&255)*f)<<16); }
    /** Applied once to native meshes, or cached once for retained model quads. */
    static void correct(int[] data,Sprite sprite,boolean alt) {
        float factor=tone(sprite,alt);
        if(factor==1f)return;
        for(int i=0;i<4;i++)data[i*8+3]=darken(data[i*8+3],factor);
    }
    static synchronized boolean hasGeneral(BakedModel model,BlockState state) {
        if(model instanceof FacadeModel facade)model=facade.delegate();
        Boolean cached=GENERAL.get(model); if(cached!=null)return cached;
        Random random=Random.create(0); boolean found=false;
        try { for(int i=-1;i<6&&!found;i++) for(BakedQuad q:model.getQuads(state,i<0?null:Direction.values()[i],random)) { if(material(q.getSprite())!=0){ found=true; break; } } }
        catch (RuntimeException unsupportedStaticProbe) { found=false; }
        GENERAL.put(model,found); return found;
    }
    static synchronized BakedQuad quad(BakedQuad source,int role,boolean general) {
        boolean alt=CityVisualClient.isAlt(); int slot=role+(alt?4:0)+(general?8:0);
        BakedQuad[] variants=CACHE.computeIfAbsent(source,k->new BakedQuad[16]); if(variants[slot]!=null)return variants[slot];
        Sprite sprite=source.getSprite(); Identifier id=sprite.getContents().getId();
        FacadeConfig c=role==0?null:config(); boolean brick=c!=null&&c.bricks.contains(id);
        boolean correction=general&&tone(sprite,alt)!=1f;
        if(!brick&&!correction)return variants[slot]=source;
        Sprite target=brick&&role==1&&c.rose.containsKey(id)?atlas().getSprite(c.rose.get(id)):sprite;
        int[] data=source.getVertexData().clone(); if(correction)correct(data,sprite,alt);
        for(int i=0;i<4;i++) { int p=i*8; if(brick&&role==1&&target!=sprite) { data[p+4]=Float.floatToRawIntBits(map(Float.intBitsToFloat(data[p+4]),sprite.getMinU(),sprite.getMaxU(),target.getMinU(),target.getMaxU())); data[p+5]=Float.floatToRawIntBits(map(Float.intBitsToFloat(data[p+5]),sprite.getMinV(),sprite.getMaxV(),target.getMinV(),target.getMaxV())); } else if(brick&&role>1) { int color=data[p+3]; float[] m=c.multipliers[role]; data[p+3]=(color&0xff000000)|((int)((color&255)*m[0]))|((int)(((color>>>8)&255)*m[1])<<8)|((int)(((color>>>16)&255)*m[2])<<16); } }
        return variants[slot]=new BakedQuad(data,source.getColorIndex(),source.getFace(),target,source.hasShade());
    }
    static boolean transform(MutableQuadView quad,int role,boolean general) {
        Sprite sprite=SpriteFinder.get(atlas()).find(quad); Identifier id=sprite.getContents().getId(); boolean alt=CityVisualClient.isAlt();
        if(general) { float factor=tone(sprite,alt); if(factor!=1f)for(int i=0;i<4;i++)quad.color(i,darken(quad.color(i),factor)); }
        if(role==0)return true; FacadeConfig c=config(); if(!c.bricks.contains(id))return true;
        if(role==1 && c.rose.containsKey(id)) { Sprite target=atlas().getSprite(c.rose.get(id)); for(int i=0;i<4;i++)quad.uv(i,map(quad.u(i),sprite.getMinU(),sprite.getMaxU(),target.getMinU(),target.getMaxU()),map(quad.v(i),sprite.getMinV(),sprite.getMaxV(),target.getMinV(),target.getMaxV())); }
        else if(role>1) { float[] m=c.multipliers[role]; for(int i=0;i<4;i++){ int color=quad.color(i); quad.color(i,(color&0xff000000)|((int)(((color>>>16)&255)*m[0])<<16)|((int)(((color>>>8)&255)*m[1])<<8)|(int)((color&255)*m[2])); } }
        return true;
    }
    private static float map(float v,float a,float b,float c,float d) { return c+(v-a)*(d-c)/(b-a); }
}
