package dev.dreamwalker.bloodbornerp.lamp;

import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornerp.object.ObjectRegistry;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.*;
import net.minecraft.network.*;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.OperatorEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Native graph/packet/tick checks. These are not a substitute for the ordinary client GUI proof. */
public final class LampV10GameTests implements FabricGameTest {
 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=240,batchId="dw_lamps_v10")
 public void legacyDirectedRoutesAndMultipleLinesSurviveTypedSaveWithoutClique(TestContext c){
  LampState s=new LampState();LampState.Node a=node(1),b=node(2),d=node(3);s.nodes.put(a.id,a);s.nodes.put(b.id,b);s.nodes.put(d.id,d);
  NbtCompound legacy=s.writeNbt(new NbtCompound());legacy.putInt("Schema",1);legacy.remove("Lines");NbtList routes=new NbtList();routes.add(NbtHelper.fromUuid(b.id));legacy.getList("Nodes",NbtElement.COMPOUND_TYPE).getCompound(0).put("Routes",routes);NbtCompound untouched=legacy.copy();
  LampState loaded=LampState.fromNbt(legacy);c.assertTrue(legacy.equals(untouched)&&loaded.connected(a.id,b.id)&&!loaded.connected(b.id,a.id),"schema1 stays directed and input is not mutated");
  c.assertTrue(loaded.setConnection("Main",a.id,b.id,true)&&loaded.setConnection("Main",a.id,d.id,true),"new explicit bidirectional connections can share a source");
  c.assertTrue(!loaded.connected(b.id,d.id),"membership in Main does not make B→C");
  loaded.setConnection("Other",b.id,a.id,false);loaded.setConnection("Main",a.id,b.id,false);
  c.assertTrue(loaded.connected(b.id,a.id),"direction edit preserves separately authored reverse path");
  loaded.unlink("Other",b.id,a.id,false);c.assertTrue(!loaded.connected(b.id,a.id)&&loaded.connected(a.id,b.id),"only chosen line/direction is removed");
  NbtCompound saved=loaded.writeNbt(new NbtCompound());LampState restored=LampState.fromNbt(saved);c.assertTrue(restored.writeNbt(new NbtCompound()).equals(saved),"names, stable line/connection/node UUIDs and directions roundtrip exactly");
  restored.removeNode(b.id);c.assertTrue(!restored.nodes.containsKey(b.id)&&!restored.connected(a.id,b.id)&&restored.connected(a.id,d.id),"destroying B cleans its routes without touching A↔C");c.complete();
 }
 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=240,batchId="dw_lamps_v10")
 public void legacyGraphAboveFormerSmallConnectionBudgetLosesNoRoutes(TestContext c){
  LampState fixture=new LampState();for(int i=1;i<=93;i++){LampState.Node n=node(i);fixture.nodes.put(n.id,n);}for(var a:fixture.nodes.values())for(var b:fixture.nodes.values())if(a!=b)a.routes.add(b.id);
  NbtCompound old=fixture.writeNbt(new NbtCompound());old.putInt("Schema",1);old.remove("Lines");LampState imported=LampState.fromNbt(old);
  c.assertTrue(imported.nodes.size()==93&&imported.connectionCount()==4278,"old legal directed graph is retained above4096 pair budget");for(var n:imported.nodes.values())c.assertTrue(n.routes.size()==92,"every original directed destination survives");
  LampState saved=LampState.fromNbt(imported.writeNbt(new NbtCompound()));for(var n:saved.nodes.values())c.assertTrue(n.routes.size()==92,"schema2 second load retains all original routes");c.complete();
 }
 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=300,batchId="dw_lamps_v10_travel")
 public void exactClickedLampPacketAndRealCooldownPermitReturnAfterDelay(TestContext c){
  Fixture f=new Fixture(c);ServerPlayerEntity p=f.player;RpObjectEntity a=f.lamp(2),b=f.lamp(5),closer=f.lamp(3);LampState.Node na=f.register(a,"A"),nb=f.register(b,"B"),nc=f.register(closer,"Closer");
  f.graph.setConnection("Travel",na.id,nb.id,true);f.graph.setConnection("Different",nc.id,nb.id,false);f.landings(a);f.landings(b);p.setPosition(a.getX()+1.5,a.getY(),a.getZ());
  c.assertTrue(a.interact(p,Hand.MAIN_HAND).isAccepted(),"ordinary lamp interaction opens menu");PacketByteBuf first=f.lastMenu();long token=first.readLong();int count=first.readVarInt();c.assertTrue(count==1&&first.readUuid().equals(nb.id)&&first.readString(64).equals("B")&&first.readString(64).equals("A"),"packet source is clicked A although Closer is nearer");first.release();
  c.assertTrue(LampService.travelForTest(p,nb.id,token),"accepted menu destination makes safe real server teleport");p.setPosition(b.getX()+1.5,b.getY(),b.getZ());b.interact(p,Hand.MAIN_HAND);long returnToken=LampService.contextTokenForTest(p.getUuid());Vec3d after=p.getPos();
  c.assertTrue(!LampService.travelForTest(p,na.id,returnToken)&&p.getPos().equals(after),"cooldown never silently teleports or changes source");PacketByteBuf status=f.lastMenu();status.readLong();int rows=status.readVarInt();for(int i=0;i<rows;i++){status.readUuid();status.readString(64);}c.assertTrue(status.readString(64).equals("B")&&!status.readString(256).isBlank()&&status.readVarInt()>0,"return menu names clicked B and reports remaining delay");status.release();
  c.runAtTick(dev.dreamwalker.bloodbornerp.RpConfig.INSTANCE.lampTravelCooldownTicks+4,()->{try{b.interact(p,Hand.MAIN_HAND);c.assertTrue(LampService.travelForTest(p,na.id,LampService.contextTokenForTest(p.getUuid())),"B→A works after real server ticks");c.assertTrue(p.getPos().squaredDistanceTo(a.getPos())<10,"return reaches actual A");c.complete();}finally{f.close();}});
 }
 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=240,batchId="dw_lamps_v10_editor")
 public void toolAutoRegistrationInstanceShiftPermissionsAndTemporaryLeaseCleanup(TestContext c){
  try(Fixture f=new Fixture(c)){
   var p=f.player;var a=f.lamp(2);var b=f.lamp(5);p.setPosition(a.getX()+1,a.getY(),a.getZ());c.assertTrue(LampEditor.selectSource(p,a.getUuid()).success(),"tool selects and registers exact source");p.setPosition(b.getX()+1,b.getY(),b.getZ());
   var linked=LampEditor.edit(p,new LampEditor.Request(LampEditor.Action.CONNECT,a.getUuid(),b.getUuid(),"Named line","",true));c.assertTrue(linked.success()&&f.graph.nodes.size()==2,"target is auto-registered through tool and explicit pair saved");var na=f.graph.byEntity(a.getUuid());var nb=f.graph.byEntity(b.getUuid());c.assertTrue(f.graph.connected(na.id,nb.id)&&f.graph.connected(nb.id,na.id),"new tool connection is bidirectional");
   f.graph.setConnection("Other forward",na.id,nb.id,false);
   c.assertTrue(!otherReverse(LampEditor.view(p,a.getUuid()),"Named line")&&otherReverse(LampEditor.view(p,b.getUuid()),"Named line"),"other-line warning follows selected source rather than stored pair orientation");
   Vec3d shifted=b.getPos().add(0,.125,0);b.setPosition(shifted);LampService.entityMoved(b);c.assertTrue(nb.origin.equals(shifted)&&nb.lamp.equals(b.getUuid()),"actual shifted origin is persisted under same UUID");
   f.world.getServer().getPlayerManager().removeFromOperators(p.getGameProfile());NbtCompound before=f.graph.writeNbt(new NbtCompound());c.assertTrue(!LampEditor.edit(p,new LampEditor.Request(LampEditor.Action.RENAME,b.getUuid(),null,"","Unauthorised",false)).success()&&f.graph.writeNbt(new NbtCompound()).equals(before),"Creative alone cannot edit OP2 network");f.operator();
   p.setPosition(a.getX()+1,a.getY(),a.getZ());var missing=new LampState.Node(UUID.randomUUID(),UUID.randomUUID(),"Unloaded",f.world.getRegistryKey().getValue().toString(),new BlockPos(30000,a.getBlockY(),30000));f.graph.nodes.put(missing.id,missing);f.graph.setConnection("Pending",na.id,missing.id,false);a.interact(p,Hand.MAIN_HAND);
   c.assertTrue(LampService.travelForTest(p,missing.id,LampService.contextTokenForTest(p.getUuid())),"unloaded instance starts bounded asynchronous request");c.assertTrue(((Number)LampService.diagnosticMetrics().get("temporaryChunkTickets")).intValue()==9,"only bounded destination neighborhood is retained");
   LampService.removeByEntity(f.world.getServer(),a.getUuid());c.assertTrue(((Number)LampService.diagnosticMetrics().get("temporaryChunkTickets")).intValue()==0&&((Number)LampService.diagnosticMetrics().get("pendingTravelRequests")).intValue()==0,"source removal releases every temporary ticket and context");c.assertTrue(f.graph.byEntity(b.getUuid())!=null,"neighbor destination is retained");c.complete();
  }
 }
 private static LampState.Node node(int n){return new LampState.Node(new UUID(10,n),new UUID(20,n),"N"+n,"minecraft:overworld",BlockPos.ORIGIN);}
 @SuppressWarnings("unchecked") private static boolean otherReverse(Map<String,Object> view,String name){for(var row:(List<Map<String,Object>>)view.getOrDefault("connections",List.of()))if(name.equals(row.get("lineName")))return Boolean.TRUE.equals(row.get("reverseOtherLines"));throw new AssertionError("missing connection view "+name);}
 private static final class Fixture implements AutoCloseable {
  final TestContext c;final ServerWorld world;final LampState graph;final NbtCompound old;final ServerPlayerEntity player;final Capture packets=new Capture();final List<RpObjectEntity> lamps=new ArrayList<>();
  Fixture(TestContext c){this.c=c;world=c.getWorld();graph=LampState.get(world.getServer().getOverworld());old=graph.writeNbt(new NbtCompound());graph.nodes.clear();graph.lines.clear();player=c.createMockCreativeServerPlayerInWorld();player.networkHandler=new ServerPlayNetworkHandler(world.getServer(),packets,player);operator();}
  void operator(){world.getServer().getPlayerManager().getOpList().add(new OperatorEntry(player.getGameProfile(),4,false));c.assertTrue(player.hasPermissionLevel(4),"fixture actual operator entry grants4 despite TestServer default0");}
  RpObjectEntity lamp(int x){var entity=ObjectRegistry.TYPES.get("hunterlamp").create(world);BlockPos p=c.getAbsolutePos(new BlockPos(x,2,2));entity.setPosition(p.getX()+.5,p.getY(),p.getZ()+.5);if(!world.spawnEntity(entity))throw new AssertionError("fixture lamp spawn");lamps.add(entity);return entity;}
  LampState.Node register(RpObjectEntity entity,String name){player.setPosition(entity.getX()+1,entity.getY(),entity.getZ());LampState.Node n=LampService.register(player,entity,name);if(n==null)throw new AssertionError("lamp registration");return n;}
  void landings(RpObjectEntity entity){BlockPos p=entity.getBlockPos();for(int[] off:new int[][]{{2,0},{-2,0},{0,2},{0,-2}}){BlockPos feet=p.add(off[0],0,off[1]);world.setBlockState(feet.down(),Blocks.STONE.getDefaultState());world.setBlockState(feet,Blocks.AIR.getDefaultState());world.setBlockState(feet.up(),Blocks.AIR.getDefaultState());}}
  PacketByteBuf lastMenu(){for(int i=packets.rows.size()-1;i>=0;i--){Packet<?> p=packets.rows.get(i);if(p instanceof CustomPayloadS2CPacket payload&&payload.getChannel().equals(LampService.LIST_PACKET))return new PacketByteBuf(payload.getData().copy());}throw new AssertionError("no actual lamp_list packet");}
  @Override public void close(){LampService.clearPlayerForTest(player.getUuid());world.getServer().getPlayerManager().removeFromOperators(player.getGameProfile());for(var lamp:lamps)lamp.discard();LampState restored=LampState.fromNbt(old);graph.nodes.clear();graph.nodes.putAll(restored.nodes);graph.lines.clear();graph.lines.putAll(restored.lines);graph.markDirty();player.discard();}
 }
 private static final class Capture extends ClientConnection {final List<Packet<?>> rows=new ArrayList<>();Capture(){super(NetworkSide.SERVERBOUND);}@Override public void send(Packet<?> packet){rows.add(packet);}@Override public void send(Packet<?> packet,PacketCallbacks callbacks){rows.add(packet);}}
}
