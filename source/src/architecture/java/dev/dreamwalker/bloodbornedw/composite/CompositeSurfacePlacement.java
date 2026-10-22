package dev.dreamwalker.bloodbornedw.composite;

import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.nbt.*;
import net.minecraft.util.math.Direction;
import dev.dreamwalker.bloodbornedw.architecture.NoCollidePlacement;

/** Reviewed decal placement and held no-collide surface anchoring. Saved source map anchors stay exact. */
final class CompositeSurfacePlacement {
    record Placement(BlockState state,NbtCompound payload){}
    private static final double GAP=1./1024;
    static Placement align(CompositeSpec spec,BlockState state,NbtCompound payload,ItemPlacementContext context){
        var decal=spec.surfaceDecals.get(CompositeSpec.poseVariant(state));
        if(decal==null)return alignHeld(spec,state,payload,context);
        Direction face=context.getSide();boolean horizontal=face.getAxis()==Direction.Axis.Y;
        // No pitch is introduced: a floor decal remains a floor decal, wall art remains wall art.
        if(horizontal!=(decal.axis()==1))return alignHeld(spec,state,payload,context);
        if(horizontal&&decal.normal()!=face.getOffsetY())return alignHeld(spec,state,payload,context);
        int yaw=state.get(CompositeRootBlock.ROTATION);
        if(!horizontal){
            double best=-2;
            for(int candidate=0;candidate<8;candidate+=2){
                var normal=new CompositeSourceShift.Shift(decal.axis()==0?decal.normal():0,0,decal.axis()==2?decal.normal():0).rotate(candidate);
                double dot=normal.x()*face.getOffsetX()+normal.z()*face.getOffsetZ();if(dot>best){best=dot;yaw=candidate;}
            }
            state=state.with(CompositeRootBlock.ROTATION,yaw);
        }
        payload=payload==null?new NbtCompound():payload;
        var local=CompositeSourceShift.local(payload);var world=local.rotate(yaw);
        var plane=new CompositeSourceShift.Shift(decal.axis()==0?decal.value()-.5:0,decal.axis()==1?decal.value():0,decal.axis()==2?decal.value()-.5:0).rotate(yaw);
        double value=horizontal?plane.y()+world.y():face.getAxis()==Direction.Axis.X?.5+plane.x()+world.x():.5+plane.z()+world.z();
        double target=face.getDirection()==Direction.AxisDirection.POSITIVE?GAP:1-GAP;
        if(NoCollidePlacement.armed(context.getPlayer()))target=hitCoordinate(context)+(face.getDirection()==Direction.AxisDirection.POSITIVE?GAP:-GAP);
        double delta=target-value;
        var change=new CompositeSourceShift.Shift(face.getAxis()==Direction.Axis.X?delta:0,horizontal?delta:0,face.getAxis()==Direction.Axis.Z?delta:0).rotate(-yaw);
        var shift=new NbtList();shift.add(NbtDouble.of(local.x()+change.x()));shift.add(NbtDouble.of(local.y()+change.y()));shift.add(NbtDouble.of(local.z()+change.z()));payload.put("SourceShift",shift);
        return new Placement(state,payload);
    }
    private static Placement alignHeld(CompositeSpec spec,BlockState state,NbtCompound payload,ItemPlacementContext context){
        if(!NoCollidePlacement.armed(context.getPlayer())||state.getBlock() instanceof ThinWindowRootBlock)return new Placement(state,payload);
        var face=context.getSide();var bounds=spec.variant(state).visible();
        java.util.List<CompositeSpec.Bounds> boxes=bounds!=null&&!state.get(CompositeRootBlock.OPEN)?java.util.List.of(bounds):spec.pose(state).selection();
        if(boxes.isEmpty())return new Placement(state,payload);
        payload=payload==null?new NbtCompound():payload;
        int yaw=state.get(CompositeRootBlock.ROTATION);var local=CompositeSourceShift.local(payload);var world=local.rotate(yaw);
        double mount=0;
        if(spec.requiredSupport){var support=spec.supportOffset;Double top=CompositeRuntime.topHeight(context.getWorld(),context.getBlockPos().add(support.x(),support.y(),support.z()),null);if(top!=null)mount=top-1;}
        boolean positive=face.getDirection()==Direction.AxisDirection.POSITIVE;double boundary=positive?Double.POSITIVE_INFINITY:Double.NEGATIVE_INFINITY;
        for(var box:boxes)for(double x:new double[]{box.from().x(),box.to().x()})for(double y:new double[]{box.from().y(),box.to().y()})for(double z:new double[]{box.from().z(),box.to().z()}){
            var point=new CompositeSourceShift.Shift(x/16-.5,y/16,z/16-.5).rotate(yaw);
            double coordinate=switch(face.getAxis()){case X->.5+point.x()+world.x();case Y->point.y()+world.y()+mount;case Z->.5+point.z()+world.z();};
            boundary=positive?Math.min(boundary,coordinate):Math.max(boundary,coordinate);
        }
        double delta=hitCoordinate(context)-boundary;
        var change=new CompositeSourceShift.Shift(face.getAxis()==Direction.Axis.X?delta:0,face.getAxis()==Direction.Axis.Y?delta:0,face.getAxis()==Direction.Axis.Z?delta:0).rotate(-yaw);
        var shift=new NbtList();shift.add(NbtDouble.of(local.x()+change.x()));shift.add(NbtDouble.of(local.y()+change.y()));shift.add(NbtDouble.of(local.z()+change.z()));payload.put("SourceShift",shift);
        return new Placement(state,payload);
    }
    private static double hitCoordinate(ItemPlacementContext context){var hit=context.getHitPos();var root=context.getBlockPos();return switch(context.getSide().getAxis()){case X->hit.x-root.getX();case Y->hit.y-root.getY();case Z->hit.z-root.getZ();};}
    private CompositeSurfacePlacement(){}
}
