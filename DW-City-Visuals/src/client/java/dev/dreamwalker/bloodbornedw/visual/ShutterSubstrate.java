package dev.dreamwalker.bloodbornedw.visual;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

/** Finite source-model routing evaluated while chunk geometry is emitted.
 * No world/position cache or frame-loop lookup is introduced. */
final class ShutterSubstrate {
    static final int BRICK_ROLE=4;
    private static volatile Map<BakedModel,Boolean> shutters=new IdentityHashMap<>();
    private record Context(java.util.Set<Identifier> models,java.util.Set<Identifier> sources){}
    private static volatile Context context;
    static void reload(){shutters=new IdentityHashMap<>();}
    static boolean source(BlockState state){
        var id=Registries.BLOCK.getId(state.getBlock());
        return sourceId(id);
    }
    private static Context context(){
        Context result=context;
        if(result==null)synchronized(ShutterSubstrate.class){
            result=context;
            if(result==null)try(var in=ShutterSubstrate.class.getResourceAsStream("/assets/bloodborne_dw/visuals/contextual-masonry.json")){
                if(in==null)throw new IllegalStateException("Missing contextual masonry metadata");
                var data=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                java.util.Set<Identifier> models=new java.util.HashSet<>(),sources=new java.util.HashSet<>();
                for(var entry:data.getAsJsonArray("models"))models.add(new Identifier(entry.getAsString()));
                for(var entry:data.getAsJsonArray("sources"))sources.add(new Identifier(entry.getAsString()));
                result=new Context(java.util.Set.copyOf(models),java.util.Set.copyOf(sources));context=result;
            }catch(java.io.IOException ex){throw new IllegalStateException("Cannot load contextual masonry metadata",ex);}
        }
        return result;
    }
    static boolean sourceId(Identifier id){return context().sources.contains(id)||id.getPath().contains("shutter");}
    static boolean modelId(Identifier id){return context().models.contains(id)||id.getPath().contains("interactions/shutter");}
    static boolean model(BakedModel model,BlockState state){
        Map<BakedModel,Boolean> cache=shutters;
        synchronized (cache) {
            Boolean cached=cache.get(model);
            if(cached!=null)return cached;
        }
        boolean result=inspect(model,state);
        synchronized (cache) {
            Boolean cached=cache.get(model);
            if(cached!=null)return cached;
            cache.put(model,result);
            return result;
        }
    }
    private static boolean inspect(BakedModel model,BlockState state){
        for(int side=-1;side<6;side++)for(var quad:model.getQuads(state,side<0?null:Direction.values()[side],Random.create(42)))
            if(quad.getSprite().getContents().getId().getPath().endsWith("/roof_1")||quad.getSprite().getContents().getId().getPath().endsWith("/window_surround")||quad.getSprite().getContents().getId().getPath().endsWith("/spirelamp_0010"))return true;
        return false;
    }
    static int role(BlockRenderView world,BlockPos pos){
        // Red masonry is an explicitly reviewed house facade, never inferred
        // from nearby generic brick sprites shared by streets and other houses.
        return FacadeMaterials.role(pos);
    }
}
