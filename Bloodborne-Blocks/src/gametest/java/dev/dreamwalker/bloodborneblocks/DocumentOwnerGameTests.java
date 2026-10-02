package dev.dreamwalker.bloodborneblocks;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import java.util.*;

/** Dedicated-server behavior checks for manually authored final-document owners. */
public final class DocumentOwnerGameTests implements FabricGameTest {
 private static final BlockPos ROOT=new BlockPos(8,5,8);

 @GameTest(templateName="bloodborne_blocks:practical_test_kit",tickLimit=120,batchId="document_owners")
 public void final01PlacesCanonicalAcrossYawAndBreaksItsWholeHelperSet(TestContext context){
  ServerWorld world=context.getWorld();PlayerEntity player=context.createMockSurvivalPlayer();ArchitectureBlock block=owner(1);BlockPos root=context.getAbsolutePos(ROOT),support=root.down();
  try{world.setBlockState(support,Blocks.WHITE_CONCRETE.getDefaultState(),Block.NOTIFY_ALL);
   for(Direction facing:Direction.Type.HORIZONTAL){clear(world,root);player.refreshPositionAndAngles(root.getX()+.5,root.getY(),root.getZ()+.5,facing.getOpposite().asRotation(),0);ItemStack item=new ItemStack(block);ActionResult result=use(player,item,support,Direction.UP);BlockState placed=world.getBlockState(root);context.assertTrue(result.isAccepted()&&placed.isOf(block)&&placed.get(Properties.HORIZONTAL_FACING)==facing&&value(placed,"root_anchor").equals("canonical"),"final01 item placement is canonical at yaw "+facing);Set<BlockPos> helpers=helpers(placed);Identifier owner=Registries.BLOCK.getId(block);context.assertTrue(!helpers.isEmpty(),"final01 owns helper cells");for(BlockPos offset:helpers){ArchitecturePartBlockEntity part=GeometryRuntime.part(world,root.add(offset));context.assertTrue(part!=null&&part.hasBinding(root,owner),"final01 helper records root "+facing+" "+offset);}world.breakBlock(root,false);for(BlockPos offset:helpers)context.assertTrue(world.getBlockState(root.add(offset)).isAir(),"final01 break clears helper "+facing+" "+offset);}
   context.complete();
  }finally{clear(world,root);world.removeBlock(support,false);player.discard();}
 }

 @GameTest(templateName="bloodborne_blocks:practical_test_kit",tickLimit=80,batchId="document_owners")
 public void authoredPlacementFootprintsSeparateEmptyAndCenterReservations(TestContext context){
  for(int item:List.of(8,16)){ArchitectureBlock block=owner(item);BlockState state=block.getDefaultState();context.assertTrue(!physical(state).isEmpty()&&placement(state).isEmpty(),"final"+item+" keeps physical collision while reserving no ordinary placement footprint");}
  for(int item:List.of(11,12,15)){ArchitectureBlock block=owner(item);BlockState state=block.getDefaultState();context.assertTrue(physical(state).isEmpty()&&placement(state).isEmpty(),"final"+item+" has neither physical collision nor an ordinary placement reservation");}
  ArchitectureBlock center=owner(14);VoxelShape placement=placement(center.getDefaultState()),collision=physical(center.getDefaultState());context.assertTrue(!placement.isEmpty()&&!collision.isEmpty()&&VoxelShapes.matchesAnywhere(collision,placement,BooleanBiFunction.ONLY_FIRST),"final14 reserves only its authored center footprint while retaining outer collision");context.complete();
 }

 @GameTest(templateName="bloodborne_blocks:practical_test_kit",tickLimit=80,batchId="document_owners")
 public void final19IsClimbableButDoesNotProvideFullFallSupport(TestContext context){
  ServerWorld world=context.getWorld();ArchitectureBlock ladder=owner(19);BlockPos root=context.getAbsolutePos(ROOT);try{world.setBlockState(root,ladder.getDefaultState(),Block.NOTIFY_ALL);BlockState state=world.getBlockState(root);context.assertTrue(GeometryRuntime.rebuild(world,root,state)&&FunctionalFurniture.isClimbable(world,root)&&!state.isSideSolidFullSquare(world,root,Direction.UP),"final19 is a climbable ladder plane, not full fall support");context.complete();}finally{clear(world,root);}
 }

