package dev.dreamwalker.bloodborneblocks;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

/** Runtime proof that every FLOOR contract state stays above a preserved support plane. */
public final class SupportPlaneGameTests implements FabricGameTest {
 private static final BlockPos CLICK=new BlockPos(4,0,4);
 private static final BlockPos ROOT=new BlockPos(4,2,4);
 private static final int PLATFORM_LIMIT=8;
 private static final double EPSILON=1.0E-6;
 private static final Set<String> FUNCTIONAL_COLLISION_POLICIES=Set.of("FENCE","WALL","DOOR","GATE","STAIRS");

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=600,batchId="support_plane")
 public void everyFloorContractStateKeepsSupportPlaneAndOwnership(TestContext context){
  ServerWorld world=context.getWorld();PlayerEntity player=context.createMockSurvivalPlayer();BlockPos root=context.getAbsolutePos(ROOT);List<ForcedChunk> forced=forceRelevantChunks(world,root);
  try{
   Map<String,ContractFamily> families=floorFamilies();
   Map<String,ModularMeshData.Mesh> meshes=ModularMeshData.loadLogicalAndValidate();
   for(ContractFamily family:families.values()){
    ArchitectureBlock block=required(family.id);Map<String,BlockState> runtimeStates=statesByKey(block);
    int platformRadius=platformRadius(family,block);preparePlatform(context,ROOT.down(),platformRadius);
    context.assertTrue(runtimeStates.keySet().equals(family.states.keySet()),"runtime state mapping matches FLOOR contract: "+family.id);
    EnumSet<Direction> facings=EnumSet.noneOf(Direction.class);
    Set<String> visuals=new HashSet<>();
    for(var entry:family.states.entrySet()){
     String key=entry.getKey();ContractState contract=entry.getValue();BlockState state=runtimeStates.get(key);
     if(state.contains(Properties.HORIZONTAL_FACING))facings.add(state.get(Properties.HORIZONTAL_FACING));
     var visual=state.getBlock().getStateManager().getProperty("visual");if(visual!=null)visuals.add(String.valueOf(state.getEntries().get(visual)));
     assertContractGeometry(context,family,contract,state,meshes);
     clearRoot(world,root);moveOutside(context,player);assertPlatform(context,ROOT.down(),platformRadius,family.id+" before "+key);
     world.setBlockState(root,state,Block.NOTIFY_ALL);
     context.assertTrue(GeometryRuntime.rebuild(world,root,state),"FLOOR state rebuilds on white concrete: "+family.id+"["+key+"]");
     context.assertTrue(world.getBlockState(root).equals(state),"rebuild preserves every state property: "+family.id+"["+key+"]");
     assertOwnedCells(context,root,state,family.id+"["+key+"]");
     ItemStack rootPick=block.getPickStack(world,root,state);context.assertTrue(!rootPick.isEmpty(),"root pick resolves an item or canonical alias: "+family.id+"["+key+"]");
     BlockPos helper=firstHelper(root,state);if(helper!=null){
      BlockState helperState=world.getBlockState(helper);
      context.assertTrue(helperState.getBlock().getPickStack(world,helper,helperState).isOf(rootPick.getItem()),"helper pick preserves canonical root item: "+family.id+"["+key+"]");
      world.breakBlock(helper,true,player);
      context.assertTrue(world.getBlockState(root).isAir(),"breaking owned helper removes canonical root: "+family.id+"["+key+"]");
      for(BlockPos offset:GeometryRuntime.state(state).parsedCells.keySet())context.assertTrue(world.getBlockState(root.add(offset)).isAir(),"helper break clears owned cell: "+family.id+"["+key+"] "+offset);
     }else world.breakBlock(root,true,player);
     moveOutside(context,player);assertPlatform(context,ROOT.down(),platformRadius,family.id+" after "+key);clearItems(world,root,block);
    }
    if(block.getDefaultState().contains(Properties.HORIZONTAL_FACING))context.assertTrue(facings.equals(horizontalDirections()),"contract represents all N/E/S/W states: "+family.id);
    if(!visuals.isEmpty())context.assertTrue(visuals.equals(Set.of("base","alt")),"contract represents all visual states: "+family.id);
    clearPlatform(context,ROOT.down(),platformRadius);
   }
   context.complete();
  }finally{clearRoot(world,root);clearPlatform(context,ROOT.down(),PLATFORM_LIMIT);unforce(world,forced);player.discard();}
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=300,batchId="support_plane")
 public void floorItemsPlaceOnConcreteForDefaultsAndEveryFacing(TestContext context){
  ServerWorld world=context.getWorld();PlayerEntity player=context.createMockCreativePlayer();BlockPos rootBase=context.getAbsolutePos(CLICK.up());List<ForcedChunk> forced=forceRelevantChunks(world,rootBase);
  try{
   Map<String,ContractFamily> families=floorFamilies();
   for(ContractFamily family:families.values()){
    ArchitectureBlock block=required(family.id);EnumSet<Direction> placedFacings=EnumSet.noneOf(Direction.class);
    // Hidden/compatibility items may intentionally redirect to a canonical section item;
    // direct-state pick checks above cover that path without asserting an obsolete item ID.
    if(PaletteAliases.hidden(family.id))continue;
    int platformRadius=platformRadius(family,block);preparePlatform(context,CLICK,platformRadius);
    List<Direction> attempts=block.getDefaultState().contains(Properties.HORIZONTAL_FACING)?List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST):List.of(Direction.NORTH);
    for(Direction facing:attempts){
     clearPlacedFamily(context,block);player.refreshPositionAndAngles(context.getAbsolutePos(new BlockPos(4,3,10)).getX()+.5,context.getAbsolutePos(new BlockPos(4,3,10)).getY(),context.getAbsolutePos(new BlockPos(4,3,10)).getZ()+.5,facing.asRotation(),0);
     context.useStackOnBlock(player,new ItemStack(block),CLICK,Direction.UP);
     BlockPos root=placedRoot(context,block);context.assertTrue(root!=null,"item placement creates FLOOR root: "+family.id+" "+facing);
     BlockState placed=world.getBlockState(root);if(placed.contains(Properties.HORIZONTAL_FACING))placedFacings.add(placed.get(Properties.HORIZONTAL_FACING));
     assertOwnedCells(context,root,placed,"manual "+family.id+" "+facing);assertPlatform(context,CLICK,platformRadius,family.id+" manual "+facing);
     clearRoot(world,root);clearItems(world,root,block);
    }
    if(block.getDefaultState().contains(Properties.HORIZONTAL_FACING))context.assertTrue(placedFacings.equals(horizontalDirections()),"manual item placement reaches every facing: "+family.id);
    clearPlatform(context,CLICK,platformRadius);
   }
   context.complete();
  }finally{clearAllFloorRoots(context);clearPlatform(context,CLICK,PLATFORM_LIMIT);unforce(world,forced);player.discard();}
 }

 // This test waits for END_WORLD_TICK removal, so it needs an isolated batch: the
 // two large support-plane fixtures otherwise finish and clear while it is pending.
 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=40,batchId="support_plane_stale_helper")
 public void obsoleteBelowRootHelperIsRemovedAfterPersistentOwnershipValidation(TestContext context){
  ServerWorld world=context.getWorld();BlockPos root=context.getAbsolutePos(new BlockPos(12,2,12));List<ForcedChunk> forced=forceRelevantChunks(world,root);ArchitectureBlock block=required("o_cases_0");BlockState state=block.getDefaultState();BlockPos obsolete=root.down(),foreign=obsolete.east();
  try{
   context.assertTrue(!GeometryRuntime.state(state).parsedCells.containsKey(new BlockPos(0,-1,0)),"fixture family no longer owns a below-root helper");
   world.setBlockState(root,state,Block.NOTIFY_ALL);context.assertTrue(GeometryRuntime.rebuild(world,root,state),"persistent-helper fixture rebuilds root");
   world.setBlockState(foreign,Blocks.WHITE_CONCRETE.getDefaultState(),Block.NOTIFY_ALL);context.assertTrue(world.getBlockState(foreign).isOf(Blocks.WHITE_CONCRETE),"fixture writes adjacent foreign support before validation");world.setBlockState(obsolete,BloodborneBlocks.PART_BLOCK.getDefaultState(),Block.NOTIFY_ALL);
   ArchitecturePartBlockEntity part=GeometryRuntime.part(world,obsolete);context.assertTrue(part!=null,"historical helper block entity exists");
   var owner=Registries.BLOCK.getId(block);part.bind(root,owner);var saved=part.createNbt();part.readNbt(saved);context.assertTrue(part.rootPos().equals(root)&&part.ownerId().equals(owner),"historical helper ownership survives NBT round trip");
   part.validateOwner(world);
   context.runAtTick(2,()->{try{context.assertTrue(world.getBlockState(obsolete).isAir(),"obsolete below-root helper is removed on queued validation");context.assertTrue(world.getBlockState(root).equals(state),"obsolete helper cleanup preserves root state");context.assertTrue(world.getBlockState(foreign).isOf(Blocks.WHITE_CONCRETE),"obsolete helper cleanup preserves adjacent foreign support");context.complete();}finally{clearRoot(world,root);world.removeBlock(obsolete,false);world.removeBlock(foreign,false);unforce(world,forced);}});
  }catch(RuntimeException|Error failure){clearRoot(world,root);world.removeBlock(obsolete,false);world.removeBlock(foreign,false);unforce(world,forced);throw failure;}
 }

 private static void assertContractGeometry(TestContext context,ContractFamily family,ContractState contract,BlockState state,Map<String,ModularMeshData.Mesh> meshes){
  GeometryRuntime.GeometryState runtime=GeometryRuntime.state(state);BlockPos anchor=GeometryRuntime.anchor(state);
  context.assertTrue(anchor.equals(family.anchor),"canonical anchor/source coordinates preserved: "+family.id+"["+contract.key+"]");
  context.assertTrue(runtime.placementPolicy.equals("FLOOR"),"runtime placement policy remains FLOOR: "+family.id+"["+contract.key+"]");
  context.assertTrue(equal(runtime.render_offset,contract.renderOffset),"runtime render offset matches authored contract: "+family.id+"["+contract.key+"]");
  for(BlockPos offset:runtime.parsedCells.keySet())context.assertTrue(offset.getY()>=0,"FLOOR state has no helper below root: "+family.id+"["+contract.key+"] "+offset);
  Box selection=GeometryRuntime.rootShape(state,true).getBoundingBox();assertBox(context,selection,contract.selection,"selection footprint remains raised with render: "+family.id+"["+contract.key+"]");
  context.assertTrue(selection.minY>=-EPSILON,"selection does not bury FLOOR object: "+family.id+"["+contract.key+"]");
  if(!FUNCTIONAL_COLLISION_POLICIES.contains(family.collisionPolicy))context.assertTrue(contract.collisionBoxes.size()<=3,"ordinary collision uses at most three authored global boxes: "+family.id+"["+contract.key+"]");
  for(double[] box:contract.collisionBoxes)context.assertTrue(box[1]>=-EPSILON,"authored collision does not extend below support plane: "+family.id+"["+contract.key+"]");
  VoxelShape collision=wholeCollision(state);if(!collision.isEmpty())context.assertTrue(collision.getBoundingBox().minY>=-EPSILON,"runtime collision does not extend below support plane: "+family.id+"["+contract.key+"]");
  ModularMeshData.Mesh mesh=meshes.get(contract.meshId);context.assertTrue(mesh!=null,"contract mesh resolves: "+family.id+"["+contract.key+"]");
  for(ModularMeshData.Polygon polygon:mesh.polygons)for(int vertex=0;vertex<polygon.vertexCount();vertex++)context.assertTrue(polygon.vertices[vertex*5+1]+contract.renderOffset[1]>=-EPSILON,"rendered mesh vertex stays above support plane: "+family.id+"["+contract.key+"]");
 }

 private static void assertOwnedCells(TestContext context,BlockPos root,BlockState state,String label){
  ServerWorld world=context.getWorld();for(BlockPos offset:GeometryRuntime.state(state).parsedCells.keySet()){
   BlockPos cell=root.add(offset);context.assertTrue(world.isChunkLoaded(cell),"fixture chunk is loaded: "+label+" "+offset);
   if(offset.equals(BlockPos.ORIGIN))continue;
   ArchitecturePartBlockEntity part=GeometryRuntime.part(world,cell);context.assertTrue(part!=null&&part.rootPos().equals(root)&&GeometryRuntime.ownsHelper(state,root,cell,part.ownerId()),"helper keeps canonical root ownership: "+label+" "+offset);
  }
 }

 private static VoxelShape wholeCollision(BlockState state){VoxelShape result=VoxelShapes.empty();for(var entry:GeometryRuntime.state(state).parsedCells.entrySet())result=VoxelShapes.union(result,GeometryRuntime.cellShape(state,entry.getKey(),false).offset(entry.getKey().getX(),entry.getKey().getY(),entry.getKey().getZ()));return result;}
 private static EnumSet<Direction> horizontalDirections(){return EnumSet.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST);}
 private static void assertBox(TestContext context,Box actual,double[] expected,String label){context.assertTrue(Math.abs(actual.minX-expected[0])<=EPSILON&&Math.abs(actual.minY-expected[1])<=EPSILON&&Math.abs(actual.minZ-expected[2])<=EPSILON&&Math.abs(actual.maxX-expected[3])<=EPSILON&&Math.abs(actual.maxY-expected[4])<=EPSILON&&Math.abs(actual.maxZ-expected[5])<=EPSILON,label+" actual="+actual);}
 private static boolean equal(double[] first,double[] second){if(first.length!=second.length)return false;for(int index=0;index<first.length;index++)if(Math.abs(first[index]-second[index])>EPSILON)return false;return true;}
 private static BlockPos firstHelper(BlockPos root,BlockState state){for(BlockPos offset:GeometryRuntime.state(state).parsedCells.keySet())if(!offset.equals(BlockPos.ORIGIN))return root.add(offset);return null;}
 private static int platformRadius(ContractFamily family,ArchitectureBlock block){
  int radius=0;for(ContractState state:family.states.values())for(int index:new int[]{0,2,3,5})radius=Math.max(radius,(int)Math.ceil(Math.abs(state.selection[index])));
  for(BlockState state:block.getStateManager().getStates())for(BlockPos offset:GeometryRuntime.state(state).parsedCells.keySet())radius=Math.max(radius,Math.max(Math.abs(offset.getX()),Math.abs(offset.getZ())));
  if(radius>PLATFORM_LIMIT)throw new AssertionError("FLOOR support plane exceeds bounded test area: "+family.id+" radius="+radius);return radius;
 }
 private static Map<String,BlockState> statesByKey(ArchitectureBlock block){Map<String,BlockState> result=new HashMap<>();for(BlockState state:block.getStateManager().getStates())result.put(BloodborneBlocks.key(state),state);return result;}
 private static ArchitectureBlock required(String id){ArchitectureBlock block=BloodborneBlocks.BLOCKS.get(id);if(block==null)throw new AssertionError("missing FLOOR contract block "+id);return block;}
 private static BlockPos placedRoot(TestContext context,ArchitectureBlock block){ServerWorld world=context.getWorld();for(BlockState candidate:block.getStateManager().getStates()){BlockPos root=context.getAbsolutePos(CLICK.up().subtract(GeometryRuntime.anchor(candidate,Direction.UP)));if(world.getBlockState(root).isOf(block))return root;}return null;}
 private static void preparePlatform(TestContext context,BlockPos center,int radius){for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++)context.setBlockState(center.add(x,0,z),Blocks.WHITE_CONCRETE);}
 private static void assertPlatform(TestContext context,BlockPos center,int radius,String label){ServerWorld world=context.getWorld();for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++)context.assertTrue(world.getBlockState(context.getAbsolutePos(center.add(x,0,z))).isOf(Blocks.WHITE_CONCRETE),"white-concrete support plane remains unchanged: "+label+" "+x+","+z);}
 private static void clearPlatform(TestContext context,BlockPos center,int radius){ServerWorld world=context.getWorld();for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++)world.removeBlock(context.getAbsolutePos(center.add(x,0,z)),false);}
 private static void clearRoot(ServerWorld world,BlockPos root){BlockState state=world.getBlockState(root);if(state.getBlock() instanceof ArchitectureBlock)world.breakBlock(root,false);}
 private static void clearPlacedFamily(TestContext context,ArchitectureBlock block){ServerWorld world=context.getWorld();for(int x=-4;x<=12;x++)for(int y=0;y<=18;y++)for(int z=-4;z<=12;z++){BlockPos pos=context.getAbsolutePos(new BlockPos(x,y,z));if(world.getBlockState(pos).isOf(block))world.breakBlock(pos,false);}}
 private static void clearAllFloorRoots(TestContext context){for(ContractFamily family:floorFamilies().values())clearPlacedFamily(context,required(family.id));}
 private static void clearItems(ServerWorld world,BlockPos root,ArchitectureBlock block){world.getEntitiesByClass(ItemEntity.class,new Box(root).expand(20),entity->entity.getStack().isOf(block.asItem())).forEach(ItemEntity::discard);}
 private static void moveOutside(TestContext context,PlayerEntity player){BlockPos pos=context.getAbsolutePos(new BlockPos(12,18,12));player.refreshPositionAndAngles(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);}
 private static List<ForcedChunk> forceRelevantChunks(ServerWorld world,BlockPos center){List<ForcedChunk> result=new ArrayList<>();int chunkX=center.getX()>>4,chunkZ=center.getZ()>>4;for(int x=chunkX-1;x<=chunkX+1;x++)for(int z=chunkZ-1;z<=chunkZ+1;z++){ChunkPos chunk=new ChunkPos(x,z);boolean wasForced=world.getForcedChunks().contains(chunk.toLong());world.setChunkForced(x,z,true);world.getChunk(x,z);if(!world.isChunkLoaded(chunk.getStartPos()))throw new AssertionError("unable to load fixture chunk "+chunk);result.add(new ForcedChunk(chunk,wasForced));}return result;}
 private static void unforce(ServerWorld world,List<ForcedChunk> chunks){for(ForcedChunk chunk:chunks)if(!chunk.wasForced)world.setChunkForced(chunk.chunk.x,chunk.chunk.z,false);}
 private record ForcedChunk(ChunkPos chunk,boolean wasForced) {}

 private static Map<String,ContractFamily> floorFamilies(){
  try(InputStream stream=SupportPlaneGameTests.class.getResourceAsStream("/bloodborne_blocks/logical/contracts-v2.json")){
   if(stream==null)throw new AssertionError("missing contracts-v2.json");JsonArray families=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("families");Map<String,ContractFamily> result=new HashMap<>();
   for(JsonElement element:families){JsonObject raw=element.getAsJsonObject();if(!"FLOOR".equals(raw.get("placement_policy").getAsString()))continue;ContractFamily family=new ContractFamily(raw);if(result.put(family.id,family)!=null)throw new AssertionError("duplicate FLOOR contract family "+family.id);}
   return result;
  }catch(Exception exception){throw new AssertionError("cannot read FLOOR contract",exception);}
 }

 private static final class ContractFamily {
  final String id,collisionPolicy;final BlockPos anchor;final Map<String,ContractState> states=new HashMap<>();
  ContractFamily(JsonObject raw){id=raw.get("id").getAsString();collisionPolicy=raw.get("collision_policy").getAsString();JsonArray cell=raw.getAsJsonObject("canonical_anchor").getAsJsonArray("cell");anchor=new BlockPos(cell.get(0).getAsInt(),cell.get(1).getAsInt(),cell.get(2).getAsInt());for(var entry:raw.getAsJsonObject("states").entrySet())states.put(entry.getKey(),new ContractState(entry.getKey(),entry.getValue().getAsJsonObject()));}
 }
 private static final class ContractState {
  final String key,meshId;final double[] renderOffset,selection;final List<double[]> collisionBoxes=new ArrayList<>();
  ContractState(String key,JsonObject raw){this.key=key;JsonObject render=raw.getAsJsonObject("render_mesh");meshId=render.get("id").getAsString();renderOffset=doubles(render.getAsJsonArray("offset"));JsonArray selectionBoxes=raw.getAsJsonObject("selection_footprint").getAsJsonArray("boxes");selection=doubles(selectionBoxes.get(0).getAsJsonArray());for(JsonElement box:raw.getAsJsonObject("collision_footprint").getAsJsonArray("boxes"))collisionBoxes.add(doubles(box.getAsJsonArray()));}
 }
 private static double[] doubles(JsonArray raw){double[] result=new double[raw.size()];for(int index=0;index<result.length;index++)result[index]=raw.get(index).getAsDouble();return result;}
}
