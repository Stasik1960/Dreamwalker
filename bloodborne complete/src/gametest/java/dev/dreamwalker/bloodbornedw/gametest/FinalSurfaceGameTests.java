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

/** Actual registered item placement on each valid decal mounting face. */
public final class FinalSurfaceGameTests implements FabricGameTest {
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