 @GameTest(templateName="bloodborne_blocks:practical_test_kit",tickLimit=100,batchId="document_owners")
 public void final05And13ToggleOpenStateAndPhysicalFootprint(TestContext context){
  ServerWorld world=context.getWorld();PlayerEntity player=context.createMockCreativePlayer();BlockPos root=context.getAbsolutePos(ROOT);try{for(int item:List.of(5,13)){ArchitectureBlock block=owner(item);clear(world,root);world.setBlockState(root,block.getDefaultState(),Block.NOTIFY_ALL);BlockState closed=world.getBlockState(root);context.assertTrue(GeometryRuntime.rebuild(world,root,closed)&&!closed.get(Properties.OPEN),"final"+item+" starts closed");VoxelShape before=physical(closed);player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);ActionResult opened=closed.onUse(world,player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(root),Direction.NORTH,root,true));BlockState open=world.getBlockState(root);VoxelShape after=physical(open);context.assertTrue(opened.isAccepted()&&open.get(Properties.OPEN)&&different(before,after),"final"+item+" opens and changes physical footprint");ActionResult shut=open.onUse(world,player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(root),Direction.NORTH,root,true));context.assertTrue(shut.isAccepted()&&!world.getBlockState(root).get(Properties.OPEN),"final"+item+" closes through ordinary interaction");}context.complete();}finally{clear(world,root);player.discard();}
 }

 private static ArchitectureBlock owner(int item){ArchitectureBlock block=BloodborneBlocks.CITY_BLOCKS.get(String.format("owner_final_%02d",item));if(block==null)throw new AssertionError("missing final document owner "+item);return block;}
 private static ActionResult use(PlayerEntity player,ItemStack stack,BlockPos clicked,Direction side){player.setStackInHand(Hand.MAIN_HAND,stack);return stack.useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(clicked),side,clicked,false)));}
 private static Set<BlockPos> helpers(BlockState state){Set<BlockPos> result=new LinkedHashSet<>(GeometryRuntime.state(state).parsedCells.keySet());result.remove(BlockPos.ORIGIN);return result;}
 private static VoxelShape physical(BlockState state){VoxelShape result=VoxelShapes.empty();for(var entry:GeometryRuntime.state(state).parsedCells.entrySet()){BlockPos offset=entry.getKey();result=VoxelShapes.union(result,GeometryRuntime.cellShape(state,offset,false).offset(offset.getX(),offset.getY(),offset.getZ()));}return result;}
 private static VoxelShape placement(BlockState state){VoxelShape result=VoxelShapes.empty();for(BlockPos offset:GeometryRuntime.state(state).parsedCells.keySet())result=VoxelShapes.union(result,GeometryRuntime.placementShape(state,offset).offset(offset.getX(),offset.getY(),offset.getZ()));return result;}
 private static String value(BlockState state,String name){var property=state.getBlock().getStateManager().getProperty(name);if(property==null)throw new AssertionError("missing property "+name);return BloodborneBlocks.value((net.minecraft.state.property.Property)property,(Comparable)state.get((net.minecraft.state.property.Property)property));}
 private static boolean different(VoxelShape first,VoxelShape second){return VoxelShapes.matchesAnywhere(first,second,BooleanBiFunction.ONLY_FIRST)||VoxelShapes.matchesAnywhere(second,first,BooleanBiFunction.ONLY_FIRST);}
 private static void clear(ServerWorld world,BlockPos root){BlockState state=world.getBlockState(root);if(state.getBlock() instanceof ArchitectureBlock)world.breakBlock(root,false);else world.removeBlock(root,false);}
}
