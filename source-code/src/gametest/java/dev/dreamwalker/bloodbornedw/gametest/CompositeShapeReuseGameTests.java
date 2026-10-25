package dev.dreamwalker.bloodbornedw.gametest;

import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import java.nio.file.*;
import java.lang.ref.WeakReference;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.*;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
import net.minecraft.world.ChunkSerializer;
import net.minecraft.world.chunk.*;

/** Only changed computed geometry and native owner decoding are exercised.
 * Catalog data, recipe IDs and all mutation decisions remain authoritative. */
public final class CompositeShapeReuseGameTests implements FabricGameTest {
    private record Shared(List<Box> boxes,VoxelShape shape){}
    @GameTest(templateName="bloodborne_dw:window_test",tickLimit=200,batchId="shape_reuse")
    public void everyFiniteShapeKeepsExactOccupiedVolumeAndSharesEqualLists(TestContext t){
        Map<List<Box>,Shared> all=new HashMap<>();Set<List<Box>> physical=new HashSet<>(),selection=new HashSet<>();
        for(var recipe:StaticArchitectureCatalog.recipes()){
            verify(t,all,recipe.collision(),recipe.collisionShape(),"collision recipe "+recipe.index());
            verify(t,all,recipe.selection(),recipe.outlineShape(),"selection recipe "+recipe.index());
            physical.add(recipe.collision());selection.add(recipe.selection());
        }
        t.assertTrue(StaticArchitectureCatalog.recipes().size()==32768&&physical.size()==4667&&selection.size()==16433,"the confirmed finite catalog keeps all recipes and exact authored box lists");
        Box left=new Box(0,0,0,.25,1,1),right=new Box(.75,0,0,1,1,1);
        List<Box> disconnected=List.of(left,left,right,right);
        VoxelShape exact=ImportedPhysicalShapes.exact(disconnected);
        sameVolume(t,original(disconnected),exact,"duplicate elimination preserves a full empty gap between disconnected solids");
        t.assertTrue(!VoxelShapes.matchesAnywhere(exact,VoxelShapes.cuboid(.25,0,0,.75,1,1),BooleanBiFunction.AND),"computed simplification does not fill an empty gap");
        t.assertTrue(disconnected.size()==4,"computed duplicate removal never edits authored boxes");
        dev.dreamwalker.bloodbornedw.DreamwalkerBb.LOG.info("SHAPE_REUSE_VERIFIED recipes={} physical={} selection={} combined={} exact-union comparisons={}",StaticArchitectureCatalog.recipes().size(),physical.size(),selection.size(),all.size(),all.size()+1);
        t.complete();
    }
    private static void verify(TestContext t,Map<List<Box>,Shared> all,List<Box> boxes,VoxelShape shape,String context){
        Shared previous=all.putIfAbsent(boxes,new Shared(boxes,shape));
        if(previous!=null)t.assertTrue(previous.boxes==boxes&&previous.shape==shape,"equal finite boxes and computed shape are shared directly: "+context);
        else sameVolume(t,original(boxes),shape,context);
    }
    private static VoxelShape original(List<Box> boxes){
        VoxelShape shape=VoxelShapes.empty();for(Box box:boxes)shape=VoxelShapes.union(shape,VoxelShapes.cuboid(box.minX(),box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ()));return shape;
    }
    private static void sameVolume(TestContext t,VoxelShape expected,VoxelShape actual,String context){t.assertTrue(!VoxelShapes.matchesAnywhere(expected,actual,BooleanBiFunction.NOT_SAME),"exact occupied volume: "+context);}

