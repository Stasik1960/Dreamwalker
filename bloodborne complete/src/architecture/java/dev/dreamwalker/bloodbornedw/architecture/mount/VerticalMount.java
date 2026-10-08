package dev.dreamwalker.bloodbornedw.architecture.mount;

import java.util.*;
import dev.dreamwalker.bloodbornedw.architecture.*;
import dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallBlock;
import dev.dreamwalker.bloodbornedw.architecture.ladder_source.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
import net.minecraft.world.*;

/** User offset is separate from the authored source shift and initial clicked-face seat.
 * The root palette/UUID stays fixed. Only transactional owner contributions move.
 * The temporary height-edit permission never enters a picked item or saved privilege. */
public final class VerticalMount {
    public static final String KEY="VerticalOffset";
    private static final ThreadLocal<Boolean> RAW=ThreadLocal.withInitial(()->false);
    private static final ThreadLocal<Boolean> HEIGHT_EDIT=ThreadLocal.withInitial(()->false);
    private VerticalMount(){}
    public static boolean isNative(BlockState state){return state.getBlock() instanceof PrototypeLadderBlock||state.getBlock() instanceof PrototypeWallBlock;}
    public static boolean isArchitecture(BlockState state){return isNative(state)||state.getBlock() instanceof CompositeRootBlock;}
    public static boolean rawQuery(){return RAW.get();}
    public static boolean heightEdit(){return HEIGHT_EDIT.get();}
    /** Do not force a chunk or ask for a currently incomplete FULL future from a shape query. */
    public static BlockEntity loadedEntity(BlockView view,BlockPos pos){
        if(view==null||pos==null)return null;
        if(CompositeShapeSnapshots.worker(view))return null;
        if(view instanceof World world){var chunk=world.getChunkManager().getWorldChunk(pos.getX()>>4,pos.getZ()>>4);return chunk==null?null:chunk.getBlockEntity(pos);}
        return view.getBlockEntity(pos);
    }
    public static double offset(BlockView world,BlockPos root){if(CompositeShapeSnapshots.worker(world))return CompositeShapeSnapshots.offset(world,root);return loadedEntity(world,root) instanceof CompositeBlockEntity own?own.verticalOffset():0;}
    public static double offset(World world,BlockPos root){return offset((BlockView)world,root);}
    public static boolean shifted(BlockView world,BlockPos root){return !rawQuery()&&offset(world,root)!=0;}
    public static Owner owner(BlockView view,BlockPos root){if(CompositeShapeSnapshots.worker(view))return CompositeShapeSnapshots.owner(view,root);return loadedEntity(view,root) instanceof CompositeBlockEntity own?own.resident():null;}
    public static dev.dreamwalker.bloodbornedw.link.MechanismLinks.TargetRef reference(ServerWorld world,BlockPos root){Owner owner=owner(world,root);return owner==null?null:new dev.dreamwalker.bloodbornedw.link.MechanismLinks.TargetRef(dev.dreamwalker.bloodbornedw.link.MechanismLinks.Kind.ARCHITECTURE,world.getRegistryKey().getValue().toString(),owner.instanceId(),root.asLong(),owner.registryId());}
    public static ItemStack item(BlockState state,NbtCompound payload){
        if(state.getBlock() instanceof CompositeRootBlock block)return block.art(state,payload);
        if(state.getBlock() instanceof PrototypeLadderBlock block)return block.artisticStack(state);
        if(state.getBlock() instanceof PrototypeWallBlock block)return block.artisticStack(state);
        return ItemStack.EMPTY;
    }
    public static void placed(World world,BlockPos root,BlockState state){
        if(world.isClient||!isNative(state)||state.getBlock() instanceof PrototypeLadderBlock&&state.get(PrototypeLadderBlock.SOURCE_CLONE))return;
        if(world.getBlockEntity(root) instanceof CompositeBlockEntity own&&own.resident()==null)
            own.set(new Owner(UUID.randomUUID(),Registries.BLOCK.getId(state.getBlock()).toString(),CompositeData.cell(root)),List.of(),new NbtCompound());
    }
    public static Map<Cell,Footprint> nativeFootprint(BlockView view,BlockPos pos,BlockState state,NbtCompound payload){
        double amount=payload.getDouble(KEY);TreeMap<Cell,List<dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box>> physical=new TreeMap<>(),selected=new TreeMap<>();
        boolean previous=RAW.get();RAW.set(true);
        try{
            List<net.minecraft.util.math.Box> collision=payload.contains("NativeGeometry",10)?readBoxes(payload.getCompound("NativeGeometry"),"collision"):(amount!=0&&state.getBlock() instanceof PrototypeWallBlock wall?wall.rawCollision(state):state.getBlock().getCollisionShape(state,view,pos,ShapeContext.absent())).getBoundingBoxes();
            List<net.minecraft.util.math.Box> outline=payload.contains("NativeGeometry",10)?readBoxes(payload.getCompound("NativeGeometry"),"selection"):state.getBlock().getOutlineShape(state,view,pos,ShapeContext.absent()).getBoundingBoxes();
            for(var box:collision)distribute(box.offset(0,amount,0),physical);
            for(var box:outline)distribute(box.offset(0,amount,0),selected);
            if(state.getBlock() instanceof PrototypeLadderBlock&&state.get(PrototypeLadderBlock.SOURCE_CLONE)&&payload.contains("NativeBackingX")){
                // SourceLadderBlockEntity exposes this role relative to its root.
                BlockPos backing=new BlockPos(payload.getInt("NativeBackingX"),payload.getInt("NativeBackingY"),payload.getInt("NativeBackingZ"));var cube=new net.minecraft.util.math.Box(backing).offset(0,amount,0);
                distribute(cube,physical);distribute(cube,selected);
                // Keep the owned fixed installation role. Its native shape is
                // suppressed while shifted; its original typed provenance stays.
                physical.computeIfAbsent(CompositeData.cell(backing),unused->new ArrayList<>());
            }
        }finally{RAW.set(previous);}
        TreeSet<Cell> cells=new TreeSet<>(physical.keySet());cells.addAll(selected.keySet());cells.add(Cell.ORIGIN);
        TreeMap<Cell,Footprint> result=new TreeMap<>();for(Cell cell:cells)result.put(cell,new Footprint(physical.getOrDefault(cell,List.of()),selected.getOrDefault(cell,List.of())));
        return Collections.unmodifiableMap(result);
    }
    /** Snapshot small native local prisms so a later neighbor change cannot
     * reconstruct the previous membership from already changed world geometry. */
    public static NbtCompound nativePayload(BlockView view,BlockPos pos,BlockState state,NbtCompound supplied){
        if(!isNative(state))return supplied.copy();NbtCompound payload=supplied.copy(),geometry=new NbtCompound();
        boolean previous=RAW.get();RAW.set(true);try{
            var collision=payload.getDouble(KEY)!=0&&state.getBlock() instanceof PrototypeWallBlock wall?wall.rawCollision(state):state.getBlock().getCollisionShape(state,view,pos,ShapeContext.absent());
            geometry.put("collision",writeBoxes(collision.getBoundingBoxes()));geometry.put("selection",writeBoxes(state.getBlock().getOutlineShape(state,view,pos,ShapeContext.absent()).getBoundingBoxes()));
        }finally{RAW.set(previous);}payload.put("NativeGeometry",geometry);return payload;
    }
    private static net.minecraft.nbt.NbtList writeBoxes(List<net.minecraft.util.math.Box> boxes){var out=new net.minecraft.nbt.NbtList();for(var box:boxes){var row=new net.minecraft.nbt.NbtList();for(double v:new double[]{box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ})row.add(net.minecraft.nbt.NbtDouble.of(v));out.add(row);}return out;}
    private static List<net.minecraft.util.math.Box> readBoxes(NbtCompound tag,String key){List<net.minecraft.util.math.Box> boxes=new ArrayList<>();var list=tag.getList(key,9);if(list.size()>64)throw new IllegalArgumentException("Native geometry box budget");for(int i=0;i<list.size();i++){var row=(net.minecraft.nbt.NbtList)list.get(i);if(row.size()!=6||row.getHeldType()!=6)throw new IllegalArgumentException("Invalid native mounted box");double[] values=new double[6];for(int j=0;j<6;j++){values[j]=row.getDouble(j);if(!Double.isFinite(values[j])||Math.abs(values[j])>4)throw new IllegalArgumentException("Invalid native mounted coordinate");}boxes.add(new net.minecraft.util.math.Box(values[0],values[1],values[2],values[3],values[4],values[5]));}return List.copyOf(boxes);}
    private static void distribute(net.minecraft.util.math.Box box,Map<Cell,List<dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box>> target){
        for(int x=(int)Math.floor(box.minX+1e-8);x<(int)Math.ceil(box.maxX-1e-8);x++)for(int y=(int)Math.floor(box.minY+1e-8);y<(int)Math.ceil(box.maxY-1e-8);y++)for(int z=(int)Math.floor(box.minZ+1e-8);z<(int)Math.ceil(box.maxZ-1e-8);z++){
            var local=new dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box(Math.max(0,box.minX-x),Math.max(0,box.minY-y),Math.max(0,box.minZ-z),Math.min(1,box.maxX-x),Math.min(1,box.maxY-y),Math.min(1,box.maxZ-z));
            target.computeIfAbsent(new Cell(x,y,z),unused->new ArrayList<>()).add(local);
        }
    }
    /** Exact numeric offset; GUI steps are caller policy, not an arbitrary storage clamp. */
    public static boolean setOffset(ServerWorld world,BlockPos root,double amount,ServerPlayerEntity player){
        if(!world.getServer().isOnThread()||!Double.isFinite(amount)||!world.isChunkLoaded(root)||!BuildPermissions.canEdit(world,player,root))return false;
        BlockState state=world.getBlockState(root);if(!isArchitecture(state)||!(loadedEntity(world,root) instanceof CompositeBlockEntity own)||own.resident()==null)return false;
        NbtCompound payload=own.payload();double before=payload.getDouble(KEY);if(amount==before)return true;
        // Guard huge input before converting a double to integer cell indices.
        // The only range comes from this world's actual build height and the
        // object's unshifted physical/selection extrema, including initial seat.
        NbtCompound base=payload.copy();base.remove(KEY);base.remove("NativeGeometry");var initial=CompositeRuntime.instance(world,root,state,own.resident().instanceId(),base);
        double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
        for(var entry:initial.cells().entrySet())for(var boxes:List.of(entry.getValue().collision(),entry.getValue().selection()))for(var box:boxes){min=Math.min(min,entry.getKey().y()+box.minY());max=Math.max(max,entry.getKey().y()+box.maxY());}
        if(!Double.isFinite(min)||root.getY()+min+amount<world.getBottomY()-1e-8||root.getY()+max+amount>world.getTopY()+1e-8)return false;
        payload.putDouble(KEY,amount);payload.putBoolean("MountEdited",true);boolean previous=HEIGHT_EDIT.get();HEIGHT_EDIT.set(true);
        try{
            var result=CompositeRuntime.transitionPayload(world,own.resident(),state,payload,player);
            if(dev.dreamwalker.bloodbornedw.diagnostics.DwDiagnostics.enabled(world))dev.dreamwalker.bloodbornedw.diagnostics.DwDiagnostics.record(world,
                dev.dreamwalker.bloodbornedw.diagnostics.ArchitectureDiagnostics.type(state),own.resident().instanceId().toString(),root,"height",
                Map.of("verticalOffset",before),Map.of("verticalOffset",amount,"fixedRoot",root.toShortString()),result.outcome().name(),result.reason());
            return result.outcome()==TransactionCore.Outcome.COMMITTED;
        }
        finally{HEIGHT_EDIT.set(previous);}
    }
    public static Optional<BlockPos> climbing(World world,net.minecraft.util.math.Box body){
        Set<Owner> seen=new HashSet<>();
        for(int x=(int)Math.floor(body.minX);x<Math.ceil(body.maxX);x++)for(int y=(int)Math.floor(body.minY);y<Math.ceil(body.maxY);y++)for(int z=(int)Math.floor(body.minZ);z<Math.ceil(body.maxZ);z++){
            BlockPos cell=new BlockPos(x,y,z);if(!world.isChunkLoaded(cell))continue;
            for(var entry:CompositeRuntime.contributions(world,cell))if(seen.add(entry.owner())){
                BlockPos root=CompositeData.pos(entry.owner().root());if(!(loadedEntity(world,root) instanceof CompositeBlockEntity own)||!entry.owner().equals(own.resident()))continue;
                BlockState state=world.getBlockState(root);if(!(state.getBlock() instanceof PrototypeLadderBlock ladder)||offset(world,root)==0)continue;
                for(var box:ladder.climbingShape(state).getBoundingBoxes())if(PlacementPhysics.overlaps(body,box.offset(root).offset(0,offset(world,root),0)))return Optional.of(root);
            }
        }return Optional.empty();
    }
}
