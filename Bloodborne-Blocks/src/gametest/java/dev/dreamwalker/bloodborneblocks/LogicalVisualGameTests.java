package dev.dreamwalker.bloodborneblocks;

import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.state.property.Property;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;

/** Server-side regression coverage for visual-only logical-state transitions. */
public final class LogicalVisualGameTests implements FabricGameTest {
 private static final BlockPos ROOT=new BlockPos(4,2,4);

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=40,batchId="logical_visual")
 public void commandTreePermissionAndVisualStatePreservation(TestContext context){
  ServerWorld world=context.getWorld();ServerCommandSource source=world.getServer().getCommandSource();
  CommandNode<ServerCommandSource> bloodborne=world.getServer().getCommandManager().getDispatcher().getRoot().getChild("bloodborne");
  context.assertTrue(bloodborne!=null&&bloodborne.getChild("visual")!=null&&bloodborne.getChild("visual").getChild("region")!=null,"visual command tree registered");
  context.assertTrue(bloodborne.getRequirement().test(source)&&!bloodborne.getRequirement().test(source.withLevel(1)),"visual inherits level-2 permission");
  ArchitectureBlock block=BloodborneBlocks.BLOCKS.get("o_c001");context.assertTrue(block!=null,"logical visual fixture exists");
  Property<?> raw=block.getStateManager().getProperty("visual");context.assertTrue(raw!=null&&raw.parse("base").isPresent()&&raw.parse("alt").isPresent(),"visible logical states expose base/alt visual property");
  @SuppressWarnings("unchecked") Property<String> visual=(Property<String>)raw;
  BlockPos root=context.getAbsolutePos(ROOT);BlockState before=block.getDefaultState();world.setBlockState(root,before,3);context.assertTrue(GeometryRuntime.rebuild(world,root,before),"visual fixture rebuilds");
  context.assertTrue(!LogicalVisualCommands.change(null,world,root,"base"),"already-selected base does not count as a region change");
  context.assertTrue(LogicalVisualCommands.change(null,world,root,"alt"),"changed visual counts as a region change");
  context.assertTrue(!LogicalVisualCommands.change(null,world,root,"alt"),"repeated alternative does not count as a region change");
  BlockState after=world.getBlockState(root);context.assertTrue(after.get(visual).equals("alt"),"visual alternative persisted");
  for(var entry:before.getEntries().entrySet())if(!"visual".equals(entry.getKey().getName()))context.assertTrue(entry.getValue().equals(after.getEntries().get(entry.getKey())),"visual update preserves "+entry.getKey().getName());
  context.assertTrue(GeometryRuntime.allCellsLoaded(world,root,after),"visual update leaves existing logical footprint intact");world.removeBlock(root,false);context.complete();
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=40,batchId="logical_visual")
 public void helpersResolveOnlyToValidLoadedMasters(TestContext context){
  ServerWorld world=context.getWorld();ArchitectureBlock block=BloodborneBlocks.BLOCKS.get("o_c001");BlockPos root=context.getAbsolutePos(ROOT);BlockState state=block.getDefaultState();world.setBlockState(root,state,3);context.assertTrue(GeometryRuntime.rebuild(world,root,state),"fixture rebuilds");
  BlockPos helper=null;for(BlockPos offset:GeometryRuntime.state(state).parsedCells.keySet())if(!offset.equals(BlockPos.ORIGIN)){helper=root.add(offset);break;}
  context.assertTrue(helper!=null&&root.equals(LogicalVisualCommands.resolveMaster(world,helper)),"owned helper resolves master");
  ArchitecturePartBlockEntity part=GeometryRuntime.part(world,helper);part.bind(root,new Identifier("minecraft","air"));context.assertTrue(LogicalVisualCommands.resolveMaster(world,helper)==null,"stale helper is never a visual target");
  BlockPos foreign=context.getAbsolutePos(new BlockPos(1,2,1));world.setBlockState(foreign,Blocks.STONE.getDefaultState(),3);context.assertTrue(LogicalVisualCommands.resolveMaster(world,foreign)==null,"foreign block is skipped");
  world.removeBlock(root,false);world.removeBlock(helper,false);world.removeBlock(foreign,false);context.complete();
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=40,batchId="logical_visual")
 public void selectionDropsOtherDimensionEndpoint(TestContext context){
  ServerWorld world=context.getWorld();ServerWorld nether=world.getServer().getWorld(World.NETHER);
  context.assertTrue(nether!=null,"nether world is available for cross-dimension selection check");
  BlockPos oldFirst=new BlockPos(1,2,3),oldSecond=new BlockPos(4,5,6),current=new BlockPos(7,8,9);
  LogicalVisualCommands.Selection prior=new LogicalVisualCommands.Selection(nether,oldFirst,oldSecond);
  LogicalVisualCommands.Selection next=LogicalVisualCommands.updatedSelection(prior,world,current,true);
  context.assertTrue(next.world()==world&&next.first().equals(current)&&next.second()==null,"pos1 in a new dimension clears the prior pos2");
  context.complete();
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=60,batchId="logical_visual")
 public void actualRegionCommandDeduplicatesHelpersAndPreservesOwnership(TestContext context) throws Exception {
  ServerWorld world=context.getWorld();ServerPlayerEntity player=context.createMockCreativeServerPlayerInWorld();
  BlockPos root=context.getAbsolutePos(ROOT),foreign=root.add(-1,0,0);
  try{
   BlockState base=BloodborneBlocks.BLOCKS.get("o_c001").getDefaultState();
   world.setBlockState(root,base,3);context.assertTrue(GeometryRuntime.rebuild(world,root,base),"region fixture rebuilds");
   var helperTags=new java.util.HashMap<BlockPos,net.minecraft.nbt.NbtCompound>();
   var helperInstances=new java.util.HashMap<BlockPos,ArchitecturePartBlockEntity>();
   BlockPos min=foreign,max=root;
   for(BlockPos offset:GeometryRuntime.state(base).parsedCells.keySet()){
    BlockPos cell=root.add(offset);min=new BlockPos(Math.min(min.getX(),cell.getX()),Math.min(min.getY(),cell.getY()),Math.min(min.getZ(),cell.getZ()));
    max=new BlockPos(Math.max(max.getX(),cell.getX()),Math.max(max.getY(),cell.getY()),Math.max(max.getZ(),cell.getZ()));
    if(!offset.equals(BlockPos.ORIGIN)){var part=GeometryRuntime.part(world,cell);helperInstances.put(cell,part);helperTags.put(cell,part.createNbt());}
   }
   context.assertTrue(!helperTags.isEmpty(),"region contains real helpers");world.setBlockState(foreign,Blocks.STONE.getDefaultState(),3);
   var dispatcher=world.getServer().getCommandManager().getDispatcher();var source=player.getCommandSource().withLevel(2);
   player.refreshPositionAndAngles(min.getX()+.5,min.getY(),min.getZ()+.5,0,0);dispatcher.execute("bloodborne visual pos1",source);
   player.refreshPositionAndAngles(max.getX()+.5,max.getY(),max.getZ()+.5,0,0);dispatcher.execute("bloodborne visual pos2",source);
   context.assertTrue(dispatcher.execute("bloodborne visual region alt",source)==1,"region changes one master, not every helper");
   context.assertTrue(dispatcher.execute("bloodborne visual region alt",source)==0,"no-op region changes zero masters");
   context.assertTrue(world.getBlockState(foreign).isOf(Blocks.STONE),"region preserves foreign cells");
   for(var entry:helperTags.entrySet()){
    var part=GeometryRuntime.part(world,entry.getKey());context.assertTrue(part==helperInstances.get(entry.getKey()),"visual does not rebuild helper BE");
    context.assertTrue(entry.getValue().equals(part.createNbt()),"visual preserves helper NBT");
    var restored=new ArchitecturePartBlockEntity(entry.getKey(),world.getBlockState(entry.getKey()));restored.readNbt(part.createNbt());
    context.assertTrue(restored.rootPos().equals(root)&&restored.ownerId().equals(part.ownerId()),"helper ownership NBT round trip");
   }
   var encoded=NbtHelper.fromBlockState(world.getBlockState(root));
   var loaded=NbtHelper.toBlockState(world.getRegistryManager().getWrapperOrThrow(RegistryKeys.BLOCK),encoded);
   context.assertTrue(loaded.equals(world.getBlockState(root)),"ALT blockstate survives Minecraft NBT round trip");
   encoded.getCompound("Properties").remove("visual");
   context.assertTrue(NbtHelper.toBlockState(world.getRegistryManager().getWrapperOrThrow(RegistryKeys.BLOCK),encoded).equals(base),"old world palette without visual loads BASE");
   context.assertTrue(dispatcher.execute("bloodborne visual region toggle",source)==1&&world.getBlockState(root).equals(base),"region toggle preserves non-visual properties");
   context.complete();
  }finally{world.removeBlock(root,false);world.removeBlock(foreign,false);player.getServer().getPlayerManager().remove(player);}
 }
}
