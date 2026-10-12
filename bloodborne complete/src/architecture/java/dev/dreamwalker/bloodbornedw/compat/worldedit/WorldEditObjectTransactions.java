package dev.dreamwalker.bloodbornedw.compat.worldedit;

import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import java.util.*;
import net.minecraft.server.world.ServerWorld;

/** Whole owner removal/replacement is preflighted before any WorldEdit write.
 * A failed incoming placement never destroys the previous object. No source
 * conversion/no-collide privilege is used for ordinary WorldEdit operations. */
public final class WorldEditObjectTransactions {
    private WorldEditObjectTransactions() {}
    public static TransactionCore.Result replace(ServerWorld world,List<Owner> remove,List<ObjectInstance> add) {
        world.getServer().assertOnThread();
        Map<Owner,ObjectInstance> incoming=new LinkedHashMap<>();
        for(ObjectInstance object:add) {
            for(Cell offset:object.cells().keySet())world.getChunk((object.owner().root().x()+offset.x())>>4,(object.owner().root().z()+offset.z())>>4);
            incoming.put(object.owner(),object);
        }
        for(Owner owner:remove)world.getChunk(owner.root().x()>>4,owner.root().z()>>4);
        FabricCompositeWorld access=new FabricCompositeWorld(world,incoming);
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
        for(ObjectInstance object:add) {
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
