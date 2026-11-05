package dev.dreamwalker.bloodbornedw.editor;

import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureBlock;
import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureCatalog;
import dev.dreamwalker.bloodbornedw.composite.NativeGridBlock;
import net.minecraft.registry.Registries;
import com.google.gson.JsonParser;
import java.util.Map;
import java.util.HashMap;
import dev.dreamwalker.bloodbornedw.composite.NativeGridCatalog;
import dev.dreamwalker.bloodbornedw.composite.NativePhysicsBlock;
import dev.dreamwalker.bloodbornedw.composite.NativeVisuals;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

/** Finite, resource-generated material decisions; never loads a chunk to inspect it. */
public final class StonePaletteSupport {
    private static final byte[] NATIVE = load("/bloodborne_dw/editor2-native-flags.bin");
    private static final byte[] FALLBACK = load("/bloodborne_dw/editor2-fallback-flags.bin");
    private static final byte[] WALL = load("/bloodborne_dw/editor2-wall-flags.bin");
    private static final Map<String,byte[]> POSES = loadPoses();
    private static final Map<String,byte[]> SLABS = loadSlabs();
    private StonePaletteSupport() {}
    /** Read the small immutable policy during mod startup, before the first brush click. */
    public static void initialize() {}
    private static byte[] load(String name) {
        try (var stream=StonePaletteSupport.class.getResourceAsStream(name)) {
            return stream==null ? new byte[0] : stream.readAllBytes();
        } catch (java.io.IOException e) { throw new IllegalStateException("Cannot load editor material policy",e); }
    }
    private static Map<String,byte[]> loadPoses() {
        try (var stream=StonePaletteSupport.class.getResourceAsStream("/bloodborne_dw/editor2-pose-flags.json")) {
            if(stream==null)return Map.of();
            var doc=JsonParser.parseReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            if(doc.get("schema").getAsInt()!=1)throw new IllegalStateException("Unknown editor pose policy");
            Map<String,byte[]> result=new HashMap<>();
            for(var entry:doc.getAsJsonObject("poses").entrySet()) {
                var rows=entry.getValue().getAsJsonArray();byte[] values=new byte[rows.size()*2];
                for(int i=0;i<rows.size();i++){var pair=rows.get(i).getAsJsonArray();values[i*2]=(byte)pair.get(0).getAsInt();values[i*2+1]=(byte)pair.get(1).getAsInt();}
                result.put(entry.getKey(),values);
            }
            return Map.copyOf(result);
        } catch(Exception e){throw new IllegalStateException("Cannot load editor pose policy",e);}
    }
    private static int poseFlags(BlockState state) {
        if(!(state.getBlock() instanceof CompositeRootBlock))return 0;
        byte[] rows=POSES.get(Registries.BLOCK.getId(state.getBlock()).toString());
        int variant=dev.dreamwalker.bloodbornedw.composite.CompositeSpec.poseVariant(state);
        return rows==null||variant<0||variant*2+1>=rows.length?0:rows[variant*2+(state.get(CompositeRootBlock.OPEN)?1:0)]&255;
    }
    private static Map<String,byte[]> loadSlabs() {
        try(var stream=StonePaletteSupport.class.getResourceAsStream("/bloodborne_dw/editor2-slab-flags.json")) {
            if(stream==null)return Map.of();
            var doc=JsonParser.parseReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            if(doc.get("schema").getAsInt()!=1)throw new IllegalStateException("Unknown slab policy");
            Map<String,byte[]> result=new HashMap<>();
            for(var entry:doc.getAsJsonObject("slabs").entrySet()) {
                var values=entry.getValue().getAsJsonArray();if(values.size()!=3)throw new IllegalStateException("Invalid slab policy");
                result.put(entry.getKey(),new byte[]{(byte)values.get(0).getAsInt(),(byte)values.get(1).getAsInt(),(byte)values.get(2).getAsInt()});
            }
            return Map.copyOf(result);
        }catch(Exception e){throw new IllegalStateException("Cannot load slab policy",e);}
    }
    private static int slabFlags(BlockState state) {
        byte[] values=SLABS.get(Registries.BLOCK.getId(state.getBlock()).toString());
        var type=state.get(net.minecraft.block.SlabBlock.TYPE);
        int i=switch(type){case BOTTOM->0;case TOP->1;case DOUBLE->2;};
        int result=values==null?0:values[i]&255;
        if(dev.dreamwalker.bloodbornedw.composite.MaterialSlabs.hasOverlay(state)) {
            int recipe=dev.dreamwalker.bloodbornedw.composite.MaterialSlabs.recipeIndex(state);
            if(recipe>=0&&recipe<StaticArchitectureCatalog.recipes().size())
                for(var fragment:StaticArchitectureCatalog.recipes().get(recipe).fragments())result|=poseFlags(fragment.pose().state());
        }
        return result;
    }
    private static int wallFlags(BlockState input) {
        var state=dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallBlock.canonicalForm(input);
        int profile=state.get(dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallBlock.PROFILE)==dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallBlock.Profile.ALT?1:0;
        int diagonal=state.get(dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallBlock.ROTATION)&1;
        int base=(profile*2+diagonal)*10;if(WALL.length<base+10)return 0;
        int result=state.get(dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallBlock.POST)?WALL[base]&255:0;
        for(var direction:net.minecraft.util.math.Direction.Type.HORIZONTAL){
            var shape=state.get(dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallBlock.property(direction));
            if(shape==net.minecraft.block.enums.WallShape.LOW)result|=WALL[base+1]&255;
            else if(shape==net.minecraft.block.enums.WallShape.TALL)result|=WALL[base+2+dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallBlock.material(state)]&255;
        }
        return result;
    }
    private static int flags(World world,BlockPos pos) {
        if (!(world instanceof ServerWorld server)) return 0;
        var chunk=server.getChunkManager().getChunk(pos.getX()>>4,pos.getZ()>>4,ChunkStatus.FULL,false);
        if (!(chunk instanceof WorldChunk)) return 0;
        BlockState state=chunk.getBlockState(pos); int mesh;
        if (state.getBlock() instanceof dev.dreamwalker.bloodbornedw.composite.MaterialSlabs.Slab) return slabFlags(state);
        if (state.getBlock() instanceof dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallBlock) return wallFlags(state);
        if (state.getBlock() instanceof NativeGridBlock) mesh=NativeGridCatalog.cell(state).mesh();
        else if (state.getBlock() instanceof NativePhysicsBlock) {
            int index=NativeVisuals.index(world,pos);
            if(index<0||index>=NativeGridCatalog.cells().size())return 0;
            mesh=NativeGridCatalog.cell(index).mesh();
        } else if (state.getBlock() instanceof dev.dreamwalker.bloodbornedw.composite.SourceFallbackCarrierV1271) {
            int index=state.get(dev.dreamwalker.bloodbornedw.composite.SourceFallbackCarrierV1271.SOURCE)+128*state.get(dev.dreamwalker.bloodbornedw.composite.SourceFallbackCarrierV1271.SOURCE_BANK); return index>=0&&index<FALLBACK.length?FALLBACK[index]&255:0;
        } else if (state.getBlock() instanceof StaticArchitectureBlock) {
            int result=0; for(var fragment:StaticArchitectureCatalog.recipe(state).fragments())result|=poseFlags(fragment.pose().state()); return result;
        } else return poseFlags(state);
        return mesh>=0&&mesh<NATIVE.length ? NATIVE[mesh]&255 : 0;
    }
    /** Resolve an unambiguous loaded logical owner; never load a root chunk. */
    public static BlockPos paintTarget(World world,BlockPos pos) {
        if(!(world instanceof ServerWorld server))return null;
        var chunk=server.getChunkManager().getChunk(pos.getX()>>4,pos.getZ()>>4,ChunkStatus.FULL,false);
        if(!(chunk instanceof WorldChunk))return null;
        BlockState state=chunk.getBlockState(pos);
        if (state.getBlock() instanceof StaticArchitectureBlock
                && StaticArchitectureCatalog.recipe(state).fragments().size() == 1) {
            BlockPos root = RgbProfiles.staticPaintRoot(server, pos, state);
            return root != null && (flags(world, root) & 1) != 0 ? root.toImmutable() : null;
        }
        if(!(state.getBlock() instanceof dev.dreamwalker.bloodbornedw.composite.CompositeCellBlock))return (flags(world,pos)&1)!=0?pos.toImmutable():null;
        dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner owner=null;
        for(var entry:dev.dreamwalker.bloodbornedw.composite.CompositeRuntime.contributions(world,pos)){
            if(owner==null)owner=entry.owner();else if(!owner.equals(entry.owner()))return null;
        }
        if(owner==null)return null;
        BlockPos root=dev.dreamwalker.bloodbornedw.composite.CompositeData.pos(owner.root());
        var rootChunk=server.getChunkManager().getChunk(root.getX()>>4,root.getZ()>>4,ChunkStatus.FULL,false);
        if(!(rootChunk instanceof WorldChunk ready))return null;
        if(!(ready.getBlockEntity(root) instanceof dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity entity)||!owner.equals(entity.resident()))return null;
        return (flags(world,root)&1)!=0?root:null;
    }
    public static boolean paintable(World world,BlockPos pos) { return paintTarget(world,pos)!=null; }
    public static int automaticRoles(World world,BlockPos pos) { BlockPos root=paintTarget(world,pos);return root==null?0:flags(world,root)&30; }
}
