package dev.dreamwalker.bloodbornedw.visual;

import java.util.*;
import dev.dreamwalker.bloodbornedw.composite.CornerQuadParentScope;
import dev.dreamwalker.bloodbornedw.editor.RgbProfiles;
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
    private static final class QuadCaches {
        final Map<BakedQuad, BakedQuad[]> regional = new IdentityHashMap<>();
        final Map<BakedQuad, BakedQuad[]> manual = new LinkedHashMap<>(256, .75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<BakedQuad, BakedQuad[]> entry) {
                return size() > 8192;
            }
        };
    }
    private static volatile QuadCaches caches = new QuadCaches();
    static synchronized void reload() {
        config = null;
        ManualStonePalette.reload();
        // In-flight work may finish against the previous generation, which is no longer retained here.
        caches = new QuadCaches();
        CityFacadesClient.clearCache();
    }
    static FacadeConfig config() { FacadeConfig c=config; if(c==null) synchronized(FacadeMaterials.class) { if(config==null)config=FacadeConfig.load(); c=config; } return c; }
    static int role(BlockPos pos) {
        if(!CityVisualClient.isAlt())return 0;
        var world=MinecraftClient.getInstance().world; if(world==null)return 0;
        FacadeConfig c=config(); return world.getRegistryKey().getValue().equals(c.dimension)?c.role(pos):0;
    }
    private static SpriteAtlasTexture atlas() { return (SpriteAtlasTexture) MinecraftClient.getInstance().getTextureManager().getTexture(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE); }
    static BakedQuad quad(BakedQuad source,int role) {
        return quad(source,role,false);
    }
    static BakedQuad quad(BakedQuad source,int role,boolean manual) {
        if(role==0)return source;
        QuadCaches generation = caches;
        Sprite sprite=source.getSprite(); Identifier id=sprite.getContents().getId(); if(manual)return manualQuad(generation,source,role,sprite,id);
        FacadeConfig c=config();
        if(role==4&&!c.brickWindow.containsKey(id))return source;
        // A facade correction must never recolour horizontal pavements or
        // floors even when they happen to share a sprite inside the house box.
        if(!manual&&role==4&&(source.getFace()==Direction.UP||source.getFace()==Direction.DOWN))return source;
        if(!c.bricks.contains(id)&&!(role==1&&c.rose.containsKey(id))&&!(role==4&&c.brickWindow.containsKey(id)))return source;
        // roof_1 is shared with the moving wooden shutter and glass islands.
        // Regional wall colours must affect only its upper masonry half.
        if(id.getPath().endsWith("/roof_1")&&!wallHalf(source.getVertexData(),sprite))return source;
        if(id.getPath().endsWith("/spirelamp_0010")&&(source.getFace()==Direction.UP||source.getFace()==Direction.DOWN))return source;
        Map<BakedQuad, BakedQuad[]> cache = generation.regional;
        BakedQuad cached = cached(cache, source, role);
        if (cached != null) return cached;
        Sprite target=role==4?atlas().getSprite(c.brickWindow.get(id)):role==1&&c.rose.containsKey(id)?atlas().getSprite(c.rose.get(id)):
            role>1&&c.accentBase.containsKey(id)?atlas().getSprite(c.accentBase.get(id)):sprite;
        int[] data=source.getVertexData().clone();
        for(int i=0;i<4;i++) { int p=i*8; if(target!=sprite) { data[p+4]=Float.floatToRawIntBits(map(Float.intBitsToFloat(data[p+4]),sprite.getMinU(),sprite.getMaxU(),target.getMinU(),target.getMaxU())); data[p+5]=Float.floatToRawIntBits(map(Float.intBitsToFloat(data[p+5]),sprite.getMinV(),sprite.getMaxV(),target.getMinV(),target.getMaxV())); } if(role>1&&role<4) { int color=data[p+3]; float[] m=c.multipliers[role]; data[p+3]=(color&0xff000000)|((int)((color&255)*m[0]))|((int)(((color>>>8)&255)*m[1])<<8)|((int)(((color>>>16)&255)*m[2])<<16); } }
        return cache(cache, source, role, new BakedQuad(data,source.getColorIndex(),source.getFace(),target,source.hasShade()));
    }
    static boolean transform(MutableQuadView quad,int role) {
        return transform(quad,role,false);
    }
    static boolean transform(MutableQuadView quad,int role,boolean manual) {
        Sprite sprite=SpriteFinder.get(atlas()).find(quad); Identifier id=sprite.getContents().getId();
        if(role==0)return true;
        if(manual) { Identifier targetId=ManualStonePalette.target(id,role); if(targetId!=null) { Sprite target=atlas().getSprite(targetId); for(int i=0;i<4;i++)quad.uv(i,map(quad.u(i),sprite.getMinU(),sprite.getMaxU(),target.getMinU(),target.getMaxU()),map(quad.v(i),sprite.getMinV(),sprite.getMaxV(),target.getMinV(),target.getMaxV())); } return true; }
        FacadeConfig c=config(); if(!c.bricks.contains(id)&&!(role==1&&c.rose.containsKey(id))&&!(role==4&&c.brickWindow.containsKey(id)))return true;
        if(role==4&&!c.brickWindow.containsKey(id))return true;
        if(!manual&&role==4&&(quad.nominalFace()==Direction.UP||quad.nominalFace()==Direction.DOWN||horizontal(quad)))return true;
        if(id.getPath().endsWith("/roof_1")) { float v=0; for(int i=0;i<4;i++)v+=quad.v(i); if(v/4>sprite.getMinV()+(sprite.getMaxV()-sprite.getMinV())*.5F)return true; }
        if(id.getPath().endsWith("/spirelamp_0010")&&(quad.nominalFace()==Direction.UP||quad.nominalFace()==Direction.DOWN))return true;
        Identifier targetId=role==4?c.brickWindow.get(id):role==1?c.rose.get(id):c.accentBase.get(id);
        if(targetId!=null) { Sprite target=atlas().getSprite(targetId); for(int i=0;i<4;i++)quad.uv(i,map(quad.u(i),sprite.getMinU(),sprite.getMaxU(),target.getMinU(),target.getMaxU()),map(quad.v(i),sprite.getMinV(),sprite.getMaxV(),target.getMinV(),target.getMaxV())); }
        if(role>1&&role<4) { float[] m=c.multipliers[role]; for(int i=0;i<4;i++){ int color=quad.color(i); quad.color(i,(color&0xff000000)|((int)(((color>>>16)&255)*m[0])<<16)|((int)(((color>>>8)&255)*m[1])<<8)|(int)((color&255)*m[2])); } }
        return true;
    }
    static BakedQuad rgbQuad(BakedQuad source,int rgb,RgbProfiles.Profile profile) {
        Sprite from = source.getSprite();
        Sprite to = neutral(from,profile);
        if (to == null) return source;
        int[] data = source.getVertexData().clone();
        for (int i = 0; i < 4; i++) {
            int offset = i*8;
            data[offset+3] = tint(data[offset+3],rgb,true);
            data[offset+4] = Float.floatToRawIntBits(map(Float.intBitsToFloat(data[offset+4]),from.getMinU(),from.getMaxU(),to.getMinU(),to.getMaxU()));
            data[offset+5] = Float.floatToRawIntBits(map(Float.intBitsToFloat(data[offset+5]),from.getMinV(),from.getMaxV(),to.getMinV(),to.getMaxV()));
        }
        BakedQuad result = new BakedQuad(data,source.getColorIndex(),source.getFace(),to,source.hasShade());
        if (CornerQuadParentScope.parentFor(source) != null) CornerQuadParentScope.alias(source,result);
        return result;
    }

    static boolean transformRgb(MutableQuadView quad,int rgb,RgbProfiles.Profile profile) {
        Sprite from = SpriteFinder.get(atlas()).find(quad);
        Sprite to = neutral(from,profile);
        if (to == null) return true;
        for (int i = 0; i < 4; i++) {
            quad.uv(i,map(quad.u(i),from.getMinU(),from.getMaxU(),to.getMinU(),to.getMaxU()),
                    map(quad.v(i),from.getMinV(),from.getMaxV(),to.getMinV(),to.getMaxV()));
            quad.color(i,tint(quad.color(i),rgb,false));
        }
        return true;
    }

    private static Sprite neutral(Sprite from,RgbProfiles.Profile profile) {
        Identifier id = profile.neutral(from.getContents().getId());
        if (id == null) return null;
        Sprite sprite = atlas().getSprite(id);
        return id.equals(sprite.getContents().getId()) ? sprite : null;
    }

    /** BakedQuad serializes ABGR. FRAPI exposes vertex colors as ARGB. */
    static int tint(int original,int rgb,boolean abgr) {
        int redShift = abgr ? 0 : 16, blueShift = 16-redShift;
        int red = ((original >>> redShift) & 255) * ((rgb >>> 16) & 255) / 255;
        int green = ((original >>> 8) & 255) * ((rgb >>> 8) & 255) / 255;
        int blue = ((original >>> blueShift) & 255) * (rgb & 255) / 255;
        return (original & 0xff000000) | (red << redShift) | (green << 8) | (blue << blueShift);
    }
    private static BakedQuad manualQuad(QuadCaches generation,BakedQuad source,int role,Sprite sprite,Identifier id) {
        Identifier targetId=ManualStonePalette.target(id,role); if(targetId==null)return source;
        boolean transientQuad=dev.dreamwalker.bloodbornedw.composite.CornerQuadParentScope.parentFor(source)!=null;
        if (!transientQuad) {
            BakedQuad cached = cached(generation.manual, source, role);
            if (cached != null) return cached;
        }
        Sprite target=atlas().getSprite(targetId); int[] data=source.getVertexData().clone();
        for(int i=0;i<4;i++){int p=i*8;data[p+4]=Float.floatToRawIntBits(map(Float.intBitsToFloat(data[p+4]),sprite.getMinU(),sprite.getMaxU(),target.getMinU(),target.getMaxU()));data[p+5]=Float.floatToRawIntBits(map(Float.intBitsToFloat(data[p+5]),sprite.getMinV(),sprite.getMaxV(),target.getMinV(),target.getMaxV()));}
        BakedQuad result=new BakedQuad(data,source.getColorIndex(),source.getFace(),target,source.hasShade());
        if(transientQuad) {
            dev.dreamwalker.bloodbornedw.composite.CornerQuadParentScope.alias(source,result);
            return result;
        }
        return cache(generation.manual, source, role, result);
    }
    private static BakedQuad cached(Map<BakedQuad, BakedQuad[]> cache, BakedQuad source, int role) {
        synchronized (cache) {
            BakedQuad[] variants = cache.get(source);
            return variants == null ? null : variants[role];
        }
    }
    private static BakedQuad cache(Map<BakedQuad, BakedQuad[]> cache, BakedQuad source, int role, BakedQuad result) {
        synchronized (cache) {
            BakedQuad[] variants = cache.computeIfAbsent(source, ignored -> new BakedQuad[5]);
            if (variants[role] == null) variants[role] = result;
            return variants[role];
        }
    }
    private static float map(float v,float a,float b,float c,float d) { return c+(v-a)*(d-c)/(b-a); }
    private static boolean wallHalf(int[] vertices,Sprite sprite) { float v=0;for(int i=0;i<4;i++)v+=Float.intBitsToFloat(vertices[i*8+5]);return v/4<=sprite.getMinV()+(sprite.getMaxV()-sprite.getMinV())*.5F; }
    private static boolean horizontal(MutableQuadView quad) {float y=quad.y(0);for(int i=1;i<4;i++)if(Math.abs(quad.y(i)-y)>.00001F)return false;return true;}
}
