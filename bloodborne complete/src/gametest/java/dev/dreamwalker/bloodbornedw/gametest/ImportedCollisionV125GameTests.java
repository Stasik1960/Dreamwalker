package dev.dreamwalker.bloodbornedw.gametest;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornedw.architecture.PlacementPhysics;
import dev.dreamwalker.bloodbornedw.architecture.SourceConversionScope;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Footprint;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.entity.MovementType;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.network.*;
import net.minecraft.network.packet.Packet;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.*;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShapes;

/** Saved native ownership, real player movement and ordinary item admission.
 * No fixture installs a persistent overlap privilege or changes a V12 object. */
public final class ImportedCollisionV125GameTests implements FabricGameTest {
    private static final String HALF_ARCH="source_stairs_axed_exposed_cut_copper_stairs_outer_3";
    private static NbtCompound payload(){var n=new NbtCompound();n.putInt(ImportedTypeAliases.VERSION_KEY,1);return n;}
    private static ServerPlayerEntity player(ServerWorld world){
        var p=new ServerPlayerEntity(world.getServer(),world,new GameProfile(UUID.randomUUID(),"Collision125Probe"));
        var connection=new ClientConnection(NetworkSide.SERVERBOUND){@Override public void send(Packet<?> packet){} @Override public void send(Packet<?> packet,PacketCallbacks callbacks){}};
        p.networkHandler=new ServerPlayNetworkHandler(world.getServer(),connection,p);p.changeGameMode(net.minecraft.world.GameMode.CREATIVE);p.getAbilities().allowModifyWorld=true;return p;
    }
    private static void load(ServerWorld world,BlockPos root,Map<Cell,Footprint> cells){
        var chunks=new HashSet<ChunkPos>();
        for(var cell:cells.keySet()){var pos=root.add(cell.x(),cell.y(),cell.z());for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)chunks.add(new ChunkPos((pos.getX()>>4)+dx,(pos.getZ()>>4)+dz));}
        for(var chunk:chunks){world.setChunkForced(chunk.x,chunk.z,true);world.getChunk(new BlockPos(chunk.x<<4,0,chunk.z<<4));}
    }
    private static BlockState state(String path){return ImportedTypeAliases.target(Registries.BLOCK.get(new Identifier("bloodborne_dw",path)).getDefaultState());}
    private static JsonObject descriptor(BlockState state){
        String path="/bloodborne_dw/composite/"+Registries.BLOCK.getId(state.getBlock()).getPath()+".json";
        var input=ImportedCollisionV125GameTests.class.getResourceAsStream(path);if(input==null)throw new IllegalStateException(path);
        try(var reader=new InputStreamReader(input,StandardCharsets.UTF_8)){return JsonParser.parseReader(reader).getAsJsonObject();}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    /** Historical broad defaults remain immutable in descriptors, independently
     * of the replacement table. Install these real BEFORE bytes into the ledger. */
    private static Map<Cell,Footprint> oldFootprint(BlockState state,NbtCompound payload){
        var raw=descriptor(state).getAsJsonArray("variants").get(CompositeSpec.poseVariant(state)).getAsJsonObject().getAsJsonObject("poses");
        var pose=raw.getAsJsonObject(state.get(CompositeRootBlock.OPEN)&&raw.has("open")?"open":"closed");
        boolean table=pose.has("simpleCollisionByRotation");
        var rows=table?pose.getAsJsonArray("simpleCollisionByRotation").get(state.get(CompositeRootBlock.ROTATION)).getAsJsonArray():pose.getAsJsonArray("collision");
        var boxes=new ArrayList<ImportedCollisionMath.Box>();
        for(var entry:rows){var row=entry.getAsJsonObject();var lo=row.getAsJsonArray("from");var hi=row.getAsJsonArray("to");boxes.add(new ImportedCollisionMath.Box(lo.get(0).getAsDouble()/16,lo.get(1).getAsDouble()/16,lo.get(2).getAsDouble()/16,hi.get(0).getAsDouble()/16,hi.get(1).getAsDouble()/16,hi.get(2).getAsDouble()/16));}
        var shift=CompositeSourceShift.world(state,payload);var result=new TreeMap<Cell,List<dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box>>();
        for(var box:ImportedCollisionMath.transform(boxes,table?0:state.get(CompositeRootBlock.ROTATION),payload.getDouble("MountY")+payload.getDouble("VerticalOffset"),true,shift.x(),shift.y(),shift.z())){
            for(int x=(int)Math.floor(box.x0()+1e-8);x<Math.ceil(box.x1()-1e-8);x++)for(int y=(int)Math.floor(box.y0()+1e-8);y<Math.ceil(box.y1()-1e-8);y++)for(int z=(int)Math.floor(box.z0()+1e-8);z<Math.ceil(box.z1()-1e-8);z++){
                double a=Math.max(0,box.x0()-x),b=Math.max(0,box.y0()-y),c=Math.max(0,box.z0()-z),d=Math.min(1,box.x1()-x),e=Math.min(1,box.y1()-y),f=Math.min(1,box.z1()-z);
                if(d>a&&e>b&&f>c)result.computeIfAbsent(new Cell(x,y,z),unused->new ArrayList<>()).add(new dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box(a,b,c,d,e,f));
            }
        }
        var footprints=new TreeMap<Cell,Footprint>();result.forEach((cell,value)->footprints.put(cell,new Footprint(value,value)));footprints.putIfAbsent(Cell.ORIGIN,Footprint.EMPTY);return footprints;
    }
    private static List<Cell> released(ObjectInstance old,ObjectInstance current){return old.cells().keySet().stream().filter(cell->!cell.equals(Cell.ORIGIN)&&!current.cells().containsKey(cell)&&!old.cells().get(cell).collision().isEmpty()).sorted(Comparator.comparingInt(Cell::y).thenComparingInt(Cell::x).thenComparingInt(Cell::z)).toList();}
    private static void outline(TestContext t,ObjectInstance current){
        var world=t.getWorld();for(var cell:current.cells().entrySet()){
            if(cell.getValue().collision().isEmpty())continue;
            t.assertTrue(cell.getValue().selection().equals(cell.getValue().collision()),"saved selection matches physical contribution at "+cell.getKey());
            var pos=CompositeData.pos(current.owner().root().add(cell.getKey()));var state=world.getBlockState(pos);
            t.assertTrue(!VoxelShapes.matchesAnywhere(state.getCollisionShape(world,pos),state.getOutlineShape(world,pos),net.minecraft.util.function.BooleanBiFunction.NOT_SAME),"actual native outline matches collision at "+pos);
        }
    }
    private static void stone(TestContext t,ServerPlayerEntity player,BlockPos target){
        var world=t.getWorld();world.setBlockState(target.down(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
        var stack=new ItemStack(Items.STONE);player.setStackInHand(Hand.MAIN_HAND,stack);
        var hit=new BlockHitResult(Vec3d.ofCenter(target.down()).add(0,.5,0),Direction.UP,target.down(),false);
        var result=stack.getItem().useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit));
        t.assertTrue(result.isAccepted()&&world.getBlockState(target).isOf(Blocks.STONE),"ordinary vanilla BlockItem installs in a cell released from the old AABB");
    }
    private static void movement(TestContext t,ObjectInstance current,List<Cell> candidates){
        var world=t.getWorld();var p=player(world);var boxes=PlacementPhysics.physicalBoxes(current);boolean found=false;
        try{
            for(var cell:candidates){var pos=CompositeData.pos(current.owner().root().add(cell));
                for(int axis:new int[]{2,0}){
                    Vec3d centre=new Vec3d(pos.getX()+.5,pos.getY()+.05,pos.getZ()+.5);
                    Vec3d delta=axis==2?new Vec3d(0,0,3):new Vec3d(3,0,0);var start=centre.subtract(delta.multiply(.5));
                    boolean clear=true;for(int sample=0;sample<=12;sample++){var foot=start.add(delta.multiply(sample/12.));var body=new Box(foot.x-.3,foot.y,foot.z-.3,foot.x+.3,foot.y+1.8,foot.z+.3);if(boxes.stream().anyMatch(box->PlacementPhysics.overlaps(body,box))||!world.isSpaceEmpty(p,body)){clear=false;break;}}
                    if(!clear)continue;p.refreshPositionAndAngles(start.x,start.y,start.z,0,0);p.move(MovementType.SELF,delta);
                    t.assertTrue(p.getPos().squaredDistanceTo(start.add(delta))<1e-8,"real 0.6x1.8 player movement crosses the released opening/corner without hitting an enclosing AABB");found=true;break;
                }
                if(found)break;
            }
            t.assertTrue(found,"a genuinely traversable old filled region was tested through actual Entity.move");
        }finally{p.discard();}
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=160,batchId="v125_saved_collision")
    public void savedHalfArchRebakesOwnedCellsWithoutChangingInstanceAndOpensPlayerAndBlockPassage(TestContext t){
        var world=t.getWorld();var root=t.getAbsolutePos(new BlockPos(80,96,80));var state=state(HALF_ARCH);var n=payload();
        n.putString("CustomEvent","preserved");n.putUuid("LinkedInstance",UUID.randomUUID());n.putIntArray("CustomNumbers",new int[]{1,-2,17});n.putDouble("VerticalOffset",.125);
        UUID uuid=UUID.randomUUID();var current=CompositeRuntime.instance(world,root,state,uuid,n);var old=new ObjectInstance(current.owner(),current.rootData(),oldFootprint(state,n));load(world,root,old.cells());load(world,root,current.cells());
        var access=new FabricCompositeWorld(world,Map.of(old.owner(),old));t.assertTrue(CompositeRuntime.execute(access,CompositeRuntime.core().placement(access,old,List.of())).outcome()==TransactionCore.Outcome.COMMITTED,"real saved broad half-arch owner installed");
        var freed=released(old,current);t.assertTrue(freed.size()>=3,"half-arch frees multiple old broad helper cells");
        var foreign=CompositeData.pos(old.owner().root().add(freed.get(freed.size()-1)));world.setBlockState(foreign,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
        ImportedTypeMigration.forget(world);ImportedCollisionMigration.forget(world);ImportedCollisionMigration.loadedChunk(world,new ChunkPos(root));
        t.assertTrue(world.getBlockEntity(root) instanceof CompositeBlockEntity,"rebaked root retains its actual BE");var be=(CompositeBlockEntity)world.getBlockEntity(root);
        t.assertTrue(be.resident().equals(old.owner())&&be.payload().equals(n)&&world.getBlockState(root).equals(state),"UUID, registry, state, private typed settings, links and art remain unchanged");
        for(var cell:old.cells().keySet()){var absolute=old.owner().root().add(cell);var entries=CompositeLedger.get(world).at(absolute).stream().filter(entry->entry.owner().equals(old.owner())).toList();
            t.assertTrue(current.cells().containsKey(cell)?entries.size()==1&&entries.get(0).shape().equals(current.cells().get(cell)):entries.isEmpty(),"saved ledger exactly replaces old membership, including released cells "+cell);
        }
        t.assertTrue(world.getBlockState(foreign).isOf(Blocks.STONE),"foreign native stone in a released old helper cell survives the atomic rebake");
        outline(t,current);var available=freed.stream().filter(cell->!CompositeData.pos(old.owner().root().add(cell)).equals(foreign)).toList();movement(t,current,available);
        var target=available.stream().map(cell->CompositeData.pos(old.owner().root().add(cell))).filter(pos->world.getBlockState(pos).isAir()&&world.getBlockState(pos.down()).isAir()).findFirst().orElseThrow();
        var cube=new Box(target);t.assertTrue(PlacementPhysics.ordinaryPlacementConflict(world,List.of(cube),null)==null,"native placement physics sees the released cell as empty");var p=player(world);try{stone(t,p,target);}finally{p.discard();}
        var solid=PlacementPhysics.physicalBoxes(current).get(0);t.assertTrue(PlacementPhysics.ordinaryPlacementConflict(world,List.of(solid),null)!=null,"ordinary physical overlap with a remaining arch support is still rejected");
        t.assertTrue(CompositeRuntime.place(world,root,state,UUID.randomUUID(),null,payload()).outcome()!=TransactionCore.Outcome.COMMITTED,"repeat normal composite placement never bypasses the resident owner");
        t.assertTrue(!SourceConversionScope.initialInstance(uuid),"saved collision rebake privilege ends before subsequent placements");
        CompositeRuntime.remove(world,old.owner(),null,false);world.setBlockState(foreign,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);world.setBlockState(target,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);world.setBlockState(target.down(),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);t.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=160,batchId="v125_diagonal_collision")
    public void diagonalPlatformCornersPermitRealMovementAndCubePlacementWhilePlatformRemainsSolid(TestContext t){
        var world=t.getWorld();var root=t.getAbsolutePos(new BlockPos(144,96,144));BlockState chosen=null;ObjectInstance current=null,old=null;
        for(var block:CompositeArchitecture.blocks()){
            if(!ImportedCollisionV125.supports(block.spec)||block instanceof ImportedStairBlock||block instanceof ImportedLadderBlock||block.spec.requiredSupport)continue;
            var candidate=ImportedTypeAliases.target(block.getDefaultState());if(candidate.getBlock()!=block)continue;
            var pose=block.spec.pose(candidate);if(pose.collision().size()!=1)continue;var box=pose.collision().get(0);
            if(box.to().x()-box.from().x()<32||box.to().z()-box.from().z()<32||box.to().y()-box.from().y()>24)continue;
            candidate=candidate.with(CompositeRootBlock.ROTATION,1);var instance=CompositeRuntime.instance(world,root,candidate,UUID.randomUUID(),payload());var before=new ObjectInstance(instance.owner(),instance.rootData(),oldFootprint(candidate,payload()));
            if(released(before,instance).size()<3||PlacementPhysics.physicalBoxes(instance).isEmpty())continue;
            chosen=candidate;current=instance;old=before;break;
        }
        t.assertTrue(current!=null,"catalogue has a real large diagonal platform with released outside corners");load(world,root,old.cells());load(world,root,current.cells());
        t.assertTrue(CompositeRuntime.place(world,root,chosen,current.owner().instanceId(),null,payload()).outcome()==TransactionCore.Outcome.COMMITTED,"ordinary diagonal platform placement commits");
        final var placed=current;outline(t,placed);var freed=released(old,placed);movement(t,placed,freed);
        var target=freed.stream().map(cell->CompositeData.pos(placed.owner().root().add(cell))).filter(pos->world.getBlockState(pos).isAir()&&world.getBlockState(pos.down()).isAir()).findFirst().orElseThrow();
        t.assertTrue(PlacementPhysics.ordinaryPlacementConflict(world,List.of(new Box(target)),null)==null,"rotated outer AABB corner is actually free to ordinary blocks");var p=player(world);try{stone(t,p,target);}finally{p.discard();}
        t.assertTrue(PlacementPhysics.ordinaryPlacementConflict(world,List.of(PlacementPhysics.physicalBoxes(current).get(0)),null)!=null,"purposeful diagonal platform boxes remain solid to normal placement");
        CompositeRuntime.remove(world,current.owner(),null,false);world.setBlockState(target,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);world.setBlockState(target.down(),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);t.complete();
    }
}
