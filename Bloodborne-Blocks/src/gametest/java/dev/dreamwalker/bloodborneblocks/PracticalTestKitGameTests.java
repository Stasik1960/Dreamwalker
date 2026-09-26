package dev.dreamwalker.bloodborneblocks;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.List;
import java.util.Set;

/** Focused dedicated-server proof for the objects shipped in the practical TEST kit. */
public final class PracticalTestKitGameTests implements FabricGameTest {
 private static final BlockPos CLICK=new BlockPos(8,0,8),ROOT=CLICK.up();

 @GameTest(templateName="bloodborne_blocks:practical_test_kit",tickLimit=240,batchId="zz_practical_test_kit")
 public void c001C474AndC618PlaceFromPickInFourDirectionsAndBreakWhole(TestContext context){
  ServerWorld world=context.getWorld();PlayerEntity player=context.createMockSurvivalPlayer();BlockPos root=context.getAbsolutePos(ROOT),support=context.getAbsolutePos(CLICK);
  try{
   for(String id:List.of("o_c001","o_c474","o_c618")){
    clear(world,root);ArchitectureBlock block=required(id);BlockState source=kitState(id,block);world.setBlockState(support,Blocks.WHITE_CONCRETE.getDefaultState(),Block.NOTIFY_ALL);world.setBlockState(root,source,Block.NOTIFY_ALL);context.assertTrue(GeometryRuntime.rebuild(world,root,source),id+" exact TEST-kit pick fixture rebuilds");
    ItemStack picked=block.getPickStack(world,root,source);context.assertTrue(picked.isOf(block.asItem()),id+" root pick resolves its whole-object item");
    assertStableItemState(context,id,"pick",block,source,picked);
    if(id.equals("o_c001")){
     ItemStack dropped=Block.getDroppedStacks(source,world,root,null,player,ItemStack.EMPTY).stream().filter(stack->stack.isOf(block.asItem())).findFirst().orElse(ItemStack.EMPTY);
     context.assertTrue(!dropped.isEmpty(),"C001 whole-object loot contains its item");assertStableItemState(context,id,"drop",block,source,dropped);
    }
    world.breakBlock(root,false);
    for(Direction facing:Direction.Type.HORIZONTAL){
     context.assertTrue(world.getBlockState(root).isAir(),id+" placement target starts empty "+facing);face(context,player,facing.getOpposite());useStackOnBlock(context,player,picked.copy(),CLICK,Direction.UP);BlockState placed=world.getBlockState(root);
     context.assertTrue(placed.isOf(block)&&placed.get(Properties.HORIZONTAL_FACING)==facing,id+" picked item places facing "+facing);
     for(String stable:List.of("variant","visual")){var property=block.getStateManager().getProperty(stable);if(property!=null)context.assertTrue(placed.get(property).equals(source.get(property)),id+" picked item preserves TEST-kit "+stable+" "+facing+" expected="+source.get(property)+" actual="+placed.get(property));}
     if(id.equals("o_c618")){if(!placed.get(Properties.LIT)){player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);context.useBlock(ROOT,player);placed=world.getBlockState(root);}context.assertTrue(placed.get(Properties.LIT),"C618 TEST-kit lantern retains or reaches authored lit=true state "+facing);}
     Set<BlockPos> offsets=GeometryRuntime.state(placed).parsedCells.keySet();
     var owner=Registries.BLOCK.getId(block);for(BlockPos offset:offsets)if(!offset.equals(BlockPos.ORIGIN)){BlockPos helper=root.add(offset);ArchitecturePartBlockEntity part=GeometryRuntime.part(world,helper);context.assertTrue(part!=null&&part.hasBinding(root,owner),id+" helper owns exact root "+facing+" "+offset);NbtCompound saved=part.createNbt();ArchitecturePartBlockEntity decoded=new ArchitecturePartBlockEntity(helper,world.getBlockState(helper));decoded.readNbt(saved);context.assertTrue(decoded.hasBinding(root,owner),id+" helper ownership survives NBT encode/decode "+facing+" "+offset);}
     BlockPos adjacent=freeHorizontal(root,offsets);world.setBlockState(adjacent,Blocks.COBBLESTONE.getDefaultState(),Block.NOTIFY_ALL);context.assertTrue(world.getBlockState(adjacent).isOf(Blocks.COBBLESTONE),id+" permits adjacent construction outside its owned cells "+facing);
     world.breakBlock(root,false);context.assertTrue(world.getBlockState(root).isAir(),id+" root break removes master "+facing);for(BlockPos offset:offsets)if(!offset.equals(BlockPos.ORIGIN))context.assertTrue(world.getBlockState(root.add(offset)).isAir(),id+" root break clears helper "+facing+" "+offset);context.assertTrue(world.getBlockState(adjacent).isOf(Blocks.COBBLESTONE),id+" whole break preserves adjacent construction "+facing);world.removeBlock(adjacent,false);
    }
   }
   context.complete();
  }finally{clear(world,root);world.removeBlock(support,false);player.discard();}
 }

 @GameTest(templateName="bloodborne_blocks:practical_test_kit",tickLimit=160,batchId="zz_practical_test_kit")
 public void connectedRailingPlacesFromPickAndJoinsEveryHorizontalNeighbor(TestContext context){
  ServerWorld world=context.getWorld();PlayerEntity player=context.createMockSurvivalPlayer();ArchitectureBlock curb=required("o_stone_railing");BlockPos root=context.getAbsolutePos(ROOT);
  try{
   prepareFloor(context);ItemStack picked=curb.getPickStack(world,root,curb.getDefaultState());context.assertTrue(picked.isOf(curb.asItem()),"connected curb pick resolves its item");
   for(Direction direction:Direction.Type.HORIZONTAL){
    clear(world,root);BlockPos other=root.offset(direction);face(context,player,direction.getOpposite());useStackOnBlock(context,player,picked.copy(),CLICK,Direction.UP);context.assertTrue(world.getBlockState(root).isOf(curb)&&world.getBlockState(root).get(Properties.HORIZONTAL_FACING)==direction,"curb picked item places facing "+direction);
    useStackOnBlock(context,player,picked.copy(),CLICK.offset(direction),Direction.UP);BooleanProperty toward=(BooleanProperty)curb.getStateManager().getProperty(direction.asString()),back=(BooleanProperty)curb.getStateManager().getProperty(direction.getOpposite().asString());
    context.assertTrue(world.getBlockState(other).isOf(curb)&&world.getBlockState(root).get(toward)&&world.getBlockState(other).get(back),"curb joins item-placed neighbor "+direction);world.breakBlock(other,false);context.assertTrue(!world.getBlockState(root).get(toward),"curb disconnects after neighbor whole break "+direction);world.breakBlock(root,false);
   }
   acceptedRootHelperGroupKeepsBothRealOwnersIntact(context);
   context.complete();
  }finally{clear(world,root);clearFloor(context);player.discard();}
 }

 private static void acceptedRootHelperGroupKeepsBothRealOwnersIntact(TestContext context){
  ServerWorld world=context.getWorld();PlayerEntity player=context.createMockCreativePlayer();ArchitectureBlock railing=required("o_stone_railing"),upper=BloodborneBlocks.CITY_BLOCKS.get("owner_7995b1c8c5cc537f41cf");context.assertTrue(upper!=null,"accepted shared owner ID is registered");BlockPos relativeRoot=new BlockPos(6,4,6),root=context.getAbsolutePos(relativeRoot),upperRoot=root.up();
  BlockState railingState=railing.getDefaultState();for(var entry:java.util.Map.of("east","true","north","false","south","true","up","true","west","false","facing","north","visual","base").entrySet()){var property=railing.getStateManager().getProperty(entry.getKey());context.assertTrue(property!=null,"accepted railing property exists: "+entry.getKey());railingState=BloodborneBlocks.set(railingState,property,entry.getValue());}
  BlockState upperState=upper.getDefaultState();clear(world,root);
  try{
   int convertedLoadFlags=Block.NOTIFY_LISTENERS|Block.FORCE_STATE;world.setBlockState(root,railingState,convertedLoadFlags);context.assertTrue(GeometryRuntime.rebuild(world,root,railingState),"accepted railing root rebuilds whole");face(context,player,Direction.SOUTH);useStackOnBlock(context,player,new ItemStack(upper),relativeRoot,Direction.UP);
   ArchitecturePartBlockEntity carrier=GeometryRuntime.part(world,root);var upperId=Registries.BLOCK.getId(upper);BlockState connected=world.getBlockState(root),placedUpper=world.getBlockState(upperRoot);context.assertTrue(connected.isOf(railing)&&placedUpper.isOf(upper)&&placedUpper.get(Properties.HORIZONTAL_FACING)==Direction.NORTH,"manual pair placement keeps both accepted registry owners and upper facing intact");for(String side:List.of("east","north","south","west")){BooleanProperty property=(BooleanProperty)railing.getStateManager().getProperty(side);context.assertTrue(!connected.get(property),"manual pair correctly recalculates absent horizontal neighbor "+side);}context.assertTrue(connected.get((BooleanProperty)railing.getStateManager().getProperty("up")),"manual pair connects railing upward to placed owner");context.assertTrue(carrier!=null&&carrier.hasBinding(upperRoot,upperId),"manually placed upper owner stores its exact guest binding on the railing root");
   world.breakBlock(upperRoot,false);context.assertTrue(world.getBlockState(root).isOf(railing)&&GeometryRuntime.part(world,root)==null,"removing the upper owner preserves the accepted railing root");
  }finally{clear(world,root);player.discard();}
 }

 private static ArchitectureBlock required(String id){ArchitectureBlock block=BloodborneBlocks.BLOCKS.get(id);if(block==null)throw new AssertionError("missing practical TEST fixture "+id);return block;}
 private static void assertStableItemState(TestContext context,String id,String route,ArchitectureBlock block,BlockState source,ItemStack stack){for(String stable:List.of("variant","visual")){var property=block.getStateManager().getProperty(stable);if(property==null)continue;String expected=BloodborneBlocks.value((net.minecraft.state.property.Property)property,(Comparable)source.get((net.minecraft.state.property.Property)property));NbtCompound tag=stack.getSubNbt("BlockStateTag");String actual=tag==null?"":tag.getString(stable);if(source.get(property).equals(block.getDefaultState().get(property)))context.assertTrue(actual.isEmpty()||actual.equals(expected),id+" "+route+" item keeps default "+stable);else context.assertTrue(actual.equals(expected),id+" "+route+" item records "+stable+" expected="+expected+" actual="+actual);}}
 private static BlockState kitState(String id,ArchitectureBlock block){BlockState state=block.getDefaultState();java.util.Map<String,String> exact=switch(id){case "o_c001"->java.util.Map.of("facing","north","variant","tree_cfd3d71f521b","visual","base");case "o_c474"->java.util.Map.of("facing","north","visual","base");case "o_c618"->java.util.Map.of("facing","north","lit","true","variant","canonical","visual","base");default->throw new IllegalArgumentException(id);};for(var entry:exact.entrySet()){var property=block.getStateManager().getProperty(entry.getKey());if(property==null)throw new AssertionError(id+" missing TEST-kit property "+entry.getKey());state=BloodborneBlocks.set(state,property,entry.getValue());}return state;}
 private static void face(TestContext context,PlayerEntity player,Direction facing){BlockPos position=context.getAbsolutePos(new BlockPos(-6,4,-6));player.refreshPositionAndAngles(position.getX()+.5,position.getY(),position.getZ()+.5,facing.asRotation(),0);}
 private static void useStackOnBlock(TestContext context,PlayerEntity player,ItemStack stack,BlockPos pos,Direction side){player.setStackInHand(Hand.MAIN_HAND,stack);context.useStackOnBlock(player,stack,pos,side);}
 private static BlockPos freeHorizontal(BlockPos root,Set<BlockPos> offsets){for(int distance=1;distance<=6;distance++)for(Direction direction:Direction.Type.HORIZONTAL){BlockPos candidate=root.offset(direction,distance),offset=candidate.subtract(root);if(!offsets.contains(offset))return candidate;}throw new AssertionError("no bounded adjacent construction cell");}
 private static void prepareFloor(TestContext context){for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)context.setBlockState(CLICK.add(x,0,z),Blocks.WHITE_CONCRETE);}
 private static void clearFloor(TestContext context){for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)context.setBlockState(CLICK.add(x,0,z),Blocks.AIR);}
 private static void clear(ServerWorld world,BlockPos root){BlockState state=world.getBlockState(root);if(state.getBlock() instanceof ArchitectureBlock){GeometryRuntime.removeOwnedParts(world,root,state);world.removeBlock(root,false);}else if(state.isOf(BloodborneBlocks.PART_BLOCK)){ArchitecturePartBlockEntity part=GeometryRuntime.part(world,root);if(part!=null)for(var binding:List.copyOf(part.bindings()))part.unbind(binding.root(),binding.owner());world.removeBlock(root,false);}}
}
