package dev.dreamwalker.bloodbornedw.gametest;

import dev.dreamwalker.bloodbornedw.architecture.BuildingTool;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

/** Exact server tool adapters, not a simulated GUI click. */
public final class ToolGeometryGameTests implements FabricGameTest {
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=120,batchId="tool_fix_geometry")
    public void allThreeGlassTypesReverseCardinalAndMountAwayFromTheSelectionRay(TestContext c){
        var world=c.getWorld();BlockPos origin=c.getAbsolutePos(new BlockPos(3,3,3));BlockPos root=new BlockPos(origin.getX(),150,origin.getZ());
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)world.getChunk(root.add(x*16,0,z*16));
        var player=c.createMockCreativeServerPlayerInWorld();player.setStackInHand(Hand.MAIN_HAND,CompositeArchitecture.BUILDER.getDefaultStack());
        player.setPosition(root.getX()+.5,root.getY()+2,root.getZ()-4);player.setYaw(180);player.setPitch(0);
        for(String path:List.of(GlazingTypes.WINDOW01,GlazingTypes.WINDOW02,GlazingTypes.WINDOW03)){
            var block=CompositeArchitecture.kindBlock(path);var initial=block.getDefaultState().with(CompositeRootBlock.ROTATION,0);
            c.assertTrue(CompositeRuntime.place(world,root,initial,UUID.randomUUID(),null).outcome()==TransactionCore.Outcome.COMMITTED,"glass test setup: "+path);
            var owner=((CompositeBlockEntity)world.getBlockEntity(root)).resident();
            c.assertTrue(BuildingTool.applyBlock(player,root,BuildingTool.Action.ROTATE,true).isAccepted(),"reverse pinned operation commits while player faces away: "+path);
            c.assertTrue(world.getBlockState(root).get(CompositeRootBlock.ROTATION)==6,"reverse glass uses -90 degrees, skips unsupported diagonal: "+path);
            c.assertTrue(BuildingTool.applyBlock(player,root,BuildingTool.Action.ROTATE,false).isAccepted()&&world.getBlockState(root).get(CompositeRootBlock.ROTATION)==0,"opposite turn returns exact original cardinal: "+path);
            var mount=world.getBlockState(root).get(ThinWindowRootBlock.MOUNT);
            for(int i=0;i<3;i++)c.assertTrue(BuildingTool.applyBlock(player,root,BuildingTool.Action.MOUNT,true).isAccepted(),"reverse mount transition commits without reacquiring ray: "+path+"/"+i);
            c.assertTrue(world.getBlockState(root).get(ThinWindowRootBlock.MOUNT)==mount&&owner.equals(((CompositeBlockEntity)world.getBlockEntity(root)).resident()),"all three mounting planes retain type and owner UUID: "+path);
            c.assertTrue(CompositeRuntime.remove(world,owner,null,false).outcome()==TransactionCore.Outcome.COMMITTED,"glass fixture cleaned up");
        }
        player.discard();c.complete();
    }
}