    @GameTest(templateName="bloodborne_dw:window_test",tickLimit=80,batchId="shape_reuse")
    public void packedChunkListsShareBeforeReparseButPayloadAndAuthorityRemainIndependent(TestContext t){
        var block=CompositeArchitecture.kindBlock("prototype_wood_window");BlockState state=block.getDefaultState();BlockPos pos=t.getAbsolutePos(new BlockPos(8,4,8));
        Owner owner=new Owner(UUID.randomUUID(),block.spec.id.toString(),CompositeData.cell(pos));
        NbtCompound typed=new NbtCompound();typed.putByte("typed",(byte)1);typed.putIntArray("array",new int[]{1,-7});typed.putDouble("MountY",.125);
        var contribution=new CompositeData.Contribution(owner,CompositeData.data(state,typed),new Footprint(List.of(new Box(0,0,0,.25,1,1)),List.of(new Box(0,0,0,1,1,1))));
        NbtCompound saved=new NbtCompound();saved.put("resident",CompositeData.owner(owner));saved.put("payload",typed.copy());CompositeOwnerNbt.write(saved,CompositeData.contributions(List.of(contribution)));
        byte[] canonical=CompositeData.bytes(saved);var first=new CompositeBlockEntity(pos,state);var second=new CompositeBlockEntity(pos,state);var nested=new CompositeBlockEntity(pos,state);var outside=new CompositeBlockEntity(pos,state);
        try(var scope=CompositeData.openDecodeScope()){
            first.readNbt(saved.copy());second.readNbt(saved.copy());
            t.assertTrue(first.contributions()==second.contributions()&&first.contributions().get(0)==second.contributions().get(0),"actual native BE reads reuse the entire immutable packed list in one chunk scope");
            t.assertTrue(first.resident()!=second.resident()&&first.resident().equals(second.resident()),"resident authority remains separately decoded and exact");
            try(var inner=CompositeData.openDecodeScope()){nested.readNbt(saved.copy());t.assertTrue(first.contributions()!=nested.contributions(),"nested chunk owns an independent temporary envelope pool");}
            var restored=new CompositeBlockEntity(pos,state);restored.readNbt(saved.copy());t.assertTrue(restored.contributions()==first.contributions(),"closing nested scope restores the outer packed list pool");
        }
        outside.readNbt(saved.copy());t.assertTrue(outside.contributions()!=first.contributions()&&outside.contributions().equals(first.contributions()),"finished scope retains no world or process decode index");
        for(var entity:List.of(first,second,nested,outside))t.assertTrue(Arrays.equals(canonical,CompositeData.bytes(entity.createNbt())),"native save preserves canonical owner data and every typed mutable payload tag");
        NbtCompound edited=first.payload();edited.putInt("typed",7);edited.getIntArray("array")[0]=99;first.set(first.resident(),first.contributions(),edited);
        t.assertTrue(second.payload().equals(typed)&&nested.payload().equals(typed)&&outside.payload().equals(typed),"payload NBT stays independent across shared contributions and scopes");
        CompositeData.DecodePool pool=new CompositeData.DecodePool();
        try(pool){
            NbtCompound input=saved.copy();var decoded=pool.savedContributions(input);input.getByteArray(CompositeOwnerNbt.KEY)[0]=0;
            t.assertTrue(pool.savedContributions(saved.copy())==decoded,"temporary byte keys own their contents independently of caller mutation");
            NbtCompound mixed=saved.copy();mixed.put("owners",new NbtList());reject(t,pool,mixed,"mixed legacy and packed envelope is rejected even after a cache hit");
            NbtCompound trailing=saved.copy();byte[] bytes=trailing.getByteArray(CompositeOwnerNbt.KEY);trailing.putByteArray(CompositeOwnerNbt.KEY,Arrays.copyOf(bytes,bytes.length+1));reject(t,pool,trailing,"trailing packed data is rejected");
            reject(t,pool,input,"invalid root tag is rejected");
            NbtCompound changed=saved.copy();var rows=CompositeOwnerNbt.read(changed);rows.getCompound(0).getCompound("owner").putUuid("uuid",UUID.randomUUID());CompositeOwnerNbt.write(changed,rows);
            var different=pool.savedContributions(changed);t.assertTrue(different!=decoded&&!different.equals(decoded),"a different saved UUID never aliases a verified packed list");
        }
        for(var field:CompositeData.DecodePool.class.getDeclaredFields())if(Map.class.isAssignableFrom(field.getType())){
            try{field.setAccessible(true);t.assertTrue(((Map<?,?>)field.get(pool)).isEmpty(),"closing the actual decode scope clears "+field.getName());}catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
        }
        t.complete();
    }
    private static void reject(TestContext t,CompositeData.DecodePool pool,NbtCompound nbt,String context){boolean rejected=false;try{pool.savedContributions(nbt);}catch(IllegalArgumentException expected){rejected=true;}t.assertTrue(rejected,context);}

