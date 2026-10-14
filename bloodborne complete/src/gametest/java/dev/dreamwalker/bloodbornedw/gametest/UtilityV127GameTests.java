package dev.dreamwalker.bloodbornedw.gametest;

import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornedw.architecture.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.*;
import net.minecraft.network.*;
import net.minecraft.network.packet.Packet;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.*;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;

public final class UtilityV127GameTests implements FabricGameTest {
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="final_utility_water",tickLimit=140)
    public void playerOnlyBarrierSurvivesRealWaterFlowAndKeepsBlockAttachments(TestContext c){
        var world=c.getWorld();var root=c.getAbsolutePos(new BlockPos(3,12,3));load(world,root,5);
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)world.setBlockState(root.add(x,-1,z),Blocks.STONE.getDefaultState());
        world.setBlockState(root,DwUtilityBlocks.BARRIER.getDefaultState());
        var state=world.getBlockState(root);
        c.assertTrue(state.getSidesShape(world,root).equals(net.minecraft.util.shape.VoxelShapes.fullCube()),"Invisible player barrier keeps native full side support for attachments");
        c.assertTrue(state.getCollisionShape(world,root,ShapeContext.absent()).isEmpty(),"Fluid solidity does not introduce non-player collision");
        world.setBlockState(root.up(),Blocks.WATER.getDefaultState());
        c.runAtTick(100,()->{
            c.assertTrue(world.getBlockState(root).isOf(DwUtilityBlocks.BARRIER),"Actual scheduled water flow cannot replace the player-only barrier");
            for(int x=-2;x<=2;x++)for(int y=0;y<=1;y++)for(int z=-2;z<=2;z++)world.removeBlock(root.add(x,y,z),false);
            c.complete();
        });
    }
    public static ServerPlayerEntity player(ServerWorld world){
        var player=new ServerPlayerEntity(world.getServer(),world,new GameProfile(UUID.randomUUID(),"Utility127"));
        var connection=new ClientConnection(NetworkSide.SERVERBOUND){@Override public void send(Packet<?> packet){}@Override public void send(Packet<?> packet,PacketCallbacks callbacks){}};
        player.networkHandler=new ServerPlayNetworkHandler(world.getServer(),connection,player);player.changeGameMode(GameMode.CREATIVE);player.getAbilities().allowModifyWorld=true;return player;
    }
    public static void load(ServerWorld world,BlockPos pos,int radius){for(int x=(pos.getX()-radius)>>4;x<=((pos.getX()+radius)>>4);x++)for(int z=(pos.getZ()-radius)>>4;z<=((pos.getZ()+radius)>>4);z++){world.setChunkForced(x,z,true);world.getChunk(x,z);}}
    public static ActionResult item(ServerWorld world,ServerPlayerEntity player,ItemStack stack,BlockPos root){
        player.setStackInHand(Hand.MAIN_HAND,stack);player.setYaw(0);
        return stack.useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,new BlockHitResult(new Vec3d(root.getX()+.5,root.getY(),root.getZ()+.5),Direction.UP,root.down(),false)));
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="v127_utilities",tickLimit=80)
    public void barrierBlocksOnlyRealPlayerMovement(TestContext c){var world=c.getWorld();var root=c.getAbsolutePos(new BlockPos(3,4,3));load(world,root,5);var p=player(world);world.setBlockState(root,DwUtilityBlocks.BARRIER.getDefaultState());
        c.assertTrue(!world.getBlockState(root).getCollisionShape(world,root,ShapeContext.of(p)).isEmpty()&&world.getBlockState(root).getCollisionShape(world,root,ShapeContext.absent()).isEmpty(),"Player-only contextual shape");
        p.setPosition(root.getX()-.6,root.getY(),root.getZ()+.5);p.move(MovementType.SELF,new Vec3d(1.8,0,0));c.assertTrue(p.getX()<root.getX(),"Real player cannot cross yellow barrier");
        var zombie=new ZombieEntity(world);zombie.setPosition(root.getX()-.6,root.getY(),root.getZ()+.5);zombie.move(MovementType.SELF,new Vec3d(1.8,0,0));c.assertTrue(zombie.getX()>root.getX()+1&&world.getBlockState(root).getCollisionShape(world,root,ShapeContext.of(zombie)).isEmpty(),"Mob crosses the same block");
        world.setBlockState(root,Blocks.AIR.getDefaultState());c.complete();}
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="v127_utilities",tickLimit=80)
    public void invisibleLightIsReplaceableAndHasTorchLuminance(TestContext c){var world=c.getWorld();var root=c.getAbsolutePos(new BlockPos(3,4,3));load(world,root,5);var p=player(world);world.setBlockState(root.down(),Blocks.STONE.getDefaultState());
        c.assertTrue(item(world,p,new ItemStack(DwUtilityBlocks.LIGHT_ITEM),root).isAccepted(),"Ordinary light item placement");var state=world.getBlockState(root);c.assertTrue(state.isOf(DwUtilityBlocks.LIGHT)&&state.getLuminance()==14&&state.getCollisionShape(world,root,ShapeContext.of(p)).isEmpty(),"Torch brightness and no player obstacle");
        p.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);c.assertTrue(state.getOutlineShape(world,root,ShapeContext.of(p)).isEmpty(),"Unheld light invisible to aiming");p.setStackInHand(Hand.MAIN_HAND,new ItemStack(DwUtilityBlocks.LIGHT_ITEM));c.assertTrue(!state.getOutlineShape(world,root,ShapeContext.of(p)).isEmpty(),"Held light can be selected");
        p.setStackInHand(Hand.MAIN_HAND,new ItemStack(dev.dreamwalker.bloodbornedw.tool.BbToolService.ITEM));c.assertTrue(!state.getOutlineShape(world,root,ShapeContext.of(p)).isEmpty(),"Debug tool can aim at the invisible light without showing its held-item particle marker");
        p.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.STONE));var action=p.getMainHandStack().useOnBlock(new ItemUsageContext(p,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(root),Direction.UP,root,false)));c.assertTrue(action.isAccepted()&&world.getBlockState(root).isOf(Blocks.STONE),"Vanilla block replaces light inside its cell");world.setBlockState(root,Blocks.AIR.getDefaultState());c.complete();}
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="v127_utilities",tickLimit=80)
    public void noCollidePersistsForTheActualHeldStackAndNeverAnItemTag(TestContext c){var world=c.getWorld();var p=player(world);
        p.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);c.assertTrue(world.getServer().getCommandManager().executeWithPrefix(p.getCommandSource().withLevel(2),"bb no-collide")==0,"Real command rejects an empty hand");
        p.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.STONE));c.assertTrue(world.getServer().getCommandManager().executeWithPrefix(p.getCommandSource().withLevel(2),"bb no-collide")==0,"Real command rejects a vanilla block");
        for(var marker:List.of(DwUtilityBlocks.LIGHT_ITEM,DwUtilityBlocks.BARRIER_ITEM)){p.setStackInHand(Hand.MAIN_HAND,new ItemStack(marker));c.assertTrue(world.getServer().getCommandManager().executeWithPrefix(p.getCommandSource().withLevel(2),"bb no-collide")==0&&!NoCollidePlacement.armed(p),"Markers have permanent placement policy and cannot obtain a held lease");}
        p.setStackInHand(Hand.MAIN_HAND,new ItemStack(CompositeArchitecture.kindItem("source_bricks")));c.assertTrue(world.getServer().getCommandManager().executeWithPrefix(p.getCommandSource().withLevel(2),"bb no-collide")==1&&NoCollidePlacement.armed(p),"Real command grants an ordinary held mod item its transient lease");NoCollidePlacement.invalidate(p);
        p.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);c.assertTrue(!NoCollidePlacement.arm(p),"Empty hand rejected");p.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.STONE));c.assertTrue(!NoCollidePlacement.arm(p),"Vanilla rejected");
        var stack=new ItemStack(CompositeArchitecture.kindItem("source_bricks"),8);p.setStackInHand(Hand.MAIN_HAND,stack);c.assertTrue(NoCollidePlacement.arm(p)&&NoCollidePlacement.armed(p)&&!stack.hasNbt(),"Transient actual-stack lease");var copy=stack.copy();p.setStackInHand(Hand.MAIN_HAND,copy);c.assertTrue(!NoCollidePlacement.armed(p)&&!copy.hasNbt(),"Moving/copying object loses lease");
        p.setStackInHand(Hand.MAIN_HAND,stack);c.assertTrue(!NoCollidePlacement.armed(p),"Returning a removed stack cannot revive an invalidated lease");NoCollidePlacement.arm(p);NoCollidePlacement.invalidate(p);c.assertTrue(!NoCollidePlacement.armed(p),"Explicit invalidation");NoCollidePlacement.arm(p);boolean entered=NoCollidePlacement.enter(p);c.assertTrue(entered&&NoCollidePlacement.ignoringBlocks(),"Scoped placement");NoCollidePlacement.exit(p,entered,true);c.assertTrue(NoCollidePlacement.armed(p)&&!NoCollidePlacement.ignoringBlocks(),"Successful placement retains permission while the same stack remains held and closes only the execution scope");stack.decrement(1);c.assertTrue(NoCollidePlacement.armed(p),"Survival count decrease retains the same nonempty stack");p.getInventory().selectedSlot=(p.getInventory().selectedSlot+1)%9;c.assertTrue(!NoCollidePlacement.armed(p),"Changing the held slot revokes the lease");c.complete();}
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="v127_utilities",tickLimit=120)
    public void forcedIntersectionKeepsPhysicsAndNormalReplacementRejects(TestContext c){var world=c.getWorld();var p=player(world);var root=c.getAbsolutePos(new BlockPos(3,12,3));load(world,root,24);
        var block=CompositeArchitecture.kindBlock("source_1_spirelamp_001");var second=root.east();var initial=CompositeRuntime.place(world,root,block.getDefaultState(),UUID.randomUUID(),p);c.assertTrue(initial.outcome()==TransactionCore.Outcome.COMMITTED,"Ordinary first wide object");
        var firstOwner=((CompositeBlockEntity)world.getBlockEntity(root)).resident();var stack=new ItemStack(block.asItem());p.setStackInHand(Hand.MAIN_HAND,stack);NoCollidePlacement.arm(p);
        world.setBlockState(second.down(),Blocks.STONE.getDefaultState());var usage=new ItemUsageContext(p,Hand.MAIN_HAND,new BlockHitResult(new Vec3d(second.getX()+.5,second.getY(),second.getZ()+.5),Direction.UP,second.down(),false));
        c.assertTrue(new ItemPlacementContext(usage).getBlockPos().equals(second),"Armed lease is visible before vanilla constructs the placement context");
        var action=stack.useOnBlock(usage);c.assertTrue(action.isAccepted(),"Real item-use pipeline forces overlapping second installation");var owner=((CompositeBlockEntity)world.getBlockEntity(second)).resident();
        c.assertTrue(!world.getBlockState(second).getCollisionShape(world,second,ShapeContext.of(p)).isEmpty()&&NoCollidePlacement.armed(p)&&!stack.getOrCreateNbt().contains("NoCollide"),"Physics unchanged; permission stays on the actual held stack with no saved privilege");
        var picked=world.getBlockState(second).getBlock().getPickStack(world,second,world.getBlockState(second));c.assertTrue(!picked.getOrCreateNbt().contains("NoCollide"),"Creative pick ordinary");c.assertTrue(CompositeRuntime.remove(world,owner,p,false).outcome()==TransactionCore.Outcome.COMMITTED,"Whole forced object breaks normally");
        var ordinary=CompositeRuntime.place(world,second,block.getDefaultState(),UUID.randomUUID(),p);c.assertTrue(ordinary.outcome()==TransactionCore.Outcome.REJECTED,"Ordinary reconstruction remains blocked by actual overlap");NoCollidePlacement.invalidate(p);CompositeRuntime.remove(world,firstOwner,p,false);world.setBlockState(second.down(),Blocks.AIR.getDefaultState());p.discard();c.complete();}
}
