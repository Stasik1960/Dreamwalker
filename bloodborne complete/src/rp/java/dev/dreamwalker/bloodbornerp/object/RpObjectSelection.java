package dev.dreamwalker.bloodbornerp.object;

import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Selection tests actual loaded RP parts; source entity origins do not define reach. */
public final class RpObjectSelection {
    private RpObjectSelection() {}
    /** Minecraft1.20.1 GameRenderer: extended Creative entity ray6, other modes3. Block ray is separate. */
    public static double entityReach(PlayerEntity player){return player.isCreative()?6:3;}
    public static Optional<Vec3d> hit(RpObjectEntity object,Vec3d start,Vec3d end){
        Vec3d nearest=null;double distance=Double.POSITIVE_INFINITY;
        for(Box shape:object.interactionBoxes()){
            Box box=shape.expand(object.getTargetingMargin());
            Vec3d point=box.contains(start)?start:box.raycast(start,end).orElse(null);
            if(point!=null&&start.squaredDistanceTo(point)<distance){nearest=point;distance=start.squaredDistanceTo(point);}
        }
        return Optional.ofNullable(nearest);
    }
    public static EntityHitResult raycast(Entity viewer,Vec3d start,Vec3d end,Box query,Predicate<Entity> predicate,double maximumSquared,EntityHitResult nativeHit){
        EntityHitResult selected=nativeHit;double limit=nativeHit==null?maximumSquared:Math.min(maximumSquared,start.squaredDistanceTo(nativeHit.getPos()));
        for(RpObjectEntity object:RpObjectIndex.in(viewer.getWorld(),query)){
            if(object==viewer||!object.canHit()||!predicate.test(object))continue;
            Vec3d point=hit(object,start,end).orElse(null);if(point==null)continue;double distance=start.squaredDistanceTo(point);
            if(distance>limit||object.getRootVehicle()==viewer.getRootVehicle()&&distance!=0)continue;
            limit=distance;selected=new EntityHitResult(object,point);
        }
        return selected;
    }
    public static EntityHitResult playerTarget(PlayerEntity player,double reach){
        // Server interaction validates the authoritative view yaw, not interpolated head animation.
        Vec3d start=player.getEyePos(),end=start.add(player.getRotationVector().multiply(reach));
        var obstruction=player.getWorld().raycast(new net.minecraft.world.RaycastContext(start,end,
            net.minecraft.world.RaycastContext.ShapeType.OUTLINE,net.minecraft.world.RaycastContext.FluidHandling.NONE,player));
        double limit=Math.min(reach*reach,start.squaredDistanceTo(obstruction.getPos()));
        return raycast(player,start,end,new Box(start,end).expand(1),e->!e.isSpectator()&&e.canHit(),limit,null);
    }
    public static Vec3d nearestPoint(RpObjectEntity object,Vec3d point){
        Vec3d selected=null;double distance=Double.POSITIVE_INFINITY;
        for(Box box:object.interactionBoxes()){
            Vec3d candidate=new Vec3d(net.minecraft.util.math.MathHelper.clamp(point.x,box.minX,box.maxX),net.minecraft.util.math.MathHelper.clamp(point.y,box.minY,box.maxY),net.minecraft.util.math.MathHelper.clamp(point.z,box.minZ,box.maxZ));
            if(point.squaredDistanceTo(candidate)<distance){selected=candidate;distance=point.squaredDistanceTo(candidate);}
        }
        return selected;
    }
}
