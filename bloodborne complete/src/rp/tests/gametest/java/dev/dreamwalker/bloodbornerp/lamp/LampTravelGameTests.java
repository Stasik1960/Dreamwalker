package dev.dreamwalker.bloodbornerp.lamp;

import dev.dreamwalker.bloodbornerp.object.ObjectRegistry;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Exercises the real opaque-context lamp endpoint using server mock players and registered lamps. */
public final class LampTravelGameTests implements FabricGameTest {
 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=120,batchId="rp_lamps")
 public void travelRejectsNegativeInputsThenMovesOnce(TestContext context){
  ServerWorld world=context.getWorld();ServerPlayerEntity player=context.createMockCreativeServerPlayerInWorld();BlockPos sourcePos=context.getAbsolutePos(new BlockPos(2,2,2)),targetPos=context.getAbsolutePos(new BlockPos(10,2,2));
  RpObjectEntity source=lamp(world,sourcePos),target=lamp(world,targetPos);LampState state=LampState.get(world.getServer().getOverworld());state.nodes.clear();
  LampState.Node sourceNode=new LampState.Node(UUID.randomUUID(),source.getUuid(),"Source",world.getRegistryKey().getValue().toString(),sourcePos),targetNode=new LampState.Node(UUID.randomUUID(),target.getUuid(),"Target",world.getRegistryKey().getValue().toString(),targetPos);state.nodes.put(sourceNode.id,sourceNode);state.nodes.put(targetNode.id,targetNode);state.markDirty();
  prepareLanding(world,targetPos);player.refreshPositionAndAngles(sourcePos.getX()+.5,sourcePos.getY(),sourcePos.getZ()+1.5,0,0);
  LampService.open(player);context.assertTrue(LampService.contextTokenForTest(player.getUuid())!=Long.MIN_VALUE,"near registered hunterlamp opens a server packet context");
  long closedToken=LampService.contextTokenForTest(player.getUuid());Vec3d before=player.getPos();context.assertTrue(!LampService.travelForTest(player,targetNode.id,closedToken)&&player.getPos().equals(before),"closed directed route cannot move player");
  sourceNode.routes.add(targetNode.id);blockLandings(world,targetPos,Blocks.LAVA.getDefaultState());LampService.open(player);long unsafeToken=LampService.contextTokenForTest(player.getUuid());context.assertTrue(!LampService.travelForTest(player,targetNode.id,unsafeToken)&&player.getPos().equals(before),"lava landing candidates fail closed");
  prepareLanding(world,targetPos);blockLandings(world,targetPos,Blocks.FIRE.getDefaultState());LampService.open(player);context.assertTrue(!LampService.travelForTest(player,targetNode.id,LampService.contextTokenForTest(player.getUuid())),"collisionless fire cannot be a safe landing");
  prepareLanding(world,targetPos);for(int[] offset:new int[][]{{2,0},{-2,0},{0,2},{0,-2}})world.setBlockState(targetPos.add(offset[0],1,offset[1]),Blocks.STONE.getDefaultState());LampService.open(player);context.assertTrue(!LampService.travelForTest(player,targetNode.id,LampService.contextTokenForTest(player.getUuid())),"low ceiling rejects a standing player");for(int[] offset:new int[][]{{2,0},{-2,0},{0,2},{0,-2}})world.setBlockState(targetPos.add(offset[0],1,offset[1]),Blocks.AIR.getDefaultState());
  prepareLanding(world,targetPos);LampService.open(player);long disconnectToken=LampService.contextTokenForTest(player.getUuid());LampService.clearPlayerForTest(player.getUuid());context.assertTrue(!LampService.travelForTest(player,targetNode.id,disconnectToken)&&player.getPos().equals(before),"disconnect cleanup prevents a pending context from teleporting");
  prepareLanding(world,targetPos);LampService.open(player);long token=LampService.contextTokenForTest(player.getUuid());context.assertTrue(LampService.travelForTest(player,targetNode.id,token),"open route travels through the real server endpoint");Vec3d after=player.getPos();context.assertTrue(after.squaredDistanceTo(before)>4,"safe destination is beside target lamp, not source");context.assertTrue(!LampService.travelForTest(player,targetNode.id,token),"one-time context rejects replay");
  player.refreshPositionAndAngles(sourcePos.getX()+20,sourcePos.getY(),sourcePos.getZ(),0,0);LampService.open(player);context.assertTrue(LampService.contextTokenForTest(player.getUuid())==Long.MIN_VALUE,"far source never issues a travel context");source.discard();target.discard();player.discard();context.complete();
 }
 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=80,batchId="rp_lamps")
 public void stateRoundTripCapsAndRejectsMalformedDimensions(TestContext context){
  LampState source=new LampState();for(int i=0;i<257;i++){UUID id=new UUID(0,i+1);source.nodes.put(id,new LampState.Node(id,new UUID(1,i+1),"N"+i,"minecraft:overworld",BlockPos.ORIGIN));}NbtCompound saved=source.writeNbt(new NbtCompound());LampState capped=LampState.fromNbt(saved);context.assertTrue(capped.nodes.size()==256,"lamp state serializes and restores at the 256 node cap");
  NbtList nodes=saved.getList("Nodes",NbtElement.COMPOUND_TYPE);nodes.getCompound(0).putString("Dimension","not a valid dimension id");LampState malformed=LampState.fromNbt(saved);context.assertTrue(malformed.nodes.size()==255,"malformed persisted dimension is rejected without dropping valid nodes");context.complete();
 }
 private static RpObjectEntity lamp(ServerWorld world,BlockPos pos){EntityType<RpObjectEntity> type=ObjectRegistry.TYPES.get("hunterlamp");if(type==null)throw new AssertionError("hunterlamp missing");RpObjectEntity lamp=type.create(world);if(lamp==null)throw new AssertionError("hunterlamp factory failed");lamp.refreshPositionAndAngles(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);if(!world.spawnEntity(lamp))throw new AssertionError("hunterlamp spawn failed");return lamp;}
 private static void prepareLanding(ServerWorld world,BlockPos lamp){blockLandings(world,lamp,Blocks.AIR.getDefaultState());for(int[] offset:new int[][]{{2,0},{-2,0},{0,2},{0,-2}})world.setBlockState(lamp.add(offset[0],-1,offset[1]),Blocks.STONE.getDefaultState());}
 private static void blockLandings(ServerWorld world,BlockPos lamp,net.minecraft.block.BlockState state){for(int[] offset:new int[][]{{2,0},{-2,0},{0,2},{0,-2}})world.setBlockState(lamp.add(offset[0],0,offset[1]),state);}
}
