package dev.dreamwalker.bloodborneblocks;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** Server coverage for hand-lantern states stored directly on statue roots. */
public final class AgonyAttachmentGameTests implements FabricGameTest {
 private static final BlockPos ROOT=new BlockPos(4,2,4);

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="agony_attachment")
 public void survivalAttachToggleRemoveAndBreakPreserveLantern(TestContext context){
  ServerWorld world=context.getWorld();PlayerEntity player=context.createMockSurvivalPlayer();ArchitectureBlock statue=required("o_c008_1"),lantern=required("o_lantern");BlockPos root=context.getAbsolutePos(ROOT);
  try{
   place(context,root,statue.getDefaultState(),"survival statue fixture");BlockState empty=world.getBlockState(root);context.assertTrue("none".equals(hand(empty)),"statue starts without a hand lantern");
   ItemStack held=new ItemStack(lantern);player.setStackInHand(Hand.MAIN_HAND,held);BlockPos helper=helper(world,root,world.getBlockState(root));
   context.assertTrue(helper!=null,"survival statue fixture owns a helper for forwarding");ActionResult attach=useHelper(world,helper,player);
   context.assertTrue(attach==ActionResult.SUCCESS&&"unlit".equals(hand(world.getBlockState(root)))&&held.isEmpty(),"helper forwards an unlit survival attachment and consumes one item: result="+attach+", hand="+hand(world.getBlockState(root))+", held="+held.getCount());
   player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);context.assertTrue(use(world,root,player)==ActionResult.SUCCESS&&"lit".equals(hand(world.getBlockState(root)))&&world.getBlockState(root).getLuminance()==15,"empty hand toggles the mounted lantern to luminance 15");
   player.setSneaking(true);context.assertTrue(use(world,root,player)==ActionResult.SUCCESS&&"none".equals(hand(world.getBlockState(root)))&&inventoryCount(player,lantern)==1,"sneaking removes one lantern item without loss");player.setSneaking(false);
   player.setStackInHand(Hand.MAIN_HAND,new ItemStack(lantern));use(world,root,player);player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);use(world,root,player);
   clearDrops(world,root,lantern);world.breakBlock(root,true,player);
   context.assertTrue(dropCount(world,root,lantern)==1&&droppedLit(world,root,lantern),"breaking a lit statue drops exactly one lit mounted lantern");context.complete();
  }finally{clear(context,statue);clearDrops(world,root,lantern);player.discard();}
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=80,batchId="agony_attachment")
 public void creativeRemovalDoesNotDuplicateLantern(TestContext context){
  ServerWorld world=context.getWorld();PlayerEntity player=context.createMockCreativePlayer();ArchitectureBlock lantern=required("o_lantern");BlockPos root=context.getAbsolutePos(ROOT);
  try{
   Direction[] facings={Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST};String[] statues={"o_c008_1","o_c008_2","o_c008_3","o_c008_5"};
   for(int index=0;index<statues.length;index++){
    ArchitectureBlock statue=required(statues[index]);place(context,root,statue.getDefaultState().with(Properties.HORIZONTAL_FACING,facings[index]),"creative statue fixture "+statues[index]);
    ItemStack held=new ItemStack(lantern);String expected=index==0?"lit":"unlit";if(index==0)held.getOrCreateSubNbt("BlockStateTag").putString("lit","true");player.setStackInHand(Hand.MAIN_HAND,held);ActionResult attach=use(world,root,player);context.assertTrue(attach==ActionResult.SUCCESS&&held.getCount()==1&&expected.equals(hand(world.getBlockState(root)))&&world.getBlockState(root).get(Properties.HORIZONTAL_FACING)==facings[index],"creative preserves lantern lit tag and statue facing: "+statues[index]+", result="+attach+", hand="+hand(world.getBlockState(root))+", held="+held.getCount());
    player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);player.setSneaking(true);context.assertTrue(use(world,root,player)==ActionResult.SUCCESS&&"none".equals(hand(world.getBlockState(root)))&&inventoryCount(player,lantern)==0,"creative removal does not duplicate: "+statues[index]);player.setSneaking(false);clear(context,statue);
   }
   context.complete();
  }finally{for(String id:new String[]{"o_c008_1","o_c008_2","o_c008_3","o_c008_5"})clear(context,required(id));player.discard();}
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=80,batchId="agony_attachment")
 public void adventureAndUnsupportedItemDataCannotMutateAttachments(TestContext context){
  ServerWorld world=context.getWorld();PlayerEntity player=context.createMockSurvivalPlayer();ArchitectureBlock statue=required("o_c008_1"),lantern=required("o_lantern");BlockPos root=context.getAbsolutePos(ROOT);
  try{
   place(context,root,statue.getDefaultState(),"permission statue fixture");ItemStack held=new ItemStack(lantern);player.setStackInHand(Hand.MAIN_HAND,held);player.getAbilities().allowModifyWorld=false;
   context.assertTrue(use(world,root,player)==ActionResult.FAIL&&"none".equals(hand(world.getBlockState(root)))&&held.getCount()==1,"adventure player cannot attach a lantern");
   BlockState lit=BloodborneBlocks.set(world.getBlockState(root),world.getBlockState(root).getBlock().getStateManager().getProperty("hand_lantern"),"lit");world.setBlockState(root,lit,Block.NOTIFY_ALL);player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);
   context.assertTrue(use(world,root,player)==ActionResult.FAIL&&"lit".equals(hand(world.getBlockState(root))),"adventure player cannot toggle a lantern");player.setSneaking(true);context.assertTrue(use(world,root,player)==ActionResult.FAIL&&"lit".equals(hand(world.getBlockState(root))),"adventure player cannot remove a lantern");player.setSneaking(false);player.getAbilities().allowModifyWorld=true;
   place(context,root,statue.getDefaultState(),"NBT statue fixture");ItemStack named=new ItemStack(lantern);named.setCustomName(Text.literal("not serializable"));player.setStackInHand(Hand.MAIN_HAND,named);
   context.assertTrue(use(world,root,player)==ActionResult.FAIL&&"none".equals(hand(world.getBlockState(root)))&&named.getCount()==1&&world.getBlockEntity(root)==null,"custom-name lantern is rejected without consumption or root block entity data");
   ItemStack arbitrary=new ItemStack(lantern);arbitrary.getOrCreateNbt().putString("unsafe","data");player.setStackInHand(Hand.MAIN_HAND,arbitrary);context.assertTrue(use(world,root,player)==ActionResult.FAIL&&"none".equals(hand(world.getBlockState(root)))&&arbitrary.getCount()==1,"arbitrary lantern NBT is rejected without consumption");context.complete();
  }finally{clear(context,statue);player.discard();}
 }

 private static ActionResult use(ServerWorld world,BlockPos root,PlayerEntity player){BlockState state=world.getBlockState(root);return ((ArchitectureBlock)state.getBlock()).onUse(state,world,root,player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(root),Direction.NORTH,root,true));}
 private static ActionResult useHelper(ServerWorld world,BlockPos helper,PlayerEntity player){BlockState state=world.getBlockState(helper);return state.getBlock().onUse(state,world,helper,player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(helper),Direction.NORTH,helper,true));}
 private static ArchitectureBlock required(String id){ArchitectureBlock block=BloodborneBlocks.BLOCKS.get(id);if(block==null)throw new AssertionError("missing agony fixture "+id);return block;}
 private static void place(TestContext context,BlockPos root,BlockState state,String message){ServerWorld world=context.getWorld();world.setBlockState(root,state,Block.NOTIFY_ALL);context.assertTrue(GeometryRuntime.rebuild(world,root,state),message+" rebuilds helpers");}
 private static String hand(BlockState state){Property<?> property=state.getBlock().getStateManager().getProperty("hand_lantern");return property==null?null:BloodborneBlocks.value((Property)property,(Comparable)state.get((Property)property));}
 private static int inventoryCount(PlayerEntity player,ArchitectureBlock block){int count=0;for(int slot=0;slot<player.getInventory().size();slot++){ItemStack stack=player.getInventory().getStack(slot);if(stack.isOf(block.asItem()))count+=stack.getCount();}return count;}
 private static int dropCount(ServerWorld world,BlockPos root,ArchitectureBlock block){return world.getEntitiesByClass(ItemEntity.class,new Box(root).expand(20),entity->entity.getStack().isOf(block.asItem())).stream().mapToInt(entity->entity.getStack().getCount()).sum();}
 private static boolean droppedLit(ServerWorld world,BlockPos root,ArchitectureBlock block){return world.getEntitiesByClass(ItemEntity.class,new Box(root).expand(20),entity->entity.getStack().isOf(block.asItem())).stream().anyMatch(entity->{ItemStack stack=entity.getStack();return stack.getSubNbt("BlockStateTag")!=null&&"true".equals(stack.getSubNbt("BlockStateTag").getString("lit"));});}
 private static BlockPos helper(ServerWorld world,BlockPos root,BlockState state){for(BlockPos offset:GeometryRuntime.state(state).parsedCells.keySet()){if(offset.equals(BlockPos.ORIGIN))continue;BlockPos target=root.add(offset);ArchitecturePartBlockEntity part=GeometryRuntime.part(world,target);if(part!=null&&GeometryRuntime.ownsHelper(state,root,target,part.ownerId()))return target;}return null;}
 private static void clearDrops(ServerWorld world,BlockPos root,ArchitectureBlock block){world.getEntitiesByClass(ItemEntity.class,new Box(root).expand(20),entity->entity.getStack().isOf(block.asItem())).forEach(ItemEntity::discard);}
 private static void clear(TestContext context,ArchitectureBlock block){ServerWorld world=context.getWorld();for(int x=-5;x<14;x++)for(int y=0;y<12;y++)for(int z=-5;z<14;z++){BlockPos pos=context.getAbsolutePos(new BlockPos(x,y,z));if(world.getBlockState(pos).isOf(block)||world.getBlockState(pos).isOf(BloodborneBlocks.PART_BLOCK))world.removeBlock(pos,false);}}
}
