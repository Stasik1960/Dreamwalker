package dev.dreamwalker.bloodbornerp.lamp;

import dev.dreamwalker.bloodbornerp.object.ObjectRegistry;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.*;
import net.minecraft.network.*;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Real server teleports and pair persistence; no removed menu/packet functionality. */
public final class LampV10GameTests implements FabricGameTest {
 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="v12_lamp_codec")
 public void isolatedLegacyPairsMigrateButDirectedAndMultiDestinationNetworksDisappear(TestContext c){
  LampState.Node a=node(1),b=node(2),d=node(3);NbtCompound legacy=legacy(a,b,d);NbtList nodes=legacy.getList("Nodes",10);routes(nodes.getCompound(0),b.id);routes(nodes.getCompound(1),a.id);NbtCompound original=legacy.copy();
  LampState loaded=LampState.fromNbt(legacy);c.assertTrue(legacy.equals(original)&&loaded.nodes.size()==2&&loaded.partner(loaded.nodes.get(a.id)).id.equals(b.id),"only isolated reciprocal legacy pair migrates without mutating input");NbtCompound saved=loaded.writeNbt(new NbtCompound());c.assertTrue(saved.getInt("Schema")==3&&LampState.fromNbt(saved).writeNbt(new NbtCompound()).equals(saved),"exact pair UUIDs and positions survive typed save");
  routes(nodes.getCompound(0),b.id,d.id);routes(nodes.getCompound(2),a.id);c.assertTrue(LampState.fromNbt(legacy).nodes.isEmpty(),"old multi-destination component cannot become ambiguous new pair");routes(nodes.getCompound(0),b.id);routes(nodes.getCompound(1));routes(nodes.getCompound(2));c.assertTrue(LampState.fromNbt(legacy).nodes.isEmpty(),"old one-way route cannot silently gain reverse travel");c.complete();
 }
 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="v12_lamp_codec")
 public void schemaTwoLinesMigrateOnlyIsolatedBidirectionalConnections(TestContext c){
  var a=node(1);var b=node(2);var d=node(3);NbtCompound old=legacy(a,b,d);old.putInt("Schema",2);NbtList lines=new NbtList(),connections=new NbtList();NbtCompound line=new NbtCompound();line.putUuid("Id",UUID.randomUUID());line.putString("Name","Old line");NbtCompound ab=connection(a.id,b.id);connections.add(ab);line.put("Connections",connections);lines.add(line);old.put("Lines",lines);
  LampState imported=LampState.fromNbt(old);c.assertTrue(imported.nodes.size()==2&&imported.partner(imported.nodes.get(a.id)).lamp.equals(b.lamp),"schema2 explicit two-way pair survives");connections.add(connection(a.id,d.id));c.assertTrue(LampState.fromNbt(old).nodes.isEmpty(),"old clique/network does not resurrect old route editor");c.complete();
 }
 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=300,batchId="v12_lamp_travel")
 public void ordinaryClickedLampTravelsImmediatelyAndReturnsOnlyAfterRealCooldown(TestContext c){
  Fixture f=new Fixture(c);ServerPlayerEntity p=f.player;var a=f.lamp(2);var b=f.lamp(10);var closer=f.lamp(3);f.landings(a);f.landings(b);p.setPosition(a.getX()+1.5,a.getY(),a.getZ());
  c.assertTrue(LampService.pairLamps(p,a.getUuid(),b.getUuid()),"construction pair created");c.assertTrue(a.interact(p,Hand.MAIN_HAND).isAccepted()&&p.getPos().squaredDistanceTo(b.getPos())<10,"ordinary clicked A travels directly to B even with nearer unpaired lamp");p.setPosition(b.getX()+1.5,b.getY(),b.getZ());Vec3d after=p.getPos();b.interact(p,Hand.MAIN_HAND);c.assertTrue(p.getPos().equals(after)&&LampService.cooldownTicks(p)>0,"immediate return is refused without teleporting");
  c.runAtTick(dev.dreamwalker.bloodbornerp.RpConfig.INSTANCE.lampTravelCooldownTicks+4,()->{try{b.interact(p,Hand.MAIN_HAND);c.assertTrue(p.getPos().squaredDistanceTo(a.getPos())<10,"ordinary B returns to actual A after server ticks");c.complete();}finally{f.close();}});
 }
 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="v12_lamp_replace")
 public void replacementDeletionAndBreakingRemoveWholePairButNotUnrelatedPairs(TestContext c){try(Fixture f=new Fixture(c)){
  var a=f.lamp(1);var b=f.lamp(4);var d=f.lamp(7);var e=f.lamp(10);var h=f.lamp(13);var p=f.player;
  c.assertTrue(LampService.pairLamps(p,a.getUuid(),b.getUuid())&&LampService.pairLamps(p,e.getUuid(),h.getUuid()),"two separate pairs");c.assertTrue(LampService.pairLamps(p,a.getUuid(),d.getUuid()),"replacement allowed");c.assertTrue(f.graph.byEntity(b.getUuid())==null&&LampService.groupMembers(p.getServer(),a.getUuid()).equals(Set.of(a.getUuid(),d.getUuid())),"replacement removes entire old pair");
  c.assertTrue(LampService.removeWholeGroup(p.getServer(),d.getUuid())&&f.graph.byEntity(a.getUuid())==null&&f.graph.byEntity(e.getUuid())!=null,"delete from either member removes exactly whole pair");e.removeObject();c.assertTrue(f.graph.byEntity(h.getUuid())==null,"breaking either member removes partner's binding");c.complete();
 }}
 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=240,batchId="v12_lamp_pending")
 public void movedOriginPermissionAndPairDeletionCancelPendingTravel(TestContext c){Fixture f=new Fixture(c);var p=f.player;var a=f.lamp(2);var b=f.lamp(10);c.assertTrue(LampService.pairLamps(p,a.getUuid(),b.getUuid()),"pair ready");var nb=f.graph.byEntity(b.getUuid());Vec3d moved=b.getPos().add(0,.125,0);b.setPosition(moved);LampService.entityMoved(b);c.assertTrue(nb.origin.equals(moved),"exact instance offset retained");
  p.getAbilities().creativeMode=false;p.getAbilities().allowModifyWorld=false;NbtCompound before=f.graph.writeNbt(new NbtCompound());c.assertTrue(!LampService.pairLamps(p,a.getUuid(),b.getUuid())&&before.equals(f.graph.writeNbt(new NbtCompound())),"unauthorized edit cannot mutate existing pair");p.getAbilities().creativeMode=true;p.getAbilities().allowModifyWorld=true;
  b.remove(net.minecraft.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);nb.pos=new BlockPos(30000,a.getBlockY(),30000);nb.origin=Vec3d.ofBottomCenter(nb.pos);p.setPosition(a.getX()+1,a.getY(),a.getZ());Vec3d original=p.getPos();a.interact(p,Hand.MAIN_HAND);c.assertTrue(LampService.waitingForTest(p.getUuid()),"unloaded exact destination starts bounded ticket request");LampService.removeWholeGroup(p.getServer(),b.getUuid());c.assertTrue(!LampService.waitingForTest(p.getUuid())&&f.graph.byEntity(a.getUuid())==null,"deleting destination cancels waiting and removes both nodes");
  c.runAtTick(220,()->{try{c.assertTrue(p.getPos().equals(original),"deleted request cannot teleport later");c.complete();}finally{f.close();}});
 }
 private static LampState.Node node(int n){return new LampState.Node(new UUID(10,n),new UUID(20,n),"minecraft:overworld",BlockPos.ORIGIN);}
 private static NbtCompound legacy(LampState.Node... nodes){NbtCompound out=new NbtCompound();out.putInt("Schema",1);NbtList list=new NbtList();for(var n:nodes){NbtCompound t=new NbtCompound();t.putUuid("Id",n.id);t.putUuid("Lamp",n.lamp);t.putString("Name","Old");t.putString("Dimension",n.dimension);t.putLong("Pos",n.pos.asLong());list.add(t);}out.put("Nodes",list);return out;}
 private static void routes(NbtCompound n,UUID... ids){NbtList routes=new NbtList();for(UUID id:ids)routes.add(NbtHelper.fromUuid(id));n.put("Routes",routes);}
 private static NbtCompound connection(UUID a,UUID b){NbtCompound t=new NbtCompound();t.putUuid("Id",UUID.randomUUID());t.putUuid("A",a);t.putUuid("B",b);t.putBoolean("AtoB",true);t.putBoolean("BtoA",true);return t;}
 private static final class Fixture implements AutoCloseable {
  final TestContext c;final ServerWorld world;final LampState graph;final ServerPlayerEntity player;final List<RpObjectEntity> lamps=new ArrayList<>();
  Fixture(TestContext c){this.c=c;world=c.getWorld();graph=LampService.state(world.getServer());player=c.createMockCreativeServerPlayerInWorld();player.getAbilities().creativeMode=true;player.getAbilities().allowModifyWorld=true;player.networkHandler=new ServerPlayNetworkHandler(world.getServer(),new Capture(),player);}
  RpObjectEntity lamp(int x){var entity=ObjectRegistry.TYPES.get("hunterlamp").create(world);BlockPos p=c.getAbsolutePos(new BlockPos(x,2,2));entity.setPosition(p.getX()+.5,p.getY(),p.getZ()+.5);if(!world.spawnEntity(entity))throw new AssertionError("fixture lamp spawn");lamps.add(entity);return entity;}
  void landings(RpObjectEntity entity){BlockPos p=entity.getBlockPos();for(int[] off:new int[][]{{2,0},{-2,0},{0,2},{0,-2}}){BlockPos feet=p.add(off[0],0,off[1]);world.setBlockState(feet.down(),Blocks.STONE.getDefaultState());world.setBlockState(feet,Blocks.AIR.getDefaultState());world.setBlockState(feet.up(),Blocks.AIR.getDefaultState());}}
  @Override public void close(){LampService.clearPlayerForTest(player.getUuid());for(var lamp:lamps){LampService.removeWholeGroup(world.getServer(),lamp.getUuid());lamp.discard();}player.discard();}
 }
 private static final class Capture extends ClientConnection {Capture(){super(NetworkSide.SERVERBOUND);}@Override public void send(Packet<?> packet){}@Override public void send(Packet<?> packet,PacketCallbacks callbacks){}}
}
