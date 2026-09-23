package dev.dreamwalker.bloodborneblocks;

import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Server-only smoke coverage for the non-mutating logical target diagnostic. */
public final class LogicalDebugGameTests implements FabricGameTest {
 private static final BlockPos ROOT=new BlockPos(4,2,4);

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE, tickLimit=40, batchId="logical_debug")
 public void commandTreeAliasesPermissionAndNonPlayer(TestContext context) throws Exception {
  ServerCommandSource source=context.getWorld().getServer().getCommandSource();
  CommandNode<ServerCommandSource> bloodborne=context.getWorld().getServer().getCommandManager().getDispatcher().getRoot().getChild("bloodborne");
  context.assertTrue(bloodborne!=null&&bloodborne.getChild("debug")!=null&&bloodborne.getChild("debug").getChild("target")!=null,"debug command and target alias are registered");
  context.assertTrue(bloodborne.getRequirement().test(source)&&!bloodborne.getRequirement().test(source.withLevel(1)),"debug inherits the level-2 command permission");
  int direct=context.getWorld().getServer().getCommandManager().getDispatcher().execute("bloodborne debug",source);
  int alias=context.getWorld().getServer().getCommandManager().getDispatcher().execute("bloodborne debug target",source);
  context.assertTrue(direct==0&&alias==0,"non-player debug calls fail safely");context.complete();
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE, tickLimit=40, batchId="logical_debug")
 public void ordinaryAndLogicalMasterHelperAndStaleHelperAreSafe(TestContext context){
  ServerWorld world=context.getWorld();BlockPos ordinary=context.getAbsolutePos(new BlockPos(1,2,1));world.setBlockState(ordinary,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
  String normal=LogicalTargetDebug.inspectTarget(world,ordinary);
  context.assertTrue(normal.contains("Registry ID: minecraft:stone")&&normal.contains("Target: NON-LOGICAL"),"ordinary block diagnostic is non-logical");
  ArchitectureBlock block=BloodborneBlocks.BLOCKS.get("o_c001");context.assertTrue(block!=null,"logical debug fixture exists");
  BlockPos root=context.getAbsolutePos(ROOT);BlockState state=block.getDefaultState();world.setBlockState(root,state,Block.NOTIFY_ALL);context.assertTrue(GeometryRuntime.rebuild(world,root,state),"logical debug fixture rebuilds");
  String master=LogicalTargetDebug.inspectTarget(world,root);
  context.assertTrue(master.contains("Logical ID: bloodborne_blocks:o_c001")&&master.contains("Target: MASTER")&&master.contains("Review ID: C001")&&master.contains("Facing: north")&&master.contains("Contract schema: v2")&&master.contains("Source family: ")&&master.contains("Source pattern: ")&&master.contains("235de5acef16")&&!master.contains("816a3e4bbde4")&&master.contains("\n")&&!master.contains(" | "),"master reports copyable, state-specific catalog metadata lines");
  LogicalContractV2.DebugMetadata east=LogicalContractV2.debugMetadata("o_c001",BloodborneBlocks.key(state.with(Properties.HORIZONTAL_FACING,net.minecraft.util.math.Direction.EAST)));
  context.assertTrue(east!=null&&east.sourcePatternSummary().contains("state signatures=none")&&east.sourcePatternSummary().contains("placed provenance not stored")&&!east.sourcePatternSummary().contains("816a3e4bbde4"),"east state never inherits a family signature as placed provenance");
  BlockPos helper=helper(world,root,state);context.assertTrue(helper!=null,"logical debug fixture has an owned helper");
  String helperReport=LogicalTargetDebug.inspectTarget(world,helper);
  context.assertTrue(helperReport.contains("Target: HELPER")&&helperReport.contains("Helper offset: "),"helper resolves to its master");
  ArchitecturePartBlockEntity part=GeometryRuntime.part(world,helper);part.bind(root,new Identifier("minecraft","air"));
  String stale=LogicalTargetDebug.inspectTarget(world,helper);
  context.assertTrue(stale.contains("Target: NON-LOGICAL")&&stale.contains("stale ownership"),"stale helper ownership is diagnostic-only");
  BlockPos unloadedHelper=context.getAbsolutePos(new BlockPos(2,2,2)),unloadedRoot=new BlockPos(1_000_000,64,1_000_000);world.setBlockState(unloadedHelper,BloodborneBlocks.PART_BLOCK.getDefaultState(),Block.NOTIFY_ALL);
  ArchitecturePartBlockEntity unloadedPart=GeometryRuntime.part(world,unloadedHelper);context.assertTrue(unloadedPart!=null&&!world.isChunkLoaded(unloadedRoot),"unloaded root fixture remains unloaded");unloadedPart.bind(unloadedRoot,BloodborneBlocks.id(block.definition.id));
  String unloaded=LogicalTargetDebug.inspectTarget(world,unloadedHelper);
  context.assertTrue(unloaded.contains("Target: NON-LOGICAL")&&unloaded.contains("root chunk unloaded"),"unloaded helper root is never loaded for diagnostics");
  world.removeBlock(root,false);world.removeBlock(helper,false);world.removeBlock(ordinary,false);world.removeBlock(unloadedHelper,false);context.complete();
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE, tickLimit=40, batchId="logical_debug")
 public void serverPlayerRaycastCommandsAreReadOnly(TestContext context) throws Exception {
  ServerWorld world=context.getWorld();BlockPos target=context.getAbsolutePos(new BlockPos(4,3,4));BlockState before=Blocks.STONE.getDefaultState();world.setBlockState(target,before,Block.NOTIFY_ALL);
  ServerPlayerEntity player=context.createMockCreativeServerPlayerInWorld();
  try{
   BlockPos camera=context.getAbsolutePos(new BlockPos(4,1,0));player.refreshPositionAndAngles(camera.getX()+.5,camera.getY()+.5,camera.getZ()+.5,0,0);
   ServerCommandSource source=player.getCommandSource().withLevel(4);int direct=world.getServer().getCommandManager().getDispatcher().execute("bloodborne debug",source);
   int alias=world.getServer().getCommandManager().getDispatcher().execute("bloodborne debug target",source);
   context.assertTrue(direct==1&&alias==1,"server-player outline raycast executes both debug aliases");
   context.assertTrue(world.getBlockState(target).equals(before),"debug raycast never mutates the targeted block");context.complete();
  }finally{world.removeBlock(target,false);player.getServer().getPlayerManager().remove(player);}
 }

 private static BlockPos helper(ServerWorld world,BlockPos root,BlockState state){
  for(BlockPos offset:GeometryRuntime.state(state).parsedCells.keySet()){
   if(offset.equals(BlockPos.ORIGIN))continue;BlockPos target=root.add(offset);ArchitecturePartBlockEntity part=GeometryRuntime.part(world,target);
   if(part!=null&&GeometryRuntime.ownsHelper(state,root,target,part.ownerId()))return target;
  }
  return null;
 }
}
