package dev.dreamwalker.bloodbornedw.gametest;

import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.test.*;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import dev.dreamwalker.bloodbornedw.architecture.*;

/** Actual registered item placement on each valid decal mounting face. */
public final class FinalSurfaceGameTests implements FabricGameTest {
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="v1274_held_glazing_surface",tickLimit=100)
    public void heldGlazingUsesExactFractionalSlabAndChestRaySurfaces(TestContext c){
        var world=c.getWorld();var player=UtilityV127GameTests.player(world);var base=c.getAbsolutePos(new BlockPos(21200,96,256));UtilityV127GameTests.load(world,base,48);
        var block=CompositeArchitecture.kindBlock(GlazingTypes.WINDOW01);var owners=new ArrayList<dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner>();
        try{
            int index=0;
            for(var face:List.of(Direction.UP,Direction.EAST)){
                var support=base.east(index++*8);world.setBlockState(support,face==Direction.UP?Blocks.STONE_SLAB.getDefaultState():Blocks.CHEST.getDefaultState());
                var oldEntity=world.getBlockEntity(support);var oldNbt=oldEntity==null?null:oldEntity.createNbtWithIdentifyingData();
                var stack=new ItemStack(block.asItem());player.setStackInHand(Hand.MAIN_HAND,stack);c.assertTrue(NoCollidePlacement.arm(player),"Exact held glazing stack can be armed");
                var point=face==Direction.UP?new Vec3d(support.getX()+.5,support.getY()+.5,support.getZ()+.5):new Vec3d(support.getX()+15./16,support.getY()+.5,support.getZ()+.5);
                player.refreshPositionAndAngles(point.x+5,point.y+8,point.z-4,0,0);
                var hit=world.raycast(new RaycastContext(point.add(face.getOffsetX()*4,face.getOffsetY()*4,face.getOffsetZ()*4),point.add(-face.getOffsetX()*.01,-face.getOffsetY()*.01,-face.getOffsetZ()*.01),RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,player));
                c.assertTrue(hit.getBlockPos().equals(support)&&hit.getSide()==face&&hit.getPos().squaredDistanceTo(point)<1e-12,"Native ray reaches the real fractional glazing support surface");
                c.assertTrue(stack.useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit)).isAccepted(),"Held glazing places on the fractional "+face+" face");
                var anchor=NoCollidePlacement.surfaceAnchor(new ItemPlacementContext(new ItemUsageContext(player,Hand.MAIN_HAND,hit)));var entity=(CompositeBlockEntity)world.getBlockEntity(anchor);owners.add(entity.resident());var object=CompositeRuntime.instance(world,anchor,world.getBlockState(anchor),entity.resident().instanceId(),entity.payload());
                double actual=PlacementPhysics.physicalBoxes(object).stream().mapToDouble(box->face==Direction.UP?box.minY:box.minX).min().orElseThrow();double expected=face==Direction.UP?point.y:point.x;
                c.assertTrue(Math.abs(actual-expected)<1e-7,"Glazing renderer and physical base use the actual clicked surface rather than a different helper top or the cell boundary");
                c.assertTrue(oldNbt==null||oldNbt.equals(world.getBlockEntity(support).createNbtWithIdentifyingData()),"Native chest typed data survives the overlapping guest binding unchanged");
                NoCollidePlacement.invalidate(player);
            }
            c.complete();
        }finally{NoCollidePlacement.invalidate(player);for(var owner:owners)CompositeRuntime.remove(world,owner,player,false);CompositeRuntime.drain(world);for(int i=0;i<2;i++)world.setBlockState(base.east(i*8),Blocks.AIR.getDefaultState());player.discard();}
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="v1274_held_surface",tickLimit=100)
    public void heldOverlapUsesRealHitSurfaceAndKeepsPermissionAcrossPlacements(TestContext c){
        var world=c.getWorld();var player=UtilityV127GameTests.player(world);var base=c.getAbsolutePos(new BlockPos(18000,96,256));UtilityV127GameTests.load(world,base,64);
        var block=CompositeArchitecture.kindBlock("source_1_spirelamp_001");var stack=new ItemStack(block.asItem(),8);player.setStackInHand(Hand.MAIN_HAND,stack);
        c.assertTrue(world.getServer().getCommandManager().executeWithPrefix(player.getCommandSource().withLevel(2),"bb no-collide")==1,"Actual command enables a held overlap item");
        var owners=new ArrayList<dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner>();var supports=new ArrayList<BlockPos>();
        try{
            int checked=0;
            for(Direction face:Direction.values()){
                var support=base.east(checked*8);supports.add(support);world.setBlockState(support,face==Direction.UP?Blocks.STONE_SLAB.getDefaultState():Blocks.STONE.getDefaultState());
                double height=face==Direction.UP?.5:1;var point=face==Direction.UP?new Vec3d(support.getX()+.5,support.getY()+height,support.getZ()+.5):Vec3d.ofCenter(support).add(face.getOffsetX()*.5,face.getOffsetY()*.5,face.getOffsetZ()*.5);
                player.refreshPositionAndAngles(point.x+face.getOffsetX()*5,point.y+8,point.z+face.getOffsetZ()*5,0,0);
                var start=point.add(face.getOffsetX()*4,face.getOffsetY()*4,face.getOffsetZ()*4);var end=point.add(-face.getOffsetX()*.01,-face.getOffsetY()*.01,-face.getOffsetZ()*.01);
                var hit=world.raycast(new RaycastContext(start,end,RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,player));
                c.assertTrue(hit.getBlockPos().equals(support)&&hit.getSide()==face&&hit.getPos().squaredDistanceTo(point)<1e-12,"Actual outline ray locates the intended "+face+" support face");
                c.assertTrue(stack.useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit)).isAccepted(),"Held item places flush at the actual "+face+" ray contact");
                var anchor=NoCollidePlacement.surfaceAnchor(new ItemPlacementContext(new ItemUsageContext(player,Hand.MAIN_HAND,hit)));var entity=(CompositeBlockEntity)world.getBlockEntity(anchor);owners.add(entity.resident());
                var object=CompositeRuntime.instance(world,anchor,world.getBlockState(anchor),entity.resident().instanceId(),entity.payload());var boxes=PlacementPhysics.physicalBoxes(object);boolean positive=face.getDirection()==Direction.AxisDirection.POSITIVE;
                double boundary=positive?Double.POSITIVE_INFINITY:Double.NEGATIVE_INFINITY;
                for(var box:boxes){double value=switch(face.getAxis()){case X->positive?box.minX:box.maxX;case Y->positive?box.minY:box.maxY;case Z->positive?box.minZ:box.maxZ;};boundary=positive?Math.min(boundary,value):Math.max(boundary,value);}
                double contact=switch(face.getAxis()){case X->point.x;case Y->point.y;case Z->point.z;};
                c.assertTrue(Math.abs(boundary-contact)<1e-7,"Actual placed physical base/boundary touches the ray surface instead of sinking into the helper or floating above a half slab");
                c.assertTrue(NoCollidePlacement.armed(player)&&!NoCollidePlacement.ignoringBlocks()&&!stack.getOrCreateNbt().contains("NoCollide"),"The same held stack retains its unsaved lease after successful placement "+checked);
                c.assertTrue(world.getBlockState(support).isOf(face==Direction.UP?Blocks.STONE_SLAB:Blocks.STONE),"Original support remains intact");checked++;
            }
            c.assertTrue(checked==6,"All actual cardinal ray faces, including a fractional slab top, are checked");c.complete();
        }finally{NoCollidePlacement.invalidate(player);for(var owner:owners)CompositeRuntime.remove(world,owner,player,false);CompositeRuntime.drain(world);for(var support:supports)world.setBlockState(support,Blocks.AIR.getDefaultState());player.discard();}
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="v1273_surface_decal",tickLimit=100)
    public void reviewedPlanesStandOffFromSupportAndPreserveOriginalSourceCoordinates(TestContext c){
        var world=c.getWorld();var player=UtilityV127GameTests.player(world);var base=c.getAbsolutePos(new BlockPos(16400,96,256));UtilityV127GameTests.load(world,base,48);
        int checked=0;var owners=new ArrayList<dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner>();
        try{
            for(Direction side:Direction.values()){
                if(side==Direction.DOWN)continue; // Reviewed floor decals face upwards in the source pack.
                boolean floor=side.getAxis()==Direction.Axis.Y;
                var block=CompositeArchitecture.kindBlock(floor?"source_addon_button_10":"source_acacia_fence_gate");int variant=floor?0:6;
                var root=base.east(checked*6);var support=root.offset(side.getOpposite());world.setBlockState(support,Blocks.STONE.getDefaultState());
                player.refreshPositionAndAngles(root.getX()+.5,root.getY()+8,root.getZ()-8,135,0);
                var stack=new ItemStack(block.asItem());stack.getOrCreateSubNbt("BlockStateTag").putString("variant",Integer.toString(variant));player.setStackInHand(Hand.MAIN_HAND,stack);
                var click=Vec3d.ofCenter(support).add(side.getOffsetX()*.5,side.getOffsetY()*.5,side.getOffsetZ()*.5);
                c.assertTrue(stack.useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,new BlockHitResult(click,side,support,false))).isAccepted(),"Real decal item installs against "+side);
                c.assertTrue(world.getBlockEntity(root) instanceof CompositeBlockEntity,"Native context selected the adjacent support cell");var entity=(CompositeBlockEntity)world.getBlockEntity(root);owners.add(entity.resident());
                var state=world.getBlockState(root);var decal=block.spec.surfaceDecals.get(variant);var shift=CompositeSourceShift.world(state,entity.payload());
                var plane=new CompositeSourceShift.Shift(decal.axis()==0?decal.value()-.5:0,decal.axis()==1?decal.value():0,decal.axis()==2?decal.value()-.5:0).rotate(state.get(CompositeRootBlock.ROTATION));
                double coordinate=floor?plane.y()+shift.y():side.getAxis()==Direction.Axis.X?.5+plane.x()+shift.x():.5+plane.z()+shift.z();
                double expected=side.getDirection()==Direction.AxisDirection.POSITIVE?1./1024:1-1./1024;
                c.assertTrue(Math.abs(coordinate-expected)<1e-9,"Rendered plane has a real1/1024-block gap instead of a coplanar support face");
                c.assertTrue(world.getBlockState(support).isOf(Blocks.STONE),"Support block is preserved");checked++;
                c.assertTrue(CompositeRuntime.remove(world,entity.resident(),player,false).outcome()==TransactionCore.Outcome.COMMITTED,"Surface object removes as a whole owner");world.setBlockState(support,Blocks.AIR.getDefaultState());
            }
            c.assertTrue(checked==5,"The authored floor face and four wall faces are checked through native item usage");c.complete();
        }finally{for(var owner:owners)if(world.getBlockEntity(CompositeData.pos(owner.root())) instanceof CompositeBlockEntity entity&&owner.equals(entity.resident()))CompositeRuntime.remove(world,owner,player,false);CompositeRuntime.drain(world);player.discard();}
    }
}
