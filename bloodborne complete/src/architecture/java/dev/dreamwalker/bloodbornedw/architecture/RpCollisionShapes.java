package dev.dreamwalker.bloodbornedw.architecture;

import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import dev.dreamwalker.bloodbornerp.object.RpObjectIndex;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.EntityView;

/** Add actual loaded RP working volumes without replacing native collision resolution. */
public final class RpCollisionShapes {
    private RpCollisionShapes() {}

    /** Preserve the original list and shape objects; add each new exact box once. */
    public static List<VoxelShape> append(EntityView view, Entity mover, Box query, List<VoxelShape> original) {
        net.minecraft.world.World world=view instanceof net.minecraft.world.World value?value:null;
        long started=world==null?0:dev.dreamwalker.bloodbornedw.diagnostics.ArchitectureDiagnostics.begin(world,true);
        try{return appendInternal(view,mover,query,original);}
        finally{if(started!=0)dev.dreamwalker.bloodbornedw.diagnostics.ArchitectureDiagnostics.finish(world,null,mover==null?null:mover.getBlockPos(),"rp.collision_index_and_shared_working_volumes",started);}
    }
    private static List<VoxelShape> appendInternal(EntityView view,Entity mover,Box query,List<VoxelShape> original){
        List<VoxelShape> result = null;
        Set<Box> seen = null;
        for (RpObjectEntity object : RpObjectIndex.in(view, query)) {
            if (object == mover) continue;
            for (Box physical : object.activePhysicalBoxes()) {
                if (!PlacementPhysics.overlaps(query, physical)) continue;
                if (seen == null) {
                    seen = new HashSet<>();
                    for (VoxelShape shape : original) seen.addAll(shape.getBoundingBoxes());
                }
                if (!seen.add(physical)) continue;
                if (result == null) result = new ArrayList<>(original);
                result.add(VoxelShapes.cuboid(physical));
            }
        }
        return result == null ? original : List.copyOf(result);
    }

    /** The engine reuses this list for ordinary movement and step-up alternatives. */
    public static List<VoxelShape> forMovement(Entity mover, Vec3d movement, List<VoxelShape> original) {
        Box body = mover.getBoundingBox();
        Box query = body.stretch(movement);
        double step = mover.getStepHeight();
        if (step > 0) query = query.union(body.stretch(movement.x, Math.max(movement.y, step), movement.z));
        return append(mover.getWorld(), mover, query, original);
    }
}
