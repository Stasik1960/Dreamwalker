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
 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=80,batchId="rp_objects")
 public void canonicalHunterLampAndMalformedNbtAreSafe(TestContext context){
  ServerWorld world=context.getWorld();EntityType<RpObjectEntity> type=ObjectRegistry.TYPES.get("hunterlamp");context.assertTrue(type!=null,"catalog hunterlamp is registered as an RP object");
  RpObjectEntity lamp=type.create(world);context.assertTrue(lamp!=null&&lamp.assetId().equals("hunterlamp"),"hunterlamp factory uses the canonical asset id");
  NbtCompound malformed=new NbtCompound();malformed.putBoolean("Open",true);malformed.put("Links",new net.minecraft.nbt.NbtList());lamp.readNbt(malformed);context.assertTrue(lamp.links().isEmpty(),"malformed or missing persisted links restore safely");
  context.assertTrue(!LampPolicy.validName("" )&&!LampPolicy.validRoute(256,256,false),"lamp persistence rejects empty names and over-capacity routes");context.complete();
 }
 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=80,batchId="rp_objects")
 public void locksPreventDirectAndLeverStateChanges(TestContext context){
  ServerWorld world=context.getWorld();RpObjectEntity door=required("door_1",world,context.getAbsolutePos(new BlockPos(2,2,2))),lever=required("lever_1",world,context.getAbsolutePos(new BlockPos(5,2,2)));var player=context.createMockCreativeServerPlayerInWorld();
  context.assertTrue(lever.addLink(door),"lever links only an allowed same-world target");lever.interact(player,Hand.MAIN_HAND);context.assertTrue(door.isOpen()&&lever.isOpen(),"unlocked linked lever toggles its own tracked state and opens door");
  door.setYaw(90);door.refreshCollider();player.refreshPositionAndAngles(door.getX(),door.getY(),door.getZ(),0,0);lever.interact(player,Hand.MAIN_HAND);context.assertTrue(door.isOpen(),"occupied rotated doorway refuses to close through a player");
  door.setLocked(true);context.assertTrue(door.interact(player,Hand.MAIN_HAND)==ActionResult.FAIL,"locked door rejects direct use");lever.interact(player,Hand.MAIN_HAND);context.assertTrue(door.isOpen(),"locked target ignores linked lever toggle");
  door.setLocked(false);lever.setLocked(true);context.assertTrue(lever.interact(player,Hand.MAIN_HAND)==ActionResult.FAIL,"locked lever rejects activation");door.discard();lever.discard();player.discard();context.complete();
 }
 private static RpObjectEntity required(String id,ServerWorld world,BlockPos pos){EntityType<RpObjectEntity> type=ObjectRegistry.TYPES.get(id);if(type==null)throw new AssertionError("missing object "+id);RpObjectEntity entity=type.create(world);if(entity==null)throw new AssertionError("factory returned null "+id);entity.refreshPositionAndAngles(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);if(!world.spawnEntity(entity))throw new AssertionError("spawn failed "+id);return entity;}
}
