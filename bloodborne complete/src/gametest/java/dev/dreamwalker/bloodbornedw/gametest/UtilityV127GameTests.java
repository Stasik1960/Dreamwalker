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
    public void noCollideIsOneUseAndNeverAnItemTag(TestContext c){var world=c.getWorld();var p=player(world);
        p.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);c.assertTrue(!NoCollidePlacement.arm(p),"Empty hand rejected");p.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.STONE));c.assertTrue(!NoCollidePlacement.arm(p),"Vanilla rejected");
        var stack=new ItemStack(DwUtilityBlocks.LIGHT_ITEM,8);p.setStackInHand(Hand.MAIN_HAND,stack);c.assertTrue(NoCollidePlacement.arm(p)&&NoCollidePlacement.armed(p)&&!stack.hasNbt(),"Transient actual-stack lease");var copy=stack.copy();p.setStackInHand(Hand.MAIN_HAND,copy);c.assertTrue(!NoCollidePlacement.armed(p)&&!copy.hasNbt(),"Moving/copying object loses lease");
        p.setStackInHand(Hand.MAIN_HAND,stack);NoCollidePlacement.arm(p);NoCollidePlacement.invalidate(p);c.assertTrue(!NoCollidePlacement.armed(p),"Inventory packet invalidation");NoCollidePlacement.arm(p);boolean entered=NoCollidePlacement.enter(p);c.assertTrue(entered&&NoCollidePlacement.ignoringBlocks(),"Scoped placement");NoCollidePlacement.exit(p,entered,true);c.assertTrue(!NoCollidePlacement.armed(p)&&!NoCollidePlacement.ignoringBlocks(),"Success consumes lease and closes scope");c.complete();}
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="v127_utilities",tickLimit=120)
    public void forcedIntersectionKeepsPhysicsAndNormalReplacementRejects(TestContext c){var world=c.getWorld();var p=player(world);var root=c.getAbsolutePos(new BlockPos(3,12,3));load(world,root,24);
        CompositeRootBlock chosen=null;net.minecraft.util.math.BlockPos offset=null;
        for(var block:CompositeArchitecture.blocks()){if(!block.spec.simplePhysicalCollision||block.spec.requiredSupport)continue;var state=block.getDefaultState();for(var entry:block.spec.footprint(state,0).entrySet())if(!entry.getKey().equals(dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell.ORIGIN)&&!entry.getValue().collision().isEmpty()){chosen=block;offset=new BlockPos(entry.getKey().x(),entry.getKey().y(),entry.getKey().z());break;}if(chosen!=null)break;}
        c.assertTrue(chosen!=null,"NEW wide object exists");var block=chosen;var second=root.add(offset);var initial=CompositeRuntime.place(world,root,block.getDefaultState(),UUID.randomUUID(),p);c.assertTrue(initial.outcome()==TransactionCore.Outcome.COMMITTED,"Ordinary first wide object");
        var firstOwner=((CompositeBlockEntity)world.getBlockEntity(root)).resident();var stack=new ItemStack(block.asItem());p.setStackInHand(Hand.MAIN_HAND,stack);NoCollidePlacement.arm(p);
        var context=new ItemPlacementContext(new ItemUsageContext(p,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(second),Direction.UP,second.down(),false))){@Override public BlockPos getBlockPos(){return second;}};
        var action=((CompositeItem)block.asItem()).place(context);c.assertTrue(action.isAccepted(),"Held lease forces overlapping second installation");var owner=((CompositeBlockEntity)world.getBlockEntity(second)).resident();
        c.assertTrue(!world.getBlockState(second).getCollisionShape(world,second,ShapeContext.of(p)).isEmpty()&&!NoCollidePlacement.armed(p)&&!stack.getOrCreateNbt().contains("NoCollide"),"Physics unchanged; no saved privilege");
        var picked=world.getBlockState(second).getBlock().getPickStack(world,second,world.getBlockState(second));c.assertTrue(!picked.getOrCreateNbt().contains("NoCollide"),"Creative pick ordinary");c.assertTrue(CompositeRuntime.remove(world,owner,p,false).outcome()==TransactionCore.Outcome.COMMITTED,"Whole forced object breaks normally");
        var ordinary=CompositeRuntime.place(world,second,block.getDefaultState(),UUID.randomUUID(),p);c.assertTrue(ordinary.outcome()==TransactionCore.Outcome.REJECTED,"Ordinary reconstruction remains blocked by actual overlap");CompositeRuntime.remove(world,firstOwner,p,false);c.complete();}
}
