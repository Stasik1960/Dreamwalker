package dev.dreamwalker.bloodbornedw.compat.worldedit;

import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import java.util.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** Whole owner removal/replacement is preflighted before any WorldEdit write.
 * A failed incoming placement never destroys the previous object. No source
 * conversion/no-collide privilege is used for ordinary WorldEdit operations. */
public final class WorldEditObjectTransactions {
    private WorldEditObjectTransactions() {}
    public static TransactionCore.Result replace(ServerWorld world,List<Owner> remove,List<ObjectInstance> add) {
        return replace(world,remove,add,Map.of());
    }
    public static TransactionCore.Result replace(ServerWorld world,List<Owner> remove,List<ObjectInstance> add,Map<BlockPos,CellSnapshot.BlockData> nativeChanges) {
        if(!world.getServer().isOnThread())throw new IllegalStateException("WorldEdit requires the server thread");
        Map<Owner,ObjectInstance> incoming=new LinkedHashMap<>();
        for(ObjectInstance object:add) {
            for(Cell offset:object.cells().keySet())world.getChunk((object.owner().root().x()+offset.x())>>4,(object.owner().root().z()+offset.z())>>4);
            incoming.put(object.owner(),object);
        }
        for(Owner owner:remove)world.getChunk(owner.root().x()>>4,owner.root().z()>>4);
        FabricCompositeWorld access=new FabricCompositeWorld(world,incoming);
        for(var entry:nativeChanges.entrySet())if(CompositeData.state(entry.getValue()).getBlock() instanceof NativePhysicsBlock){
            var payload=CompositeData.nbt(entry.getValue().blockEntityNbt());
            if(!payload.contains(NativeVisuals.ITEM_KEY,net.minecraft.nbt.NbtElement.INT_TYPE))return new TransactionCore.Result(TransactionCore.Outcome.REJECTED,0,"WorldEdit clipboard is missing NativeVisualIndex");
            int visual=payload.getInt(NativeVisuals.ITEM_KEY);
            if(visual<0||visual>=NativeGridCatalog.cells().size()||NativeVisualLayout.physics(visual)<0||!NativeGridCatalog.state(visual).equals(CompositeData.state(entry.getValue())))return new TransactionCore.Result(TransactionCore.Outcome.REJECTED,0,"Invalid WorldEdit native visual/physics pair");
            access.authorizeNativeVisualWrite(CompositeData.cell(entry.getKey()),visual);
        }
        Map<Cell,CellSnapshot> expected=new TreeMap<>(),overlay=new TreeMap<>();
        TransactionCore.WorldAccess virtual=new TransactionCore.WorldAccess() {
            public void assertMutationThread(){access.assertMutationThread();}
            public String dimensionKey(){return access.dimensionKey();}
            public boolean isLoaded(Cell c){world.getChunk(c.x()>>4,c.z()>>4);return access.isLoaded(c);}
            public boolean inBounds(Cell c){return access.inBounds(c);}
            public CellSnapshot read(Cell c){expected.computeIfAbsent(c,access::read);return overlay.getOrDefault(c,expected.get(c));}
            public Optional<ObjectInstance> describeRoot(Owner o,CellSnapshot s){return access.describeRoot(o,s);}
            public boolean supportsGuestBindings(CellSnapshot s){return access.supportsGuestBindings(s);}
            public boolean allowsReviewedOverlap(ObjectInstance a,ObjectInstance b,Cell c){return access.allowsReviewedOverlap(a,b,c);}
            public boolean intersectsEntities(Cell c,Footprint p){return access.intersectsEntities(c,p);}
            public boolean needsRefresh(Cell c,CellSnapshot a,CellSnapshot b){return access.needsRefresh(c,a,b);}
            public boolean writeSilently(Cell c,CellSnapshot s){throw new IllegalStateException("WorldEdit preflight cannot write");}
            public void publishCommitted(List<Cell> c){throw new IllegalStateException("WorldEdit preflight cannot publish");}
        };
        for(Owner owner:remove) {
            TransactionCore.Preparation step=CompositeRuntime.core().removal(virtual,owner);
            if(!step.accepted())return new TransactionCore.Result(TransactionCore.Outcome.REJECTED,0,step.reason());
            overlay.putAll(step.plan().writes());
        }
        for(var entry:nativeChanges.entrySet()){
            BlockPos p=entry.getKey();world.getChunk(p.getX()>>4,p.getZ()>>4);Cell cell=CompositeData.cell(p);CellSnapshot before=virtual.read(cell);if(before.resident()!=null)return new TransactionCore.Result(TransactionCore.Outcome.REJECTED,0,"WorldEdit native replacement touches an unrelated owner root");var state=CompositeData.state(entry.getValue());
            if(!state.isAir())for(var box:state.getCollisionShape(world,p,net.minecraft.block.ShapeContext.absent()).getBoundingBoxes()){
                var placed=box.offset(p);for(Owner guest:before.guests())for(var contribution:CompositeRuntime.contributions(world,p))if(contribution.owner().equals(guest)&&!remove.contains(guest))for(var part:CompositeRuntime.physicalParts(contribution,p))if(dev.dreamwalker.bloodbornedw.architecture.PlacementPhysics.overlaps(placed,new net.minecraft.util.math.Box(p.getX()+part.minX(),p.getY()+part.minY(),p.getZ()+part.minZ(),p.getX()+part.maxX(),p.getY()+part.maxY(),p.getZ()+part.maxZ())))return new TransactionCore.Result(TransactionCore.Outcome.REJECTED,0,"WorldEdit native replacement overlaps an unrelated BB guest");
                String entity=dev.dreamwalker.bloodbornedw.architecture.PlacementPhysics.entityConflict(world,List.of(placed),null,false);if(entity!=null)return new TransactionCore.Result(TransactionCore.Outcome.REJECTED,0,entity);
            }
            var data=entry.getValue();CellSnapshot.Kind kind=state.isAir()?before.guests().isEmpty()?CellSnapshot.Kind.AIR:CellSnapshot.Kind.HELPER:CellSnapshot.Kind.FOREIGN;if(kind==CellSnapshot.Kind.HELPER)data=CompositeData.data(CompositeArchitecture.CELL.getDefaultState(),new net.minecraft.nbt.NbtCompound());overlay.put(cell,new CellSnapshot(kind,data,null,before.guests()));
        }
        for(ObjectInstance object:add) {
            Cell root=object.owner().root();CellSnapshot previous=virtual.read(root);
            // WorldEdit explicitly replaces the targeted native carrier. This
            // affects only the requested root, preserving unrelated guests.
            if(previous.kind()==CellSnapshot.Kind.FOREIGN)overlay.put(root,new CellSnapshot(previous.guests().isEmpty()?CellSnapshot.Kind.AIR:CellSnapshot.Kind.HELPER,CompositeData.data(previous.guests().isEmpty()?net.minecraft.block.Blocks.AIR.getDefaultState():CompositeArchitecture.CELL.getDefaultState(),new net.minecraft.nbt.NbtCompound()),null,previous.guests()));
            TransactionCore.Preparation step=CompositeRuntime.core().placement(virtual,object,List.of());
            if(!step.accepted())return new TransactionCore.Result(TransactionCore.Outcome.REJECTED,0,step.reason());
            overlay.putAll(step.plan().writes());
        }
        overlay.entrySet().removeIf(e->e.getValue().equals(expected.get(e.getKey()))&&!access.needsRefresh(e.getKey(),expected.get(e.getKey()),e.getValue()));
        TransactionCore.Result result=CompositeRuntime.execute(access,new TransactionCore.Preparation(new TransactionCore.Plan(access.dimensionKey(),expected,overlay,Map.of()),null));
        if(result.outcome()==TransactionCore.Outcome.COMMITTED)for(Owner owner:remove)if(!incoming.containsKey(owner))
            dev.dreamwalker.bloodbornedw.link.MechanismLinks.removedArchitecture(world.getServer(),dev.dreamwalker.bloodbornedw.link.CompositeMechanismBridge.reference(world,owner));
        return result;
    }
}
