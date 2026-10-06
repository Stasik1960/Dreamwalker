package dev.dreamwalker.bloodbornerp.object;

import dev.dreamwalker.bloodbornerp.lamp.LampPolicy;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

/** Server checks for canonical lamp registration and locked linked-object refusal. */
public final class RpObjectLampGameTests implements FabricGameTest {
 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="rp_objects")
 public void sourceExceptionsLegacyGateLinksAndPulsePersistence(TestContext context){
  var world=context.getWorld();var pos=context.getAbsolutePos(new BlockPos(3,2,3));var player=context.createMockCreativeServerPlayerInWorld();player.refreshPositionAndAngles(pos.getX(),pos.getY(),pos.getZ(),0,0);
  for(String id:java.util.List.of("curtainsmall","curtain_half","curtains","door_empty","gate_empty","trapdoor","wood_gate")){
   var e=ObjectRegistry.TYPES.get(id).create(world);context.assertTrue(!e.isCollidable()&&!e.canBeLinked(),"source noncolliding helper "+id);
  }
  for(String id:java.util.List.of("chandelier_small","chandelier_large")){
   context.assertTrue(ObjectRegistry.place(player,id,pos,0),"chandelier places with source offset");
   var e=world.getEntitiesByType(ObjectRegistry.TYPES.get(id),new net.minecraft.util.math.Box(pos).expand(8),x->true).get(0);
   context.assertTrue(e.getY()==pos.getY()-5.5,"source chandelier Y-5.5");e.discard();
  }
  var gate=required("small_gate",world,pos.add(2,0,0));var legacy=new NbtCompound();legacy.putInt("EntityState",1);legacy.putString("ConnectionId","test_gate");gate.readCustomDataFromNbt(legacy);context.assertTrue(gate.isOpen()&&!gate.isCollidable(),"legacy EntityState keeps source gate open");gate.setOpen(false);
  var lever=required("lever_1",world,pos);var oldLever=new NbtCompound();oldLever.putString("ConnectionId","test_gate");lever.readCustomDataFromNbt(oldLever);lever.interact(player,Hand.MAIN_HAND);context.assertTrue(lever.links().contains(gate.getUuid()),"loaded legacy ConnectionId migrated to bounded UUID link");
  for(int i=0;i<30;i++)lever.tick();var saved=new NbtCompound();lever.writeNbt(saved);lever.discard();var restored=ObjectRegistry.TYPES.get("lever_1").create(world);restored.readNbt(saved);world.spawnEntity(restored);context.assertTrue(saved.getInt("LeverPulseTicks")==40,"pulse remaining time is persistent");
  for(int i=0;i<39;i++)restored.tick();context.assertTrue(!gate.isOpen(),"restored pulse has not fired early");restored.tick();context.assertTrue(gate.isOpen()&&!restored.isOpen(),"restored pulse fires once after remaining 40 ticks");restored.tick();context.assertTrue(gate.isOpen(),"pulse does not repeat");gate.discard();restored.discard();player.discard();context.complete();
 }
 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=80,batchId="rp_objects")
 public void placementCopyBreakAndPermissionChecks(TestContext context){
  var world=context.getWorld();var player=context.createMockCreativeServerPlayerInWorld();var pos=context.getAbsolutePos(new BlockPos(3,2,3));
  player.refreshPositionAndAngles(pos.getX(),pos.getY(),pos.getZ()-2,0,0);
  var stack=net.minecraft.registry.Registries.ITEM.get(dev.dreamwalker.bloodbornerp.BloodborneRp.id("door_1_placer")).getDefaultStack();
  stack.setCustomName(net.minecraft.text.Text.literal("copy check"));
  // Regression: the original allows architectural overlap and a builder inside a large box.
  world.setBlockState(pos.add(1,1,0),net.minecraft.block.Blocks.STONE.getDefaultState());
  context.assertTrue(ObjectRegistry.place(player,"door_1",pos,45,stack),"door places beside a wall");
  var door=world.getEntitiesByType(ObjectRegistry.TYPES.get("door_1"),new net.minecraft.util.math.Box(pos).expand(2),e->true).get(0);
  context.assertTrue(door.getX()==pos.getX()&&door.getZ()==pos.getZ()&&door.getYaw()==-135,"source integer anchor and 45 degree yaw convention");
  context.assertTrue(!ObjectRegistry.place(player,"door_1",pos.add(40,0,0),0),"distant placement denied");
  context.assertTrue(!ObjectRegistry.place(player,"door_1",pos,Float.NaN),"nonfinite yaw denied");
  var ordinary=context.createMockSurvivalPlayer();ordinary.refreshPositionAndAngles(pos.getX(),pos.getY(),pos.getZ(),0,0);
  context.assertTrue(!ObjectRegistry.place(ordinary,"hunterlamp",pos,0),"survival placement denied");
  context.assertTrue(!door.damage(world.getDamageSources().playerAttack(ordinary),1)&&!door.isRemoved(),"survival cannot break decor");
  context.assertTrue(!door.damage(world.getDamageSources().generic(),100)&&!door.isRemoved(),"environment damage cannot destroy decor");
  context.assertTrue(!door.damage(world.getDamageSources().arrow(new net.minecraft.entity.projectile.ArrowEntity(world,player),player),5)&&!door.isRemoved(),"builder projectiles cannot accidentally delete decor");
  door.setOpen(true);context.assertTrue(door.setOpen(false),"unoccupied wall-overlapped door can close");door.setOpen(true);door.setLocked(true);door.setObjectScale(1.6f);var picked=door.getPickBlockStack();
  context.assertTrue(picked.getItem()==stack.getItem()&&picked.getName().getString().equals("copy check"),"pick returns placer and name");
  var copiedPos=pos.add(3,0,0);context.assertTrue(ObjectRegistry.place(player,"door_1",copiedPos,90,picked),"picked item places copy");
  var copy=world.getEntitiesByType(ObjectRegistry.TYPES.get("door_1"),new net.minecraft.util.math.Box(copiedPos).expand(.2),e->e!=door).get(0);
  context.assertTrue(copy.isOpen()&&copy.isLocked()&&copy.objectScale()==1.6f&&copy.links().isEmpty()&&!copy.getUuid().equals(door.getUuid()),"copy local state and Scale without UUID or links");
  var saved=new NbtCompound();copy.writeNbt(saved);var restored=copy.getType().create(world);restored.readNbt(saved);context.assertTrue(((RpObjectEntity)restored).objectScale()==1.6f,"legacy Scale survives save and reload");
  copy.setObjectScale(Float.NaN);context.assertTrue(copy.objectScale()==1f,"nonfinite Scale sanitized");copy.setObjectScale(1000);context.assertTrue(copy.objectScale()==1f,"huge Scale sanitized");copy.setObjectScale(0);context.assertTrue(copy.objectScale()==0,"intentional zero Scale retained");
  context.assertTrue(door.damage(world.getDamageSources().playerAttack(player),1)&&door.isRemoved(),"creative attack removes object");
  copy.removeByBuilder();player.discard();ordinary.discard();context.complete();
 }
 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=80,batchId="rp_objects")
 public void nativeMovementCollidesAndOpenDoorAllowsPassage(TestContext context){
  var world=context.getWorld();var pos=context.getAbsolutePos(new BlockPos(3,2,3));var player=context.createMockCreativeServerPlayerInWorld();
  var lamp=required("hunterlamp",world,pos);lamp.refreshPositionAndAngles(pos.getX(),pos.getY(),pos.getZ(),0,0);
  player.refreshPositionAndAngles(pos.getX(),pos.getY(),pos.getZ()-2,0,0);double start=player.getZ();
  player.move(net.minecraft.entity.MovementType.SELF,new net.minecraft.util.math.Vec3d(0,0,3));
  context.assertTrue(player.getZ()-start<2,"native player movement blocked by non-door decoration");lamp.discard();
  var chest=required("chest",world,pos);chest.refreshPositionAndAngles(pos.getX(),pos.getY(),pos.getZ(),0,0);
  context.assertTrue(Math.abs(chest.getBoundingBox().getXLength()-1.5)<1e-5&&Math.abs(chest.getBoundingBox().getZLength()-1.5)<1e-5,"original registered square collider, no invented thin slab");
  chest.setOpen(true);context.assertTrue(chest.isCollidable(),"source chest remains collidable when open");chest.discard();var door=required("door_1",world,pos);door.refreshPositionAndAngles(pos.getX(),pos.getY(),pos.getZ(),0,0);door.setOpen(true);player.refreshPositionAndAngles(pos.getX(),pos.getY(),pos.getZ()-2,0,0);start=player.getZ();player.move(net.minecraft.entity.MovementType.SELF,new net.minecraft.util.math.Vec3d(0,0,3));
  context.assertTrue(Math.abs(player.getZ()-start-3)<1e-5,"open door permits native movement");
  player.refreshPositionAndAngles(pos.getX(),pos.getY(),pos.getZ(),0,0);context.assertTrue(!door.setOpen(false),"occupied door refuses close");
  door.discard();player.discard();context.complete();
 }
 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=80,batchId="rp_objects")
 public void objectCommandsFollowCrosshairAndRespectWalls(TestContext context){
  var world=context.getWorld();var p=context.getAbsolutePos(new BlockPos(3,2,2));var player=context.createMockCreativeServerPlayerInWorld();
  player.refreshPositionAndAngles(p.getX()+.5,p.getY(),p.getZ()+.5,0,0);
  var target=required("door_1",world,p.add(0,0,3));var nearer=required("hunterlamp",world,p.add(2,0,0));
  context.assertTrue(ObjectRegistry.objectAt(player)==target,"crosshair chooses farther door, not nearest lamp");
  world.setBlockState(p.add(0,1,1),net.minecraft.block.Blocks.STONE.getDefaultState());
  context.assertTrue(ObjectRegistry.objectAt(player)==null,"wall blocks object command targeting");
  target.discard();nearer.discard();player.discard();context.complete();
 }
 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=80,batchId="rp_objects")
 public void canonicalHunterLampAndMalformedNbtAreSafe(TestContext context){
  ServerWorld world=context.getWorld();EntityType<RpObjectEntity> type=ObjectRegistry.TYPES.get("hunterlamp");context.assertTrue(type!=null,"catalog hunterlamp is registered as an RP object");
  RpObjectEntity lamp=type.create(world);context.assertTrue(lamp!=null&&lamp.assetId().equals("hunterlamp"),"hunterlamp factory uses the canonical asset id");
  NbtCompound malformed=new NbtCompound();malformed.putBoolean("Open",true);malformed.put("Links",new net.minecraft.nbt.NbtList());lamp.readNbt(malformed);context.assertTrue(lamp.links().isEmpty(),"malformed or missing persisted links restore safely");
  context.assertTrue(!LampPolicy.validName("" )&&!LampPolicy.validRoute(256,256,false),"lamp persistence rejects empty names and over-capacity routes");context.complete();
 }
 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=180,batchId="rp_objects")
 public void locksPreventDirectAndLeverStateChanges(TestContext context){
  ServerWorld world=context.getWorld();RpObjectEntity door=required("door_1",world,context.getAbsolutePos(new BlockPos(2,2,2))),lever=required("lever_1",world,context.getAbsolutePos(new BlockPos(5,2,2)));var player=context.createMockCreativeServerPlayerInWorld();
  context.assertTrue(lever.addLink(door),"RP lever links allowed same-world target");lever.interact(player,Hand.MAIN_HAND);context.assertTrue(lever.isOpen()&&!door.isOpen(),"lever starts pulse before target activation");
  context.runAtTick(69,()->context.assertTrue(!door.isOpen(),"lever does not activate before 70 ticks"));
  context.runAtTick(71,()->{
   context.assertTrue(door.isOpen()&&!lever.isOpen(),"pulse opens door and resets lever after 70 ticks");
   door.setLocked(true);context.assertTrue(door.interact(player,Hand.MAIN_HAND)==ActionResult.FAIL,"locked door rejects direct use");lever.interact(player,Hand.MAIN_HAND);
  });
  context.runAtTick(142,()->{
   context.assertTrue(door.isOpen()&&!lever.isOpen(),"locked target ignores second pulse, lever still resets");
   door.setLocked(false);door.setYaw(90);player.refreshPositionAndAngles(door.getX(),door.getY(),door.getZ(),0,0);context.assertTrue(!door.setOpen(false),"occupied rotated doorway refuses close");
   lever.setLocked(true);context.assertTrue(lever.interact(player,Hand.MAIN_HAND)==ActionResult.FAIL,"locked lever rejects activation");door.discard();lever.discard();player.discard();context.complete();
  });
 }
 private static RpObjectEntity required(String id,ServerWorld world,BlockPos pos){EntityType<RpObjectEntity> type=ObjectRegistry.TYPES.get(id);if(type==null)throw new AssertionError("missing object "+id);RpObjectEntity entity=type.create(world);if(entity==null)throw new AssertionError("factory returned null "+id);entity.refreshPositionAndAngles(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);if(!world.spawnEntity(entity))throw new AssertionError("spawn failed "+id);return entity;}
}
