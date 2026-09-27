package dev.dreamwalker.bloodborneblocks;

import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

/** Item-driven TEST3 coverage for a normal wall-backed shutter window. */
public final class WindowTest3GameTests implements FabricGameTest {
 private static final BlockPos BASE=new BlockPos(16,8,16);
 private static final List<Block> WALLS=List.of(Blocks.LIME_WOOL,Blocks.STONE_BRICKS,Blocks.OAK_PLANKS,Blocks.GLASS);

 @GameTest(templateName="bloodborne_blocks:practical_test_kit",tickLimit=220,batchId="window_test3")
 public void wallFirstMountsAtRearBoundaryForAllFacesAndVisualStates(TestContext context){
  ServerWorld world=context.getWorld();PlayerEntity player=context.createMockCreativePlayer();ArchitectureBlock window=required();
  try{int index=0;for(Direction face:Direction.Type.HORIZONTAL)for(String visual:List.of("base","alt")){
   clear(context);BlockPos wall=context.getAbsolutePos(BASE.offset(face,5)),root=wall.offset(face);Block backing=WALLS.get(index++%WALLS.size());
   for(int dy=-1;dy<=2;dy++)world.setBlockState(wall.up(dy),backing.getDefaultState(),Block.NOTIFY_ALL);
   ItemStack held=new ItemStack(window);if(visual.equals("alt"))held.getOrCreateSubNbt("BlockStateTag").putString("visual","alt");
   context.assertTrue(use(player,held,wall,face).isAccepted(),"wall-first item placement succeeds "+face+" "+visual);
   BlockState placed=world.getBlockState(root);context.assertTrue(placed.isOf(window)&&placed.get(Properties.HORIZONTAL_FACING)==face&&visual.equals(visual(placed)),"clicked face and item visual determine state "+face+" "+visual);
   context.assertTrue(world.getBlockState(wall).isOf(backing)&&world.getBlockState(wall.up()).isOf(backing),"ordinary backing is not changed "+face);
   assertMounted(context,placed,face,"closed "+face);assertCells(context,world,root,placed,"closed "+face);
   NbtCompound nbt=GeometryRuntime.part(world,root.up()).createNbt();player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);
   context.assertTrue(placed.onUse(world,player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(root),face,root,true)).isAccepted(),"real shutter use opens "+face);
   BlockState opened=world.getBlockState(root);context.assertTrue(opened.get(Properties.OPEN)&&world.getBlockState(wall).isOf(backing),"open shutters leave solid backing untouched "+face);
   context.assertTrue(GeometryRuntime.part(world,root.up()).createNbt().equals(nbt),"open shutters preserve helper NBT "+face);assertMounted(context,opened,face,"open "+face);assertCells(context,world,root,opened,"open "+face);
   context.assertTrue(opened.onUse(world,player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(root),face,root,true)).isAccepted()&&!world.getBlockState(root).get(Properties.OPEN),"real shutter use closes at fixed root "+face);
  }context.complete();}finally{clear(context);player.discard();}
 }

 @GameTest(templateName="bloodborne_blocks:practical_test_kit",tickLimit=220,batchId="window_test3")
 public void windowFirstAllowsNormalBackingEditsAndWholeCleanup(TestContext context){
  ServerWorld world=context.getWorld();PlayerEntity player=context.createMockCreativePlayer();ArchitectureBlock window=required();BlockPos root=context.getAbsolutePos(BASE);
  try{
   BlockPos floor=root.down();world.setBlockState(floor,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);ItemStack windowStack=new ItemStack(window);context.assertTrue(use(player,windowStack,floor,Direction.UP).isAccepted(),"free-standing item placement succeeds");
   BlockState placed=world.getBlockState(root);Direction facing=placed.get(Properties.HORIZONTAL_FACING),back=facing.getOpposite();BlockPos column=root.offset(back);
   for(int dy=-1;dy<=2;dy++){BlockPos target=column.up(dy);Block material=List.of(Blocks.LIME_WOOL,Blocks.STONE,Blocks.BRICKS,Blocks.OAK_PLANKS).get(dy+1);placeVanilla(context,player,target,material);context.assertTrue(world.getBlockState(target).isOf(material),"normal backing item fills independent column dy="+dy);}
   BlockPos foreign=root.offset(facing.rotateYClockwise());placeVanilla(context,player,foreign,Blocks.GLASS);ItemStack foreignHeld=new ItemStack(Blocks.GLASS,3);player.setStackInHand(Hand.MAIN_HAND,foreignHeld);
   BlockPos middle=column;world.breakBlock(middle,false,player);context.assertTrue(world.getBlockState(root).isOf(window)&&world.getBlockState(root.up()).isOf(BloodborneBlocks.PART_BLOCK)&&world.getBlockState(foreign).isOf(Blocks.GLASS)&&foreignHeld.getCount()==3,"removing ordinary backing preserves window, helper, foreign block and held item");
   placeVanilla(context,player,middle,Blocks.STONE);context.assertTrue(world.getBlockState(middle).isOf(Blocks.STONE)&&world.getBlockState(root).isOf(window),"ordinary backing re-add stays independent");
   ArchitecturePartBlockEntity helper=GeometryRuntime.part(world,root.up());NbtCompound saved=helper.createNbt();ArchitecturePartBlockEntity decoded=new ArchitecturePartBlockEntity(root.up(),world.getBlockState(root.up()));decoded.readNbt(saved);context.assertTrue(decoded.hasBinding(root,Registries.BLOCK.getId(window)),"window helper binding survives NBT encode/decode");
   world.breakBlock(root,false,player);context.assertTrue(world.getBlockState(root).isAir()&&world.getBlockState(root.up()).isAir(),"breaking whole window removes its only helper");
   context.assertTrue(world.getBlockState(middle).isOf(Blocks.STONE)&&world.getBlockState(foreign).isOf(Blocks.GLASS),"whole cleanup never removes independent backing or foreign block");
   ItemStack again=new ItemStack(window);context.assertTrue(use(player,again,middle,facing).isAccepted()&&world.getBlockState(middle.offset(facing)).isOf(window),"window can be re-added by item against normal backing");context.complete();
  }finally{clear(context);player.discard();}
 }

 @GameTest(templateName="bloodborne_blocks:practical_test_kit",tickLimit=180,batchId="window_test3")
 public void blockedUpperCellAndUpperBreakDoNotLeaveAnOrphan(TestContext context){
  ServerWorld world=context.getWorld();PlayerEntity player=context.createMockCreativePlayer();ArchitectureBlock window=required();BlockPos wall=context.getAbsolutePos(BASE.offset(Direction.NORTH,5)),root=wall.north();
  try{
   world.setBlockState(wall,Blocks.LIME_WOOL.getDefaultState(),Block.NOTIFY_ALL);world.setBlockState(root.up(),Blocks.GLASS.getDefaultState(),Block.NOTIFY_ALL);
   ItemStack blocked=new ItemStack(window);context.assertTrue(!use(player,blocked,wall,Direction.NORTH).isAccepted()&&blocked.getCount()==1,"foreign upper cell rejects window item without consuming it");
   context.assertTrue(world.getBlockState(wall).isOf(Blocks.LIME_WOOL)&&world.getBlockState(root).isAir()&&world.getBlockState(root.up()).isOf(Blocks.GLASS),"blocked placement preserves wall and foreign upper block");
   world.breakBlock(root.up(),false,player);ItemStack placed=new ItemStack(window);context.assertTrue(use(player,placed,wall,Direction.NORTH).isAccepted()&&world.getBlockState(root).isOf(window)&&world.getBlockState(root.up()).isOf(BloodborneBlocks.PART_BLOCK),"window item succeeds after upper foreign block is removed");
   ItemStack sentinel=new ItemStack(Blocks.GLASS,3);player.setStackInHand(Hand.MAIN_HAND,sentinel);world.breakBlock(root.up(),false,player);
   context.assertTrue(world.getBlockState(root).isAir()&&world.getBlockState(root.up()).isAir()&&sentinel.getCount()==3,"breaking the only helper removes root without orphaning or consuming held items");
   ItemStack again=new ItemStack(window);context.assertTrue(use(player,again,wall,Direction.NORTH).isAccepted()&&world.getBlockState(root).isOf(window),"window can be re-added after upper cleanup");context.complete();
  }finally{clear(context);player.discard();}
 }

 @GameTest(templateName="bloodborne_blocks:practical_test_kit",tickLimit=100,batchId="window_test3")
 public void everyFacingVisualAndOpenStateHasOneFixedMountTransform(TestContext context){
  try{ArchitectureBlock window=required();for(Direction facing:Direction.Type.HORIZONTAL)for(boolean open:new boolean[]{false,true})for(String visual:List.of("base","alt")){
   BlockState state=withVisual(window.getDefaultState().with(Properties.HORIZONTAL_FACING,facing).with(Properties.OPEN,open),visual);assertMounted(context,state,facing,facing+"/"+open+"/"+visual);
  }context.complete();}finally{clear(context);}
 }

 private static void placeVanilla(TestContext context,PlayerEntity player,BlockPos target,Block block){
  ServerWorld world=context.getWorld();BlockPos support=target.down();if(world.getBlockState(support).isAir())world.setBlockState(support,Blocks.DEEPSLATE.getDefaultState(),Block.NOTIFY_ALL);ItemStack stack=new ItemStack(block);context.assertTrue(use(player,stack,support,Direction.UP).isAccepted(),"normal "+block+" item action at "+target);
 }
 private static void assertCells(TestContext context,ServerWorld world,BlockPos root,BlockState state,String label){
  context.assertTrue(GeometryRuntime.state(state).parsedCells.keySet().equals(Set.of(BlockPos.ORIGIN,BlockPos.ORIGIN.up())),"only root and upper helper are owned "+label);
  for(BlockPos offset:Set.of(BlockPos.ORIGIN,BlockPos.ORIGIN.up())){VoxelShape shape=GeometryRuntime.cellShape(state,offset,false);Box box=shape.getBoundingBox();context.assertTrue(!shape.isEmpty()&&box.minX>=0&&box.maxX<=1&&box.minY>=0&&box.maxY<=1&&box.minZ>=0&&box.maxZ<=1,"collision remains cell-local "+label+" "+offset);}
 }
 private static void assertMounted(TestContext context,BlockState state,Direction facing,String label){
  Box selection=GeometryRuntime.rootShape(state,true).getBoundingBox();context.assertTrue(Math.abs(selection.minY)<1e-6,"sill support edge is at local support plane "+label);
  VoxelShape collision=GeometryRuntime.cellShape(state,BlockPos.ORIGIN,false);Box box=collision.getBoundingBox();
  boolean open=state.get(Properties.OPEN);double[] offset=GeometryRuntime.renderOffset("o_shuttered_window",BloodborneBlocks.key(state));
  context.assertTrue(offset!=null&&Math.abs(offset[1]-.875)<1e-6,"all shutter poses retain the measured sill offset "+label);
  switch(facing){
   case NORTH->{context.assertTrue((open||Math.abs(selection.maxZ-1)<1e-6)&&box.minZ>=.875&&box.maxZ<=1,"north stationary frame/collision touch rear wall boundary "+label);}
   case EAST->{context.assertTrue((open||Math.abs(selection.minX)<1e-6)&&box.minX>=0&&box.maxX<=.125,"east stationary frame/collision touch rear wall boundary "+label);}
   case SOUTH->{context.assertTrue((open||Math.abs(selection.minZ)<1e-6)&&box.minZ>=0&&box.maxZ<=.125,"south stationary frame/collision touch rear wall boundary "+label);}
   case WEST->{context.assertTrue((open||Math.abs(selection.maxX-1)<1e-6)&&box.minX>=.875&&box.maxX<=1,"west stationary frame/collision touch rear wall boundary "+label);}
   default->throw new AssertionError(facing);
  }
 }
 private static ActionResult use(PlayerEntity player,ItemStack stack,BlockPos clicked,Direction side){player.setStackInHand(Hand.MAIN_HAND,stack);return stack.useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(clicked),side,clicked,false)));}
 @SuppressWarnings({"rawtypes","unchecked"}) private static String visual(BlockState state){var property=state.getBlock().getStateManager().getProperty("visual");return BloodborneBlocks.value((net.minecraft.state.property.Property)property,(Comparable)state.get((net.minecraft.state.property.Property)property));}
 private static BlockState withVisual(BlockState state,String visual){return BloodborneBlocks.set(state,state.getBlock().getStateManager().getProperty("visual"),visual);}
 private static ArchitectureBlock required(){ArchitectureBlock window=BloodborneBlocks.BLOCKS.get("o_shuttered_window");if(window==null)throw new AssertionError("missing shuttered window");return window;}
 private static void clear(TestContext context){ServerWorld world=context.getWorld();for(int x=0;x<32;x++)for(int y=0;y<20;y++)for(int z=0;z<32;z++)world.removeBlock(context.getAbsolutePos(new BlockPos(x,y,z)),false);}
}