    @GameTest(templateName="bloodborne_dw:window_test",tickLimit=80,batchId="shape_reuse")
    public void singleLoadedImportedFootprintReusesItsShapeAndRetiredWeakInputsAreReleased(TestContext t)throws Exception{
        ServerWorld world=t.getWorld();BlockPos pos=t.getAbsolutePos(new BlockPos(8,4,8));var block=CompositeArchitecture.kindBlock("source_original_tree");BlockState state=block.getDefaultState();
        List<Box> boxes=List.of(new Box(.125,0,.125,.375,.875,.625));Owner owner=new Owner(UUID.randomUUID(),block.spec.id.toString(),CompositeData.cell(pos));
        var entry=new CompositeData.Contribution(owner,CompositeData.data(state,new NbtCompound()),new Footprint(boxes,boxes));
        try{
            world.setBlockState(pos,state,Block.FORCE_STATE|Block.SKIP_DROPS);var entity=(CompositeBlockEntity)world.getBlockEntity(pos);entity.set(owner,List.of(entry),new NbtCompound());
            VoxelShape shape=ImportedPhysicalShapes.exact(entry.shape().collision());
            t.assertTrue(ImportedPhysicalShapes.collision(world,pos,state).orElseThrow()==shape,"one imported owner uses its retained exact footprint directly");
            entity.set(new Owner(UUID.randomUUID(),owner.registryId(),owner.root()),entity.contributions(),entity.payload());
            t.assertTrue(ImportedPhysicalShapes.collision(world,pos,state).orElseThrow().isEmpty(),"direct shape reuse still rejects a proven replaced owner UUID");
        }finally{world.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.FORCE_STATE|Block.SKIP_DROPS);CompositeRuntime.drain(world);}
        var field=ImportedPhysicalShapes.class.getDeclaredField("CACHE");field.setAccessible(true);Map<?,?> cache=(Map<?,?>)field.get(null);
        t.assertTrue(cache instanceof WeakHashMap,"the sole dynamic exact-shape lookup owns no input lists strongly");
        var references=retiredShape();collect(t,references.get(0),"unloaded immutable input list");
        synchronized(cache){cache.size();} // WeakHashMap expunges its queued stale entries.
        collect(t,references.get(1),"computed shape after its retired key is expunged");t.complete();
    }
    private static List<WeakReference<?>> retiredShape(){
        List<Box> boxes=List.of(new Box(.12345671,.23456782,.34567893,.45678914,.56789125,.67891236));VoxelShape shape=ImportedPhysicalShapes.exact(boxes);
        return List.of(new WeakReference<>(boxes),new WeakReference<>(shape));
    }
    private static void collect(TestContext t,WeakReference<?> reference,String context)throws InterruptedException{
        for(int attempt=0;attempt<8&&reference.get()!=null;attempt++){System.gc();Thread.sleep(10);}
        t.assertTrue(reference.get()==null,"no dynamic retained cache value survives: "+context);
    }

    @GameTest(templateName="bloodborne_dw:window_test",tickLimit=200,batchId="shape_reuse_save")
    public void selectiveStaticRemovalPreservesSharedNeighbourThroughNativeDiskChunkReload(TestContext t)throws Exception{
        ServerWorld world=t.getWorld();BlockPos base=t.getAbsolutePos(new BlockPos(8,8,8));
        Map<Set<StaticArchitectureCatalog.Fragment>,StaticArchitectureCatalog.Recipe> byParts=new HashMap<>();
        for(var recipe:StaticArchitectureCatalog.recipes())byParts.putIfAbsent(Set.copyOf(recipe.fragments()),recipe);
        StaticArchitectureCatalog.Recipe chosen=null;Map<Cell,StaticArchitectureCatalog.Recipe> before=null;
        for(var recipe:StaticArchitectureCatalog.recipes()){
            if(recipe.resident()<0||recipe.fragments().size()<2||recipe.fragments().stream().anyMatch(part->!part.member()))continue;
            Map<Cell,List<StaticArchitectureCatalog.Fragment>> cells=new TreeMap<>();
            for(var part:recipe.fragments())for(var relative:part.pose().footprint().keySet()){
                Cell at=part.origin().add(relative);
                cells.computeIfAbsent(at,ignored->new ArrayList<>()).add(new StaticArchitectureCatalog.Fragment(part.pose(),part.origin().subtract(at),true));
            }
            if(cells.size()>48||cells.keySet().stream().anyMatch(cell->Math.abs(cell.x())>8||Math.abs(cell.y())>16||Math.abs(cell.z())>8))continue;
            Map<Cell,StaticArchitectureCatalog.Recipe> candidate=new TreeMap<>();boolean complete=true;
            for(var row:cells.entrySet()){var match=byParts.get(Set.copyOf(row.getValue()));if(match==null){complete=false;break;}candidate.put(row.getKey(),match);}
            if(complete){chosen=recipe;before=candidate;break;}
        }
        t.assertTrue(chosen!=null,"finite catalog contains a complete overlapping static component fixture");
        var removed=chosen.fragments().get(chosen.resident());Cell removedRoot=removed.origin();
        Map<Cell,BlockState> expected=new TreeMap<>();Set<Long> chunks=new HashSet<>();
        try{
            for(var row:before.entrySet()){
                BlockPos pos=base.add(row.getKey().x(),row.getKey().y(),row.getKey().z());world.getChunk(pos);chunks.add(new ChunkPos(pos).toLong());world.setBlockState(pos,StaticArchitectureCatalog.state(row.getValue().index()),Block.FORCE_STATE|Block.SKIP_DROPS);
                int member=-1;for(int i=0;i<row.getValue().fragments().size();i++){
                    var part=row.getValue().fragments().get(i);if(part.pose()==removed.pose()&&row.getKey().add(part.origin()).equals(removedRoot)){member=i;break;}
                }
                expected.put(row.getKey(),member<0?StaticArchitectureCatalog.state(row.getValue().index()):row.getValue().without(member));
            }
            BlockPos root=base.add(removedRoot.x(),removedRoot.y(),removedRoot.z());
            var result=StaticArchitectureRuntime.remove(world,root,removed.pose());
            t.assertTrue(result.outcome()==TransactionCore.Outcome.COMMITTED,"actual scoped component removal commits: "+result);
            t.assertTrue(!expected.get(Cell.ORIGIN).isAir(),"the jointly occupied target cell retains its other static component");
            for(var row:expected.entrySet()){
                BlockPos pos=base.add(row.getKey().x(),row.getKey().y(),row.getKey().z());t.assertTrue(world.getBlockState(pos).equals(row.getValue()),"actual removal preserves exact remaining recipe at "+pos);
                if(row.getValue().getBlock() instanceof StaticArchitectureBlock)sameVolume(t,original(StaticArchitectureCatalog.recipe(row.getValue()).collision()),row.getValue().getCollisionShape(world,pos),"remaining static collision "+pos);
            }
            Path directory=Path.of("shape-reuse-native-save");Files.createDirectories(directory);
            for(long packed:chunks){
                ChunkPos chunkPos=new ChunkPos(packed);WorldChunk loaded=world.getChunk(chunkPos.x,chunkPos.z);
                Path file=directory.resolve("chunk."+chunkPos.x+"."+chunkPos.z+".nbt");NbtIo.writeCompressed(ChunkSerializer.serialize(world,loaded),file.toFile());
                ProtoChunk decoded=ChunkSerializer.deserialize(world,world.getPointOfInterestStorage(),chunkPos,NbtIo.readCompressed(file.toFile()));
                t.assertTrue(decoded instanceof WrapperProtoChunk,"native full-chunk deserializer reconstructs a distinct WorldChunk from disk");WorldChunk reopened=((WrapperProtoChunk)decoded).getWrappedChunk();
                t.assertTrue(reopened!=loaded,"native disk reload owns a fresh palette/chunk instance");
                for(var row:expected.entrySet()){
                    BlockPos pos=base.add(row.getKey().x(),row.getKey().y(),row.getKey().z());if(!new ChunkPos(pos).equals(chunkPos))continue;
                    BlockState actual=reopened.getBlockState(pos);t.assertTrue(actual.equals(row.getValue()),"disk chunk reload preserves exact remaining native static state "+pos);
                    if(actual.getBlock() instanceof StaticArchitectureBlock){var recipe=StaticArchitectureCatalog.recipe(actual);t.assertTrue(actual.getCollisionShape(reopened,pos)==recipe.collisionShape()&&actual.getOutlineShape(reopened,pos)==recipe.outlineShape(),"reopened palette points directly at the same shared exact catalog forms");}
                    t.assertTrue(reopened.getBlockEntity(pos)==null,"static cell reload does not create owner entities");
                }
            }
            dev.dreamwalker.bloodbornedw.DreamwalkerBb.LOG.info("SHAPE_REUSE_NATIVE_SAVE_VERIFIED fixtureRecipe={} cells={} nativeChunks={} diskDirectory={}",chosen.index(),expected.size(),chunks.size(),directory.toAbsolutePath());
            t.complete();
        }finally{for(Cell cell:before.keySet()){BlockPos pos=base.add(cell.x(),cell.y(),cell.z());world.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.FORCE_STATE|Block.SKIP_DROPS);}}
    }
}
