package dev.dreamwalker.bloodbornedw.composite;

import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.nbt.*;
import net.minecraft.util.math.Direction;

/** Hand placement of reviewed zero-thickness decals only. Saved source map anchors stay exact. */
final class CompositeSurfacePlacement {
    record Placement(BlockState state,NbtCompound payload){}
    private static final double GAP=1./1024;
    static Placement align(CompositeSpec spec,BlockState state,NbtCompound payload,ItemPlacementContext context){
        var decal=spec.surfaceDecals.get(CompositeSpec.poseVariant(state));
        if(decal==null)return new Placement(state,payload);
        Direction face=context.getSide();boolean horizontal=face.getAxis()==Direction.Axis.Y;
        // No pitch is introduced: a floor decal remains a floor decal, wall art remains wall art.
        if(horizontal!=(decal.axis()==1))return new Placement(state,payload);
        if(horizontal&&decal.normal()!=face.getOffsetY())return new Placement(state,payload);
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
        double target=face.getDirection()==Direction.AxisDirection.POSITIVE?GAP:1-GAP,delta=target-value;
        var change=new CompositeSourceShift.Shift(face.getAxis()==Direction.Axis.X?delta:0,horizontal?delta:0,face.getAxis()==Direction.Axis.Z?delta:0).rotate(-yaw);
        var shift=new NbtList();shift.add(NbtDouble.of(local.x()+change.x()));shift.add(NbtDouble.of(local.y()+change.y()));shift.add(NbtDouble.of(local.z()+change.z()));payload.put("SourceShift",shift);
        return new Placement(state,payload);
    }
    private CompositeSurfacePlacement(){}
}
