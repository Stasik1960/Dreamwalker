package dev.dreamwalker.bloodborneblocks;

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
import net.minecraft.util.Identifier;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Production-palette checks: only complete logical objects are registrable. */
public final class ProductionPaletteGameTests implements FabricGameTest {
 private static final BlockPos CLICK=new BlockPos(4,0,4);
 private static final BlockPos ROOT=new BlockPos(4,2,4);

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="production_palette_registry")
 public void registryExactlyMatchesProductionPaletteAndPart(TestContext context){
  Set<Identifier> expected=new LinkedHashSet<>();
  for(String objectId:BloodborneBlocks.productionPalette().keySet())expected.add(BloodborneBlocks.id(objectId));
  expected.add(BloodborneBlocks.id("architecture_part"));
  Set<Identifier> actual=new LinkedHashSet<>();
  for(Identifier id:Registries.BLOCK.getIds())if(BloodborneBlocks.ID.equals(id.getNamespace()))actual.add(id);
  context.assertTrue(actual.equals(expected),"registered production blocks exactly match production-palette.json: actual="+actual+" expected="+expected);
  for(String obsolete:List.of("o_c001_a","o_c001_b","o_c009_a","o_c009_b","o_c282_a","o_c282_b","o_c008_4"))context.assertTrue(!BloodborneBlocks.BLOCKS.containsKey(obsolete)&&!Registries.BLOCK.containsId(BloodborneBlocks.id(obsolete)),"obsolete fragment is not registered: "+obsolete);
  context.complete();
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=160,batchId="production_palette_door")
 public void c282IsOneDoubleLeafDoorAcrossFacings(TestContext context){
  floor(context);ServerWorld world=context.getWorld();PlayerEntity player=context.createMockCreativePlayer();ArchitectureBlock door=required("o_c282");BlockPos root=context.getAbsolutePos(ROOT);
  try{
   context.assertTrue(door.getStateManager().getProperty("hinge")==null,"collective C282 has no independent hinge/leaf state");
   for(Direction facing:Direction.Type.HORIZONTAL)for(String visual:visuals(door)){
    clear(context,door);BlockState closed=door.getDefaultState().with(Properties.HORIZONTAL_FACING,facing).with(Properties.OPEN,false);if(visual!=null)closed=BloodborneBlocks.set(closed,door.getStateManager().getProperty("visual"),visual);
    world.setBlockState(root,closed,Block.NOTIFY_ALL);context.assertTrue(GeometryRuntime.rebuild(world,root,closed),"closed C282 rebuilds for "+facing+" visual="+visual);
    List<BlockPos> contextGlass=foreignContext(root,facing);for(BlockPos glass:contextGlass)world.setBlockState(glass,Blocks.GLASS_PANE.getDefaultState(),Block.NOTIFY_ALL);
    context.assertTrue(blocksDoorway(GeometryRuntime.rootShape(closed,false)),"closed C282 blocks the central doorway: "+facing);
    context.useBlock(ROOT,player);BlockState opened=world.getBlockState(root);
    context.assertTrue(opened.isOf(door)&&opened.get(Properties.OPEN)&&opened.get(Properties.HORIZONTAL_FACING)==facing,"C282 opens both leaves without moving its root: "+facing);
    context.assertTrue(!blocksDoorway(GeometryRuntime.rootShape(opened,false)),"open C282 has a collision-free central passage: "+facing);
    context.assertTrue(!GeometryRuntime.rootShape(opened,true).isEmpty(),"open C282 retains one selectable collective object: "+facing);
    for(BlockPos glass:contextGlass)context.assertTrue(world.getBlockState(glass).isOf(Blocks.GLASS_PANE),"opening C282 preserves foreign glass context: "+facing);
    context.useBlock(ROOT,player);BlockState reclosed=world.getBlockState(root);
    context.assertTrue(reclosed.isOf(door)&&!reclosed.get(Properties.OPEN)&&reclosed.get(Properties.HORIZONTAL_FACING)==facing,"C282 closes at the same collective root: "+facing);
    for(BlockPos glass:contextGlass){context.assertTrue(world.getBlockState(glass).isOf(Blocks.GLASS_PANE),"closing C282 preserves foreign glass context: "+facing);world.removeBlock(glass,false);}
   }
   context.complete();
  }finally{clear(context,door);player.discard();}
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="production_palette_door")
 public void c282OpenIsAtomicWhenAnOuterLeafCellIsBlocked(TestContext context){
  floor(context);ServerWorld world=context.getWorld();PlayerEntity player=context.createMockCreativePlayer();ArchitectureBlock door=required("o_c282");BlockPos root=context.getAbsolutePos(ROOT);
  try{
   BlockState closed=door.getDefaultState().with(Properties.HORIZONTAL_FACING,Direction.NORTH).with(Properties.OPEN,false),opened=closed.with(Properties.OPEN,true);
   world.setBlockState(root,closed,Block.NOTIFY_ALL);context.assertTrue(GeometryRuntime.rebuild(world,root,closed),"closed C282 rebuilds before atomicity check");
   BlockPos blocked=helperOnly(root,closed,opened);world.setBlockState(blocked,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
   ActionResult result=closed.onUse(world,player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(root),Direction.NORTH,root,true));
   context.assertTrue(result==ActionResult.FAIL&&world.getBlockState(root).equals(closed),"blocked C282 leaf preflight preserves the closed root");
   context.assertTrue(world.getBlockState(blocked).isOf(Blocks.STONE),"blocked C282 preflight preserves foreign cell");
   world.removeBlock(blocked,false);world.setBlockState(root,opened,Block.NOTIFY_ALL);context.assertTrue(GeometryRuntime.rebuild(world,root,opened),"open C282 rebuilds before close atomicity check");
   BlockPos blockedClose=helperOnly(root,opened,closed);world.setBlockState(blockedClose,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
   ActionResult close=opened.onUse(world,player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(root),Direction.NORTH,root,true));
   context.assertTrue(close==ActionResult.FAIL&&world.getBlockState(root).equals(opened),"blocked C282 close preserves the open root");
   context.assertTrue(world.getBlockState(blockedClose).isOf(Blocks.STONE),"blocked C282 close preserves foreign cell");
   world.removeBlock(blockedClose,false);context.complete();
  }finally{clear(context,door);player.discard();}
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=140,batchId="production_palette_door")
 public void c282AuthenticSourcePaneContextIsNeverClaimed(TestContext context){
  floor(context);ServerWorld world=context.getWorld();PlayerEntity player=context.createMockCreativePlayer();ArchitectureBlock door=required("o_c282");
  try{
   moveOutside(context,player);BlockPos root=context.getAbsolutePos(CLICK.up());
   for(Direction facing:Direction.Type.HORIZONTAL){
    clear(context,door);List<BlockPos> panes=authenticPaneContext(root,facing);for(BlockPos pane:panes)world.setBlockState(pane,Blocks.GLASS_PANE.getDefaultState(),Block.NOTIFY_ALL);
    player.setYaw(facing.asRotation());context.useStackOnBlock(player,new ItemStack(door),CLICK,Direction.UP);
    BlockState placed=world.getBlockState(root);
    if(placed.isOf(door)){context.assertTrue(GeometryRuntime.rebuild(world,root,placed),"clear authentic context rebuilds C282: "+facing);context.useBlock(CLICK.up(),player);context.useBlock(CLICK.up(),player);}
    else context.assertTrue(placed.isAir(),"authentic panes safely block C282 placement: "+facing);
    for(BlockPos pane:panes){context.assertTrue(world.getBlockState(pane).isOf(Blocks.GLASS_PANE),"authentic C282 pane remains foreign: "+facing);world.removeBlock(pane,false);}
   }
   context.complete();
  }finally{clear(context,door);player.discard();}
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=80,batchId="production_palette_rotation")
 public void productionFacingStatesRotateWithoutChangingTheirObjectId(TestContext context){
  for(String id:BloodborneBlocks.productionPalette().keySet()){
   ArchitectureBlock block=required(id);if(block.getStateManager().getProperty("facing")==null)continue;
   BlockState north=BloodborneBlocks.set(block.getDefaultState(),block.getStateManager().getProperty("facing"),"north");
   BlockState east=block.rotate(north,net.minecraft.util.BlockRotation.CLOCKWISE_90);
   var facing=east.getBlock().getStateManager().getProperty("facing");
   context.assertTrue(east.isOf(block)&&"east".equals(BloodborneBlocks.value(facing,east.get(facing))),"production facing rotates within one object: "+id);
  }
  context.complete();
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="production_palette_tree")
 public void c001PlacesAndBreaksAsOneWholeTreeItem(TestContext context){
  floor(context);ServerWorld world=context.getWorld();PlayerEntity player=context.createMockSurvivalPlayer();ArchitectureBlock tree=required("o_c001");
  try{
   moveOutside(context,player);context.useStackOnBlock(player,new ItemStack(tree),CLICK,Direction.UP);BlockPos root=find(context,tree);
   context.assertTrue(root!=null,"whole C001 tree item places one master");
   for(BlockPos pos:fixtureCells(context)){
    BlockState state=world.getBlockState(pos);if(state.getBlock() instanceof ArchitectureBlock block)context.assertTrue(block==tree,"tree placement never creates a branch/fragment architecture block");
   }
   clearDrops(world,root,tree);world.breakBlock(root,true,player);context.assertTrue(world.getBlockState(root).isAir(),"breaking the C001 master removes the whole tree");
   for(BlockPos pos:fixtureCells(context))context.assertTrue(!world.getBlockState(pos).isOf(BloodborneBlocks.PART_BLOCK),"tree break removes every owned helper");
   context.assertTrue(dropCount(world,root,tree)==1,"breaking the C001 master drops exactly one whole-tree item");clearDrops(world,root,tree);
   context.useStackOnBlock(player,new ItemStack(tree),CLICK,Direction.UP);root=find(context,tree);context.assertTrue(root!=null,"whole C001 tree item re-places one master");
   BlockPos helper=firstHelper(root,world.getBlockState(root));context.assertTrue(helper!=null,"whole C001 tree has an owned helper to break");
   world.breakBlock(helper,true,player);context.assertTrue(world.getBlockState(root).isAir(),"breaking an owned C001 helper removes its master");
   context.assertTrue(dropCount(world,root,tree)==1,"breaking an owned C001 helper drops exactly one whole-tree item");
   context.complete();
  }finally{clear(context,tree);player.discard();}
 }

 private static ArchitectureBlock required(String id){ArchitectureBlock block=BloodborneBlocks.BLOCKS.get(id);if(block==null)throw new AssertionError("missing production object "+id);return block;}
 private static List<String> visuals(ArchitectureBlock block){List<String> values=block.definition.properties.get("visual");return values==null?Collections.singletonList(null):values;}
 private static List<BlockPos> foreignContext(BlockPos root,Direction facing){Direction side=facing.rotateYClockwise();return List.of(root.offset(side,4),root.offset(side,4).up());}
 private static List<BlockPos> authenticPaneContext(BlockPos root,Direction facing){List<BlockPos> result=new ArrayList<>();for(int rawZ:List.of(-1,1))for(int y:List.of(0,1)){BlockPos offset=rotateFromNorth(new BlockPos(-rawZ,y,0),facing);result.add(root.add(offset));}return result;}
 private static BlockPos rotateFromNorth(BlockPos offset,Direction facing){int turns=switch(facing){case NORTH->0;case EAST->1;case SOUTH->2;case WEST->3;default->throw new IllegalArgumentException("horizontal facing required");};BlockPos result=offset;for(int turn=0;turn<turns;turn++)result=new BlockPos(-result.getZ(),result.getY(),result.getX());return result;}
 private static BlockPos helperOnly(BlockPos root,BlockState before,BlockState after){Set<BlockPos> prior=new LinkedHashSet<>(GeometryRuntime.state(before).parsedCells.keySet());return GeometryRuntime.state(after).parsedCells.keySet().stream().filter(offset->!offset.equals(BlockPos.ORIGIN)&&!prior.contains(offset)).map(root::add).findFirst().orElseThrow(()->new AssertionError("open C282 must reserve an outer leaf helper cell"));}
 private static boolean blocksDoorway(VoxelShape shape){return VoxelShapes.matchesAnywhere(shape,VoxelShapes.cuboid(.45,0,.45,.55,1,.55),BooleanBiFunction.AND);}
 private static void floor(TestContext context){context.setBlockState(CLICK,Blocks.WHITE_CONCRETE);}
 private static void moveOutside(TestContext context,PlayerEntity player){BlockPos pos=context.getAbsolutePos(new BlockPos(-6,4,-6));player.refreshPositionAndAngles(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);}
 private static BlockPos firstHelper(BlockPos root,BlockState state){return GeometryRuntime.state(state).parsedCells.keySet().stream().filter(offset->!offset.equals(BlockPos.ORIGIN)).map(root::add).findFirst().orElse(null);}
 private static int dropCount(ServerWorld world,BlockPos root,ArchitectureBlock block){return world.getEntitiesByClass(ItemEntity.class,new Box(root).expand(20),entity->entity.getStack().isOf(block.asItem())).stream().mapToInt(entity->entity.getStack().getCount()).sum();}
 private static void clearDrops(ServerWorld world,BlockPos root,ArchitectureBlock block){world.getEntitiesByClass(ItemEntity.class,new Box(root).expand(20),entity->entity.getStack().isOf(block.asItem())).forEach(ItemEntity::discard);}
 private static BlockPos find(TestContext context,Block block){for(BlockPos pos:fixtureCells(context))if(context.getWorld().getBlockState(pos).isOf(block))return pos;return null;}
 private static List<BlockPos> fixtureCells(TestContext context){List<BlockPos> result=new ArrayList<>();for(int x=-6;x<14;x++)for(int y=0;y<16;y++)for(int z=-6;z<14;z++)result.add(context.getAbsolutePos(new BlockPos(x,y,z)));return result;}
 private static void clear(TestContext context,ArchitectureBlock block){ServerWorld world=context.getWorld();for(BlockPos pos:fixtureCells(context)){BlockState state=world.getBlockState(pos);if(state.isOf(block)||state.isOf(BloodborneBlocks.PART_BLOCK))world.removeBlock(pos,false);}}
}
