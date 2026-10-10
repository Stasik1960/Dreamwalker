package dev.dreamwalker.bloodbornedw.architecture;

import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.block.ShapeContext;
import net.minecraft.world.World;

/** Ordinary placement examines active physical volumes, never decorative or selection bounds. */
public final class PlacementPhysics {
    public static final double EPS = ObjectGeometry.PHYSICAL_EPSILON;
    private PlacementPhysics() {}
    public static boolean overlaps(Box a, Box b) {
        return Math.min(a.maxX,b.maxX)-Math.max(a.minX,b.minX)>EPS
                && Math.min(a.maxY,b.maxY)-Math.max(a.minY,b.minY)>EPS
                && Math.min(a.maxZ,b.maxZ)-Math.max(a.minZ,b.minZ)>EPS;
    }
    public static String ordinaryPlacementConflict(World world, List<Box> proposed, Entity exclude) {
        return conflict(world,proposed,null,exclude);
    }
    public static String conflict(World world, List<Box> proposed, Owner excludeOwner, Entity excludeEntity) {
        return conflict(world,proposed,excludeOwner,excludeEntity,null);
    }
    public static String conflict(World world, List<Box> proposed, Owner excludeOwner, Entity excludeEntity, BlockPos replacingNative) {
        List<Box> previous=List.of();
        if(excludeOwner!=null&&world.getBlockEntity(CompositeData.pos(excludeOwner.root())) instanceof CompositeBlockEntity root&&excludeOwner.equals(root.resident()))
            previous=physicalBoxes(CompositeRuntime.instance(world,CompositeData.pos(excludeOwner.root()),world.getBlockState(CompositeData.pos(excludeOwner.root())),excludeOwner.instanceId(),root.payload()));
        for (Box box : proposed) {
            if (box.getXLength()<=EPS || box.getYLength()<=EPS || box.getZLength()<=EPS) continue;
            // Native fences/walls extend beyond their carrier cell. Include their
            // neighboring carriers; exact volume tests still leave empty corners free.
            for (int x=(int)Math.floor(box.minX+EPS)-1;x<(int)Math.ceil(box.maxX-EPS)+1;x++)
                for (int y=(int)Math.floor(box.minY+EPS)-1;y<(int)Math.ceil(box.maxY-EPS)+1;y++)
                    for (int z=(int)Math.floor(box.minZ+EPS)-1;z<(int)Math.ceil(box.maxZ-EPS)+1;z++) {
                        BlockPos pos=new BlockPos(x,y,z);
                        if (!world.isChunkLoaded(pos)) return "unloaded_physical_cell:"+pos.toShortString();
                        BlockState state=world.getBlockState(pos);
                        if (!(excludeOwner!=null&&pos.equals(CompositeData.pos(excludeOwner.root()))&&dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.isNative(state)) && !pos.equals(replacingNative) && !(state.getBlock() instanceof CompositeRootBlock) && !state.isOf(CompositeArchitecture.CELL))
                            for (Box nativeBox : CompositeRuntime.nativeCollision(world,pos,ShapeContext.absent()).getBoundingBoxes())
                                if (overlaps(box,nativeBox.offset(pos))&&!retainedIntersection(box,nativeBox.offset(pos),previous)) return "solid_native_overlap:"+pos.toShortString();
                        for (var contribution : CompositeRuntime.contributions(world,pos)) {
                            if (contribution.owner().equals(excludeOwner)) continue;
                            // Only block placement passes through the roof's protruding volume.
                            // Its real root remains occupied and movement/entity checks remain solid.
                            if(contribution.owner().registryId().equals("bloodborne_dw:prototype_roof")&&!pos.equals(CompositeData.pos(contribution.owner().root())))continue;
                            for (var b : contribution.shape().collision())
                                if (overlaps(box,new Box(x+b.minX(),y+b.minY(),z+b.minZ(),x+b.maxX(),y+b.maxY(),z+b.maxZ()))
                                        &&!retainedIntersection(box,new Box(x+b.minX(),y+b.minY(),z+b.minZ(),x+b.maxX(),y+b.maxY(),z+b.maxZ()),previous))
                                    return "solid_owned_overlap:"+contribution.owner().instanceId();
                        }
                    }
            String entityConflict=entityConflict(world,List.of(box),excludeEntity,false,previous);
            if(entityConflict!=null)return entityConflict;
        }
        return null;
    }
    public static String entityConflict(World world,List<Box> proposed,Entity excludeEntity,boolean initialSourceInstance) {
        return entityConflict(world,proposed,excludeEntity,initialSourceInstance,List.of());
    }
    public static String entityConflict(World world,List<Box> proposed,Entity excludeEntity,boolean initialSourceInstance,List<Box> previous) {
        for(Box box:proposed)for (Entity other : world.getOtherEntities(excludeEntity,box.expand(EPS))) {
                if (other instanceof RpObjectEntity rp) {
                    // Permanent V10 placement policy: RP geometry affects movement only.
                    continue;
                } else if (other.isAlive() && !other.isSpectator() && (other.intersectionChecked||other.isCollidable())
                        &&(excludeEntity==null||!other.isConnectedThroughVehicle(excludeEntity))&&overlaps(box,other.getBoundingBox()))
                    return "entity_obstruction:"+other.getUuid();
            }
        return null;
    }
    /** An existing object may keep exactly its prior intersection, but may not add solid volume there. */
    public static boolean retainedIntersection(Box proposed,Box obstacle,List<Box> previous) {
        if(!overlaps(proposed,obstacle))return true;
        List<Box> remaining=new ArrayList<>();remaining.add(intersection(proposed,obstacle));
        for(Box old:previous){List<Box> next=new ArrayList<>();for(Box piece:remaining){
            if(!overlaps(piece,old)){next.add(piece);continue;}Box cut=intersection(piece,old);
            addPositive(next,new Box(piece.minX,piece.minY,piece.minZ,cut.minX,piece.maxY,piece.maxZ));
            addPositive(next,new Box(cut.maxX,piece.minY,piece.minZ,piece.maxX,piece.maxY,piece.maxZ));
            addPositive(next,new Box(cut.minX,piece.minY,piece.minZ,cut.maxX,cut.minY,piece.maxZ));
            addPositive(next,new Box(cut.minX,cut.maxY,piece.minZ,cut.maxX,piece.maxY,piece.maxZ));
            addPositive(next,new Box(cut.minX,cut.minY,piece.minZ,cut.maxX,cut.maxY,cut.minZ));
            addPositive(next,new Box(cut.minX,cut.minY,cut.maxZ,cut.maxX,cut.maxY,piece.maxZ));
        }remaining=next;if(remaining.isEmpty())return true;if(remaining.size()>4096)return false;}
        return false;
    }
    private static Box intersection(Box a,Box b){return new Box(Math.max(a.minX,b.minX),Math.max(a.minY,b.minY),Math.max(a.minZ,b.minZ),Math.min(a.maxX,b.maxX),Math.min(a.maxY,b.maxY),Math.min(a.maxZ,b.maxZ));}
    private static void addPositive(List<Box> boxes,Box box){if(box.getXLength()>EPS&&box.getYLength()>EPS&&box.getZLength()>EPS)boxes.add(box);}
    /** Owner contributions are cell-clipped; translate only once into world coordinates. */
    public static List<Box> physicalBoxes(dev.dreamwalker.bloodbornedw.runtime.ObjectInstance object) {
        List<Box> boxes=new ArrayList<>();
        for (var entry : object.cells().entrySet()) {
            var cell=object.owner().root().add(entry.getKey());
            for (var b : entry.getValue().collision()) boxes.add(new Box(cell.x()+b.minX(),cell.y()+b.minY(),cell.z()+b.minZ(),cell.x()+b.maxX(),cell.y()+b.maxY(),cell.z()+b.maxZ()));
        }
        return List.copyOf(boxes);
    }
}
