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
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
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

 @GameTest(templateName="bloodborne_blocks:practical_test_kit",tickLimit=600,batchId="window_test3")
 public void wallFirstMountsAtRearBoundaryForAllFacesAndVisualStates(TestContext context){
  ServerWorld world=context.getWorld();PlayerEntity player=context.createMockCreativePlayer();ArchitectureBlock window=required();
  try{for(Block backing:WALLS)for(Direction face:Direction.Type.HORIZONTAL)for(String visual:List.of("base","alt")){
   clear(context);BlockPos wall=context.getAbsolutePos(BASE.offset(face,5)),root=wall.offset(face);
   for(int dy=-1;dy<=2;dy++)placeVanilla(context,player,wall.up(dy),backing);
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

 @GameTest(templateName="bloodborne_blocks:practical_test_kit",tickLimit=900,batchId="window_test3")
 public void windowFirstAllowsNormalBackingEditsAndWholeCleanup(TestContext context){
  ServerWorld world=context.getWorld();PlayerEntity player=context.createMockCreativePlayer();ArchitectureBlock window=required();BlockPos root=context.getAbsolutePos(BASE);
  try{for(Block backing:WALLS)for(Direction facing:Direction.Type.HORIZONTAL)for(String visual:List.of("base","alt")){
   clearFixture(context,root);BlockPos floor=root.down();world.setBlockState(floor,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);player.refreshPositionAndAngles(root.getX()+10,root.getY()+6,root.getZ()+10,yawFor(facing),0);
   ItemStack windowStack=new ItemStack(window);if(visual.equals("alt"))windowStack.getOrCreateSubNbt("BlockStateTag").putString("visual","alt");context.assertTrue(use(player,windowStack,floor,Direction.UP).isAccepted(),"window-first item placement succeeds "+backing+" "+facing+" "+visual);
   BlockState placed=world.getBlockState(root);context.assertTrue(placed.isOf(window)&&placed.get(Properties.HORIZONTAL_FACING)==facing&&visual.equals(visual(placed)),"window-first item keeps requested facing and visual "+backing+" "+facing+" "+visual);assertCells(context,world,root,placed,"window-first closed");
   BlockPos back=root.offset(facing.getOpposite());placeBackPair(context,player,back,backing,"closed initial");BlockPos foreign=root.offset(facing.rotateYClockwise());placeVanilla(context,player,foreign,Blocks.GLASS);NbtCompound saved=GeometryRuntime.part(world,root.up()).createNbt();
   player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);context.assertTrue(placed.onUse(world,player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(root),facing,root,true)).isAccepted()&&world.getBlockState(root).get(Properties.OPEN),"window-first opens "+backing+" "+facing+" "+visual);context.assertTrue(GeometryRuntime.part(world,root.up()).createNbt().equals(saved),"open preserves helper NBT "+backing+" "+facing+" "+visual);removeAndRestoreBackPair(context,player,root,back,backing,"open");
   BlockState open=world.getBlockState(root);context.assertTrue(open.onUse(world,player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(root),facing,root,true)).isAccepted()&&!world.getBlockState(root).get(Properties.OPEN),"window-first closes "+backing+" "+facing+" "+visual);removeAndRestoreBackPair(context,player,root,back,backing,"closed");
   world.breakBlock(root,false,player);context.assertTrue(world.getBlockState(root).isAir()&&world.getBlockState(root.up()).isAir()&&world.getBlockState(back).isOf(backing)&&world.getBlockState(back.up()).isOf(backing),"whole removal preserves both independently placed backing cells "+backing+" "+facing+" "+visual);
   context.assertTrue(world.getBlockState(foreign).isOf(Blocks.GLASS)&&world.getBlockState(floor).isOf(Blocks.STONE),"window removal preserves side neighbor and lower support");
   ItemStack again=new ItemStack(window);again.getOrCreateSubNbt("BlockStateTag").putString("visual",visual);context.assertTrue(use(player,again,back,facing).isAccepted(),"window can be re-added against backing");assertCells(context,world,root,world.getBlockState(root),"re-added");world.breakBlock(root.up(),false,player);context.assertTrue(world.getBlockState(root).isAir()&&world.getBlockState(root.up()).isAir()&&world.getBlockState(back).isOf(backing)&&world.getBlockState(foreign).isOf(Blocks.GLASS),"upper removal also preserves independent neighbors");
  }context.complete();}finally{clearFixture(context,root);player.discard();}
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

 private static void placeBackPair(TestContext context,PlayerEntity player,BlockPos back,Block block,String label){placeVanilla(context,player,back,block);placeVanilla(context,player,back.up(),block);context.assertTrue(context.getWorld().getBlockState(back).isOf(block)&&context.getWorld().getBlockState(back.up()).isOf(block),"ordinary items place both backing cells "+label);}
 private static void removeAndRestoreBackPair(TestContext context,PlayerEntity player,BlockPos root,BlockPos back,Block block,String label){ServerWorld world=context.getWorld();BlockState before=world.getBlockState(root);NbtCompound nbt=GeometryRuntime.part(world,root.up()).createNbt();ItemStack sentinel=new ItemStack(Blocks.GLASS,3);player.setStackInHand(Hand.MAIN_HAND,sentinel);world.breakBlock(back,false,player);world.breakBlock(back.up(),false,player);context.assertTrue(world.getBlockState(back).isAir()&&world.getBlockState(back.up()).isAir()&&sentinel.getCount()==3,"both ordinary backing cells can be removed without consuming held items while "+label);assertCells(context,world,root,before,label+" backing removed");placeBackPair(context,player,back,block,label+" restore");assertCells(context,world,root,before,label+" backing restored");context.assertTrue(nbt.equals(GeometryRuntime.part(world,root.up()).createNbt()),"backing edits preserve exact helper NBT "+label);}
 private static float yawFor(Direction facing){return switch(facing){case NORTH->0F;case EAST->90F;case SOUTH->180F;case WEST->270F;default->throw new AssertionError(facing);};}
 private static void clearFixture(TestContext context,BlockPos root){ServerWorld world=context.getWorld();for(int x=-4;x<=4;x++)for(int y=-4;y<=6;y++)for(int z=-4;z<=4;z++)world.removeBlock(root.add(x,y,z),false);}
 private static void placeVanilla(TestContext context,PlayerEntity player,BlockPos target,Block block){
  ServerWorld world=context.getWorld();BlockPos support=target.down();if(world.getBlockState(support).isAir())world.setBlockState(support,Blocks.DEEPSLATE.getDefaultState(),Block.NOTIFY_ALL);ItemStack stack=new ItemStack(block);context.assertTrue(use(player,stack,support,Direction.UP).isAccepted(),"normal "+block+" item action at "+target);
 }
 private static void assertCells(TestContext context,ServerWorld world,BlockPos root,BlockState state,String label){
  context.assertTrue(world.getBlockState(root).equals(state),"placed root state is preserved "+label);
  ArchitecturePartBlockEntity part=GeometryRuntime.part(world,root.up());var owner=Registries.BLOCK.getId(state.getBlock());context.assertTrue(part!=null&&part.bindings().size()==1&&part.hasBinding(root,owner),"one exact upper helper binding "+label);
  ArchitecturePartBlockEntity decoded=new ArchitecturePartBlockEntity(root.up(),world.getBlockState(root.up()));decoded.readNbt(part.createNbt());context.assertTrue(decoded.bindings().equals(part.bindings()),"helper ownership survives NBT read/write "+label);
  context.assertTrue(NbtHelper.toBlockState(world.getRegistryManager().getWrapperOrThrow(RegistryKeys.BLOCK),NbtHelper.fromBlockState(state)).equals(state),"facing/open/art state survives NBT read/write "+label);
  context.assertTrue(GeometryRuntime.state(state).parsedCells.keySet().equals(Set.of(BlockPos.ORIGIN,BlockPos.ORIGIN.up())),"only root and upper helper are owned "+label);
  Box outline=GeometryRuntime.rootShape(state,true).getBoundingBox();context.assertTrue(box(outline,2),"selection is exactly the two physical cells "+label);
  for(BlockPos offset:Set.of(BlockPos.ORIGIN,BlockPos.ORIGIN.up())){VoxelShape collision=GeometryRuntime.cellShape(state,offset,false),selection=GeometryRuntime.cellShape(state,offset,true);context.assertTrue(box(collision.getBoundingBox(),1)&&box(selection.getBoundingBox(),1),"collision and selection are full local cells "+label+" "+offset);}
 }
 private static void assertMounted(TestContext context,BlockState state,Direction facing,String label){
  double[] offset=GeometryRuntime.renderOffset("o_shuttered_window",BloodborneBlocks.key(state));context.assertTrue(offset!=null&&Math.abs(offset[1]-.875)<1e-6,"all shutter poses retain the measured sill offset "+label);
  context.assertTrue(box(GeometryRuntime.rootShape(state,true).getBoundingBox(),2),"render decoration cannot expand interaction volume "+label);
 }
 private static boolean box(Box box,double height){return Math.abs(box.minX)<1e-6&&Math.abs(box.minY)<1e-6&&Math.abs(box.minZ)<1e-6&&Math.abs(box.maxX-1)<1e-6&&Math.abs(box.maxY-height)<1e-6&&Math.abs(box.maxZ-1)<1e-6;}
 private static ActionResult use(PlayerEntity player,ItemStack stack,BlockPos clicked,Direction side){player.setStackInHand(Hand.MAIN_HAND,stack);return stack.useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(clicked),side,clicked,false)));}
 @SuppressWarnings({"rawtypes","unchecked"}) private static String visual(BlockState state){var property=state.getBlock().getStateManager().getProperty("visual");return BloodborneBlocks.value((net.minecraft.state.property.Property)property,(Comparable)state.get((net.minecraft.state.property.Property)property));}
 private static BlockState withVisual(BlockState state,String visual){return BloodborneBlocks.set(state,state.getBlock().getStateManager().getProperty("visual"),visual);}
 private static ArchitectureBlock required(){ArchitectureBlock window=BloodborneBlocks.BLOCKS.get("o_shuttered_window");if(window==null)throw new AssertionError("missing shuttered window");return window;}
 private static void clear(TestContext context){ServerWorld world=context.getWorld();for(int x=0;x<32;x++)for(int y=0;y<20;y++)for(int z=0;z<32;z++)world.removeBlock(context.getAbsolutePos(new BlockPos(x,y,z)),false);}
}
