package dev.dreamwalker.bloodborneblocks;

import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
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
import net.minecraft.util.shape.VoxelShapes;

/** Client-QA coverage for the ordinary shutter window and authored ladders. */
public final class AgonySurfaceGameTests implements FabricGameTest {
 private static final BlockPos BASE=new BlockPos(4,3,4);

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=180,batchId="agony_surface")
 public void shutterPlacesBesideWallWithoutMutatingItForEachFacing(TestContext context){
  ServerWorld world=context.getWorld();PlayerEntity player=context.createMockCreativePlayer();ArchitectureBlock window=required("o_shuttered_window");
  try{for(Direction side:Direction.Type.HORIZONTAL){
   clearArea(context);BlockPos wall=context.getAbsolutePos(BASE.offset(side,5)),root=wall.offset(side);world.setBlockState(wall,Blocks.DIAMOND_ORE.getDefaultState(),Block.NOTIFY_ALL);
   ItemStack stack=new ItemStack(window,5);ActionResult result=use(window,world,player,stack,wall,side);BlockState placed=world.getBlockState(root);
   context.assertTrue(result.isAccepted()&&placed.isOf(window),"ordinary shutter is placed in free space beside the clicked wall "+side);
   context.assertTrue(world.getBlockState(wall).isOf(Blocks.DIAMOND_ORE),"ordinary shutter never replaces or mutates its background "+side);
   context.assertTrue(placed.get(Properties.HORIZONTAL_FACING)==side,"shutter facing follows clicked wall face "+side);
   context.assertTrue(stack.getCount()==4&&GeometryRuntime.part(world,root.up())!=null,"one item creates the exact two-cell object "+side);
  }context.complete();}finally{clearArea(context);player.discard();}
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=120,batchId="agony_surface")
 public void shutterOwnsOnlyRootAndUpperCellAndLeavesSidesPlaceable(TestContext context){
  ServerWorld world=context.getWorld();PlayerEntity player=context.createMockCreativePlayer();ArchitectureBlock window=required("o_shuttered_window");BlockPos wall=context.getAbsolutePos(BASE),root=wall.north();
  try{
   world.setBlockState(wall,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);ItemStack stack=new ItemStack(window);context.assertTrue(use(window,world,player,stack,wall,Direction.NORTH).isAccepted(),"ordinary shutter placement succeeds");
   BlockState state=world.getBlockState(root);Set<BlockPos> cells=GeometryRuntime.state(state).parsedCells.keySet();context.assertTrue(cells.equals(Set.of(BlockPos.ORIGIN,BlockPos.ORIGIN.up())),"shutter physical ownership is exactly one block wide and two blocks high");
   for(BlockPos offset:cells){VoxelShape shape=GeometryRuntime.cellShape(state,offset,false);Box bounds=shape.getBoundingBox();context.assertTrue(!shape.isEmpty()&&bounds.minX>=0&&bounds.maxX<=1&&bounds.minY>=0&&bounds.maxY<=1&&bounds.minZ>=0&&bounds.maxZ<=1,"shutter collision stays cell-local "+offset);context.assertTrue(bounds.maxX-bounds.minX<.2||bounds.maxZ-bounds.minZ<.2,"shutter collision remains a thin plane "+offset);}
   BlockPos left=root.west(),right=root.east();world.setBlockState(left,Blocks.WHITE_CONCRETE.getDefaultState(),Block.NOTIFY_ALL);world.setBlockState(right,Blocks.WHITE_CONCRETE.getDefaultState(),Block.NOTIFY_ALL);
   context.assertTrue(world.getBlockState(left).isOf(Blocks.WHITE_CONCRETE)&&world.getBlockState(right).isOf(Blocks.WHITE_CONCRETE),"ordinary blocks remain placeable on both sides of the shutter");context.complete();
  }finally{clearArea(context);player.discard();}
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=120,batchId="agony_surface")
 public void shutterToggleKeepsAnchorHelpersAndBackground(TestContext context){
  ServerWorld world=context.getWorld();PlayerEntity player=context.createMockCreativePlayer();ArchitectureBlock window=required("o_shuttered_window");BlockPos wall=context.getAbsolutePos(BASE),root=wall.north();
  try{
   world.setBlockState(wall,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);context.assertTrue(use(window,world,player,new ItemStack(window),wall,Direction.NORTH).isAccepted(),"shutter fixture places");BlockState closed=world.getBlockState(root);
   player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);ActionResult toggled=closed.onUse(world,player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(root),Direction.NORTH,root,true));BlockState open=world.getBlockState(root);
   context.assertTrue(toggled.isAccepted()&&open.get(Properties.OPEN),"ordinary shutter retains open/closed interaction");
   context.assertTrue(world.getBlockState(wall).isOf(Blocks.STONE)&&GeometryRuntime.part(world,root.up())!=null&&GeometryRuntime.state(open).parsedCells.keySet().equals(Set.of(BlockPos.ORIGIN,BlockPos.ORIGIN.up())),"toggle preserves background, anchor, and exact helper column");context.complete();
  }finally{clearArea(context);player.discard();}
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=200,batchId="agony_surface")
 public void ladderStacksFromRootAndHelperThenCleansOwnedCells(TestContext context){
  ServerWorld world=context.getWorld();PlayerEntity player=context.createMockCreativePlayer();ArchitectureBlock ladder=required("o_ladder_03"),landing=required("o_ladder_01");
  try{for(Direction side:Direction.Type.HORIZONTAL){
   clearArea(context);BlockPos wall=context.getAbsolutePos(BASE.offset(side,5)),root=wall.offset(side);backing(world,root,side);world.setBlockState(wall,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
   ItemStack stack=new ItemStack(ladder,5);context.assertTrue(use(ladder,world,player,stack,wall,side).isAccepted(),"ladder attaches to wall "+side);BlockState state=world.getBlockState(root);
   context.assertTrue(state.isOf(ladder)&&state.get(Properties.HORIZONTAL_FACING)==side&&GeometryRuntime.rebuild(world,root,state),"two-cell ladder root "+side);
   VoxelShape thin=GeometryRuntime.rootShape(state,false);Box planeBounds=thin.getBoundingBox();context.assertTrue(planeBounds.maxX-planeBounds.minX<.2||planeBounds.maxZ-planeBounds.minZ<.2,"ladder lateral collision is a thin plane "+side);assertInvertedPlane(context,thin,side);assertPhysicalPlane(context,thin,side);
   context.assertTrue(FunctionalFurniture.isClimbable(world,root),"ladder root climbable "+side);player.refreshPositionAndAngles(root.getX()+.5+side.getOffsetX()*.4,root.getY(),root.getZ()+.5+side.getOffsetZ()*.4,0,0);context.assertTrue(player.isClimbing(),"player climbs at inverted plane contact "+side);moveOutside(context,player);
   BlockPos two=root.up(2),four=root.up(4),six=root.up(6),eight=root.up(8);backing(world,two,side);context.assertTrue(use(ladder,world,player,stack,root,Direction.UP).isAccepted(),"second root stacks "+side);
   backing(world,four,side);context.assertTrue(use(ladder,world,player,stack,two.down(),Direction.UP).isAccepted(),"second helper stacks third root "+side);
   backing(world,six,side);context.assertTrue(use(ladder,world,player,stack,four,Direction.UP).isAccepted(),"third root stacks fourth root "+side);
   backing(world,eight,side);context.assertTrue(use(ladder,world,player,stack,six.down(),Direction.UP).isAccepted()&&stack.isEmpty(),"fourth helper stacks fifth root and consumes all five "+side);
   for(int dy=-1;dy<=8;dy++){BlockPos owned=root.up(dy);context.assertTrue(FunctionalFurniture.isClimbable(world,owned),"every owned cell is continuously climbable at dy="+dy+" "+side);}
   world.breakBlock(eight.down(),false,player);context.assertTrue(world.getBlockState(eight).isAir()&&roots(world,ladder,root,two,four,six),"breaking top helper preserves four lower roots "+side);
   ItemStack repairTop=new ItemStack(ladder);backing(world,eight,side);context.assertTrue(use(ladder,world,player,repairTop,six,Direction.UP).isAccepted()&&world.getBlockState(eight).isOf(ladder),"top root re-places "+side);
   world.breakBlock(four.down(),false,player);context.assertTrue(world.getBlockState(four).isAir()&&roots(world,ladder,root,two,six,eight),"breaking middle helper preserves other roots "+side);
   ItemStack repairMiddle=new ItemStack(ladder);backing(world,four,side);context.assertTrue(use(ladder,world,player,repairMiddle,two,Direction.UP).isAccepted()&&roots(world,ladder,root,two,four,six,eight),"middle root re-places without disturbing stack "+side);
   BlockPos landingRoot=root.offset(side.rotateYClockwise(),3);world.setBlockState(landingRoot,landing.getDefaultState(),Block.NOTIFY_ALL);context.assertTrue(GeometryRuntime.rebuild(world,landingRoot,world.getBlockState(landingRoot)),"landing rebuilds beside ladder "+side);BlockState landingState=world.getBlockState(landingRoot);context.assertTrue(GeometryRuntime.rootShape(landingState,false).getBoundingBox().maxY>=.75&&!FunctionalFurniture.isClimbable(world,landingRoot),"landing remains standable/non-climbable beside ladder "+side);for(var entry:GeometryRuntime.state(landingState).parsedCells.entrySet())context.assertTrue(!GeometryRuntime.cellShape(landingState,entry.getKey(),false).isEmpty(),"every visible deck cell has walkable support "+entry.getKey()+" "+side);
  }context.complete();}finally{clearArea(context);player.discard();}
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="agony_transition")
 public void ladderContactClimbsAndTopCanReachStandableLanding(TestContext context){
  ServerWorld world=context.getWorld();PlayerEntity player=context.createMockSurvivalPlayer();ArchitectureBlock ladder=required("o_ladder_03"),landing=required("o_ladder_01");BlockPos root=context.getAbsolutePos(BASE);
  try{
   for(int n=0;n<5;n++){BlockPos step=root.up(n*2);backing(world,step,Direction.NORTH);world.setBlockState(step,ladder.getDefaultState(),Block.NOTIFY_ALL);context.assertTrue(GeometryRuntime.rebuild(world,step,world.getBlockState(step)),"ladder stack fixture rebuilds");}
   BlockPos top=root.up(8),deck=top.north(2);world.setBlockState(deck,landing.getDefaultState(),Block.NOTIFY_ALL);context.assertTrue(GeometryRuntime.rebuild(world,deck,world.getBlockState(deck)),"landing beside actual stack does not consume ladder ownership");
   player.refreshPositionAndAngles(top.getX()+.5,top.getY()-.6,top.getZ()-.3,0,0);player.setVelocity(0,0,0);context.assertTrue(player.isClimbing(),"player contacts the inverted ladder plane before travel");double startY=player.getY();
   for(int tick=0;tick<5;tick++)player.travel(new Vec3d(0,0,1));
   context.assertTrue(player.getY()>startY+.1,"vanilla player travel ascends while pushing into the ladder plane");
   // Probe the authored exit path from the top: a short horizontal transfer,
   // followed by falling onto the separate deck. It is not a flush walkway.
   player.refreshPositionAndAngles(top.getX()+.5,top.getY()+1,top.getZ()+.55,0,0);
   player.move(MovementType.SELF,new Vec3d(0,0,-1.1));player.move(MovementType.SELF,new Vec3d(0,-1,0));
   context.assertTrue(Math.abs(player.getY()-(deck.getY()+.8125))<1e-5,"exit lands on the platform top without falling through");
   context.assertTrue(world.getBlockState(top).isOf(ladder)&&world.getBlockState(deck).isOf(landing),"separate ladder and platform remain intact");context.complete();
  }finally{clearArea(context);player.discard();}
 }

 private static ActionResult use(ArchitectureBlock block,ServerWorld world,PlayerEntity player,ItemStack stack,BlockPos pos,Direction side){return ((ArchitectureBlockItem)block.asItem()).useOnBlock(new ItemUsageContext(world,player,Hand.MAIN_HAND,stack,new BlockHitResult(Vec3d.ofCenter(pos),side,pos,false)));}
 private static boolean roots(ServerWorld world,ArchitectureBlock ladder,BlockPos... roots){for(BlockPos root:roots)if(!world.getBlockState(root).isOf(ladder))return false;return true;}
 private static void backing(ServerWorld world,BlockPos root,Direction side){Direction back=side.getOpposite();world.setBlockState(root.down().offset(back),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);world.setBlockState(root.offset(back),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);}
 private static void assertInvertedPlane(TestContext context,VoxelShape shape,Direction side){Box bounds=shape.getBoundingBox();switch(side){case NORTH->context.assertTrue(bounds.minZ<.001&&bounds.maxZ<=.126,"north ladder plane is inverted to the opposite block edge");case EAST->context.assertTrue(bounds.minX>=.874&&bounds.maxX>0.999,"east ladder plane is the rotated inverted edge");case SOUTH->context.assertTrue(bounds.minZ>=.874&&bounds.maxZ>0.999,"south ladder plane is the rotated inverted edge");case WEST->context.assertTrue(bounds.minX<.001&&bounds.maxX<=.126,"west ladder plane is the rotated inverted edge");default->throw new AssertionError(side);}}
 private static void assertPhysicalPlane(TestContext context,VoxelShape shape,Direction side){Box bounds=shape.getBoundingBox();Direction.Axis axis=side.getAxis();double requested;Box mover;if(axis==Direction.Axis.X){requested=bounds.minX>.5?1:-1;mover=new Box(bounds.minX>.5?.2:bounds.maxX+.01,.2,.25,bounds.minX>.5?bounds.minX-.01:.8,1.6,.75);}else{requested=bounds.minZ>.5?1:-1;mover=new Box(.25,.2,bounds.minZ>.5?.2:bounds.maxZ+.01,.75,1.6,bounds.minZ>.5?bounds.minZ-.01:.8);}double allowed=VoxelShapes.calculateMaxOffset(axis,mover,List.of(shape),requested);context.assertTrue(Math.abs(allowed)<Math.abs(requested),"thin plane blocks lateral movement");}
 private static void moveOutside(TestContext context,PlayerEntity player){BlockPos pos=context.getAbsolutePos(new BlockPos(-12,5,-12));player.refreshPositionAndAngles(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);}
 private static ArchitectureBlock required(String id){ArchitectureBlock block=BloodborneBlocks.BLOCKS.get(id);if(block==null)throw new AssertionError("missing surface fixture "+id);return block;}
 private static void clearArea(TestContext context){ServerWorld world=context.getWorld();for(int x=-10;x<20;x++)for(int y=-2;y<16;y++)for(int z=-10;z<20;z++)world.removeBlock(context.getAbsolutePos(new BlockPos(x,y,z)),false);}
}
