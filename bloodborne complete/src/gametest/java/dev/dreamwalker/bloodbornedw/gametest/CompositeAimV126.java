package dev.dreamwalker.bloodbornedw.gametest;

import dev.dreamwalker.bloodbornedw.architecture.PlacementPhysics;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import java.util.*;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;

/** Aim with actual world raycasts from six faces. A thin door's mounting
 * plane may face any axis; a north-only probe can be parallel to that plane. */
public final class CompositeAimV126 {
    private CompositeAimV126() {}
    public static BlockHitResult aim(TestContext t,ServerPlayerEntity player,Owner owner,Collection<BlockPos> cells) {
        var world=t.getWorld();player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);
        var root=CompositeData.pos(owner.root());var entity=(CompositeBlockEntity)world.getBlockEntity(root);
        var next=CompositeRuntime.instance(world,root,world.getBlockState(root).cycle(CompositeRootBlock.OPEN),owner.instanceId(),entity.payload());
        var future=PlacementPhysics.physicalBoxes(next);
        for(var pos:cells) {
            if(CompositeRuntime.contributions(world,pos).stream().noneMatch(row->row.owner().equals(owner)))continue;
            var shape=world.getBlockState(pos).getOutlineShape(world,pos,ShapeContext.absent());
            for(var bounds:shape.getBoundingBoxes())for(var face:List.of(Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST,Direction.UP,Direction.DOWN)) {
                var centre=bounds.getCenter().add(Vec3d.of(pos));
                var target=switch(face) {
                    case NORTH -> new Vec3d(centre.x,centre.y,pos.getZ()+bounds.minZ);
                    case SOUTH -> new Vec3d(centre.x,centre.y,pos.getZ()+bounds.maxZ);
                    case WEST -> new Vec3d(pos.getX()+bounds.minX,centre.y,centre.z);
                    case EAST -> new Vec3d(pos.getX()+bounds.maxX,centre.y,centre.z);
                    case UP -> new Vec3d(centre.x,pos.getY()+bounds.maxY,centre.z);
                    case DOWN -> new Vec3d(centre.x,pos.getY()+bounds.minY,centre.z);
                };
                var normal=new Vec3d(face.getOffsetX(),face.getOffsetY(),face.getOffsetZ());
                var eye=target.add(normal.multiply(3.75));var direction=target.subtract(eye).normalize();
                float yaw=(float)(Math.toDegrees(Math.atan2(direction.z,direction.x))-90);
                float pitch=(float)-Math.toDegrees(Math.atan2(direction.y,Math.sqrt(direction.x*direction.x+direction.z*direction.z)));
                player.refreshPositionAndAngles(eye.x,eye.y-player.getStandingEyeHeight(),eye.z,yaw,pitch);
                if(!world.isSpaceEmpty(player,player.getBoundingBox())||future.stream().anyMatch(box->PlacementPhysics.overlaps(player.getBoundingBox(),box)))continue;
                var end=player.getEyePos().add(player.getRotationVec(1).multiply(5));
                var hit=world.raycast(new RaycastContext(player.getEyePos(),end,RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,player));
                if(hit.getType()!=HitResult.Type.BLOCK||!owner.equals(CompositeRuntime.target(world,hit.getBlockPos(),player)))continue;
                var actual=world.getBlockState(hit.getBlockPos()).getOutlineShape(world,hit.getBlockPos(),ShapeContext.of(player)).raycast(player.getEyePos(),end,hit.getBlockPos());
                if(actual!=null&&actual.getPos().squaredDistanceTo(hit.getPos())<1e-8)return hit;
            }
        }
        t.assertTrue(false,"Door has no reachable ordinary aiming part from any of its six physical faces: "+owner.registryId());
        return null;
    }
}
