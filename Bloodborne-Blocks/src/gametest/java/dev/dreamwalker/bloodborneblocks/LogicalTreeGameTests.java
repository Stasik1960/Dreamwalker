package dev.dreamwalker.bloodborneblocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

/** Regression coverage for the complete, single-item c001 tree contract. */
public final class LogicalTreeGameTests implements FabricGameTest {
 private static final BlockPos CLICK=new BlockPos(4,0,4);
 private static final BlockPos ROOT=CLICK.up();
 private static final Set<BlockPos> TREE_CELLS=Set.of(
  BlockPos.ORIGIN,new BlockPos(0,1,0),new BlockPos(0,2,0),new BlockPos(0,3,0),new BlockPos(0,4,0),
  new BlockPos(0,5,0),new BlockPos(0,6,0),new BlockPos(0,7,0),new BlockPos(0,8,0),new BlockPos(0,9,0));

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE, tickLimit=100, batchId="logical_c001_tree")
 public void completeTreeHasOneTallOwnedColumnAndSimpleShapes(TestContext context){
  ArchitectureBlock tree=required("o_c001");Property<?> variant=tree.getStateManager().getProperty("variant");
  context.assertTrue(variant!=null&&!variant.getValues().isEmpty(),"complete c001 tree retains its variant property");
  for(BlockState state:tree.getStateManager().getStates()){
   context.assertTrue(state.contains(Properties.HORIZONTAL_FACING),"complete c001 tree state has facing: "+state);
   context.assertTrue(TREE_CELLS.equals(GeometryRuntime.state(state).parsedCells.keySet()),"complete c001 tree owns only its 10-cell trunk: "+state);
   assertSingleBox(context,GeometryRuntime.rootShape(state,false),new Box(.25,0,.25,.75,1,.75),"complete c001 root-cell collision: "+state);
   VoxelShape trunk=VoxelShapes.empty();for(BlockPos offset:TREE_CELLS)trunk=VoxelShapes.union(trunk,GeometryRuntime.cellShape(state,offset,false).offset(offset.getX(),offset.getY(),offset.getZ()));
   assertSingleBox(context,trunk,new Box(.25,0,.25,.75,10,.75),"complete c001 aggregate trunk collision: "+state);
   assertSingleBox(context,GeometryRuntime.rootShape(state,true),new Box(0,0,0,1,10,1),"complete c001 outline: "+state);
   for(int x:List.of(-3,3))context.assertTrue(GeometryRuntime.cellShape(state,new BlockPos(x,4,0),false).isEmpty(),"visual branch has no collision cell: "+state+" x="+x);
  }
  context.complete();
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE, tickLimit=100, batchId="logical_c001_tree")
 public void completeTreePlacesPicksAndBreaksAsOneObject(TestContext context){
  floor(context);ServerWorld world=context.getWorld();PlayerEntity player=context.createMockCreativePlayer();ArchitectureBlock tree=required("o_c001");
  try{
   for(Direction yaw:Direction.Type.HORIZONTAL)for(boolean breakHelper:List.of(false,true)){
    clear(context,tree);moveOutside(context,player);player.setYaw(yaw.asRotation());BlockPos root=context.getAbsolutePos(ROOT),foreign=root.add(3,4,0);
    world.setBlockState(foreign,Blocks.LIGHT.getDefaultState(),Block.NOTIFY_ALL);
    ItemStack stack=new ItemStack(tree);player.setStackInHand(Hand.MAIN_HAND,stack);context.useStackOnBlock(player,stack,CLICK,Direction.UP);
    BlockState state=world.getBlockState(root);context.assertTrue(state.isOf(tree),"manual c001 placement keeps the clicked master for "+yaw);
    context.assertTrue(state.get(Properties.HORIZONTAL_FACING)==yaw.getOpposite(),"manual c001 placement stores yaw on the master for "+yaw);
    context.assertTrue(state.getBlock().getPickStack(world,root,state).isOf(tree.asItem()),"c001 master pick is the complete item for "+yaw);
    BlockPos helper=root.up();BlockState helperState=world.getBlockState(helper);context.assertTrue(helperState.isOf(BloodborneBlocks.PART_BLOCK),"c001 creates its vertical helper for "+yaw);
    context.assertTrue(helperState.getBlock().getPickStack(world,helper,helperState).isOf(tree.asItem()),"c001 helper pick is the complete item for "+yaw);
    if(breakHelper)world.breakBlock(helper,true,player);else world.breakBlock(root,true,player);
    for(BlockPos offset:TREE_CELLS)context.assertTrue(world.getBlockState(root.add(offset)).isAir(),"c001 removal clears owned tree cell "+offset+" for "+yaw);
    context.assertTrue(world.getBlockState(foreign).isOf(Blocks.LIGHT),"c001 removal preserves foreign visual branch cell for "+yaw);
    world.removeBlock(foreign,false);
   }
   context.complete();
  }finally{clear(context,tree);player.discard();}
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE, tickLimit=100, batchId="logical_c001_legacy_items")
 public void legacyTreeItemsPlaceTheCompleteHiddenTree(TestContext context){
  floor(context);ServerWorld world=context.getWorld();PlayerEntity player=context.createMockCreativePlayer();ArchitectureBlock tree=required("o_c001");
  try{
   context.assertTrue(!PaletteAliases.hidden(tree.definition.id),"complete c001 tree remains creative-visible");
   for(String id:List.of("o_c001_a","o_c001_b","o_c009_a","o_c009_b","o_dead_tree_planter")){
   ArchitectureBlock legacy=required(id);context.assertTrue(PaletteAliases.hidden(id),"legacy tree item is hidden: "+id);
   LogicalItemMigration.Result migrated=LogicalItemMigration.migrate(legacy,new ItemStack(legacy));context.assertTrue(migrated.matched()&&migrated.stack().isOf(tree.asItem()),"legacy tree item migrates directly to complete c001 item: "+id+" result="+migrated);
   BlockState legacyState=legacy.getDefaultState();context.assertTrue(legacyState.getBlock().getPickStack(world,context.getAbsolutePos(ROOT),legacyState).isOf(tree.asItem()),"legacy tree pick is the complete c001 item: "+id);
   clear(context,tree);moveOutside(context,player);BlockPos root=context.getAbsolutePos(ROOT);BlockState treeState=tree.getDefaultState();BlockPos conflict=GeometryRuntime.conflict(world,root,treeState,null);
   context.assertTrue(GeometryRuntime.canPlace(world,root,treeState),"complete c001 destination is available for legacy item: "+id+" conflict="+conflict+" occupied="+occupiedTreeCells(world,root));
   ItemStack stack=new ItemStack(legacy);player.setStackInHand(Hand.MAIN_HAND,stack);context.useStackOnBlock(player,stack,CLICK,Direction.UP);
   context.assertTrue(world.getBlockState(root).isOf(tree),"legacy tree item places complete c001 tree: "+id+" actualRootState="+world.getBlockState(root)+" nearbyRoots="+nearbyRoots(world,root));
    BlockState state=world.getBlockState(root);context.assertTrue(state.getBlock().getPickStack(world,root,state).isOf(tree.asItem()),"migrated tree master picks complete c001 item: "+id);
    world.breakBlock(root,false);
   }
   context.complete();
  }finally{clear(context,tree);player.discard();}
 }

 private static void floor(TestContext context){context.setBlockState(CLICK,Blocks.STONE);}
 private static ArchitectureBlock required(String id){ArchitectureBlock block=BloodborneBlocks.BLOCKS.get(id);if(block==null)throw new AssertionError("missing c001 tree fixture "+id);return block;}
 private static void moveOutside(TestContext context,PlayerEntity player){BlockPos pos=context.getAbsolutePos(new BlockPos(-6,4,-6));player.refreshPositionAndAngles(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);}
 private static void clear(TestContext context,ArchitectureBlock block){ServerWorld world=context.getWorld();for(int x=-4;x<12;x++)for(int y=-4;y<14;y++)for(int z=-4;z<12;z++){BlockPos pos=context.getAbsolutePos(new BlockPos(x,y,z));if(world.getBlockState(pos).isOf(block))world.breakBlock(pos,false);}}
 private static List<String> occupiedTreeCells(ServerWorld world,BlockPos root){List<String> cells=new ArrayList<>();for(BlockPos offset:TREE_CELLS){BlockPos pos=root.add(offset);if(!world.getBlockState(pos).isAir())cells.add(pos+"="+world.getBlockState(pos));}return cells;}
 private static List<String> nearbyRoots(ServerWorld world,BlockPos root){List<String> roots=new ArrayList<>();for(int x=-4;x<=4;x++)for(int y=-2;y<=12;y++)for(int z=-4;z<=4;z++){BlockPos pos=root.add(x,y,z);BlockState state=world.getBlockState(pos);if(state.getBlock() instanceof ArchitectureBlock block)roots.add(pos+"="+block.definition.id+" "+state);}return roots;}
 private static void assertSingleBox(TestContext context,VoxelShape shape,Box expected,String message){
  List<Box> boxes=new ArrayList<>();shape.forEachBox((minX,minY,minZ,maxX,maxY,maxZ)->boxes.add(new Box(minX,minY,minZ,maxX,maxY,maxZ)));
  context.assertTrue(boxes.size()==1,message+" box count="+boxes.size());Box actual=boxes.get(0);
  context.assertTrue(actual.minX==expected.minX&&actual.minY==expected.minY&&actual.minZ==expected.minZ&&actual.maxX==expected.maxX&&actual.maxY==expected.maxY&&actual.maxZ==expected.maxZ,message+" expected="+expected+" actual="+actual);
 }
}
