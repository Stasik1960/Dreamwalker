package dev.dreamwalker.bloodbornedw.visual;

import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureBlock;
import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureCatalog;
import dev.dreamwalker.bloodbornedw.composite.NativeCellBlock;
import dev.dreamwalker.bloodbornedw.composite.NativePhysicsBlock;
import dev.dreamwalker.bloodbornedw.composite.NativeVisuals;
import dev.dreamwalker.bloodbornedw.composite.NativeGridBridge;
import dev.dreamwalker.bloodbornedw.composite.NativeGridCatalog;
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

/** Material context evaluated only while chunk/source geometry is emitted.
 * No world/position cache or frame-loop lookup is introduced. */
final class ShutterSubstrate {
    static final int BRICK_ROLE=4;
    private static final Map<BakedModel,Boolean> SHUTTERS=new IdentityHashMap<>();
    private static volatile byte[] nativeTypes;
    private record Context(java.util.Set<Identifier> models,java.util.Set<Identifier> sources){}
    private static volatile Context context;
    static synchronized void reload(){SHUTTERS.clear();}
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
    private static int nativeMaterial(int mesh){
        byte[] values=nativeTypes;
        if(values==null)synchronized(ShutterSubstrate.class){
            values=nativeTypes;
            if(values==null)try(var in=ShutterSubstrate.class.getResourceAsStream("/assets/bloodborne_dw/visuals/native-substrates.bin")){
                if(in==null)throw new IllegalStateException("Missing native substrate metadata");
                byte[] data=in.readAllBytes();
                if(data.length<8||data[0]!='N'||data[1]!='S'||data[2]!='S'||data[3]!='1')throw new IllegalStateException("Invalid native substrate metadata");
                int length=java.nio.ByteBuffer.wrap(data,4,4).getInt();
                if(length!=data.length-8)throw new IllegalStateException("Native substrate length mismatch");
                values=java.util.Arrays.copyOfRange(data,8,data.length);nativeTypes=values;
            }catch(java.io.IOException ex){throw new IllegalStateException("Cannot load native substrate metadata",ex);}
        }
        int kind=mesh>=0&&mesh<values.length?values[mesh]:0;
        return kind==2?BRICK_ROLE:kind==1?-1:0;
    }
    static synchronized boolean model(BakedModel model,BlockState state){
        return SHUTTERS.computeIfAbsent(model,ignored->{
            for(int side=-1;side<6;side++)for(var quad:model.getQuads(state,side<0?null:Direction.values()[side],Random.create(42)))
                if(quad.getSprite().getContents().getId().getPath().endsWith("/roof_1")||quad.getSprite().getContents().getId().getPath().endsWith("/window_surround")||quad.getSprite().getContents().getId().getPath().endsWith("/spirelamp_0010"))return true;
            return false;
        });
    }
    private static int sourceMaterial(BlockState state){
        var id=Registries.BLOCK.getId(state.getBlock());
        if(!id.getNamespace().equals("bloodborne_dw"))return 0;
        String path=id.getPath();
        if(path.equals("source_bricks")||path.equals("source_assembly_brick_wall"))return BRICK_ROLE;
        if(path.equals("source_stone_bricks")||path.contains("stone_brick_wall"))return -1;
        return 0;
    }
    private static int material(BlockRenderView world,BlockPos pos){
        BlockState state=world.getBlockState(pos);
        // NativePhysicsBlock.cell(state) is a shared PHYSICAL representative.
        // The chunk attachment selects its actual material-bearing visual.
        if(state.getBlock() instanceof NativePhysicsBlock){
            int visual=NativeVisuals.forView(world,pos,state);
            return nativeMaterial(NativeGridCatalog.cell(visual).mesh());
        }
        if(state.getBlock() instanceof NativeCellBlock cell)return nativeMaterial(cell.cell(state).mesh());
        if(state.getBlock() instanceof StaticArchitectureBlock){
            int result=0;
            for(var fragment:StaticArchitectureCatalog.recipe(state).fragments()){
                int type=sourceMaterial(fragment.pose().state());
                if(type==-1)return -1;
                if(type==BRICK_ROLE)result=BRICK_ROLE;
            }
            if(result!=0)return result;
        }
        int source=sourceMaterial(state);
        if(source!=0)return source;
        int background=NativeGridBridge.base(world,pos);
        return background>=0?nativeMaterial(NativeGridCatalog.cell(background).mesh()):0;
    }
    static int role(BlockRenderView world,BlockPos pos){
        int explicit=FacadeMaterials.role(pos);
        if(explicit!=0||!CityVisualClient.isAlt())return explicit;
        // The original shutter surround is stone. Only select chestnut when
        // the nearest actual structural wall is a generic brick family.
        // The bounded four-block Manhattan neighbourhood is visited only for
        // the few shutter-bearing source/native models during mesh compilation.
        BlockPos.Mutable sample=new BlockPos.Mutable();
        for(int radius=1;radius<=4;radius++){
            int stone=0,brick=0;
            for(int dy=-1;dy<=1;dy++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
                if(Math.abs(dx)+Math.abs(dy)+Math.abs(dz)!=radius)continue;
                sample.set(pos.getX()+dx,pos.getY()+dy,pos.getZ()+dz);
                int type=material(world,sample);
                if(type==-1)stone++;else if(type==BRICK_ROLE)brick++;
            }
            if(stone+brick>0)return brick>stone?BRICK_ROLE:0;
        }
        return 0;
    }
}
