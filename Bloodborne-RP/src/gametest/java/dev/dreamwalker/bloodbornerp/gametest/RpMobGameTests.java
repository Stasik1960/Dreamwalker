package dev.dreamwalker.bloodbornerp.gametest;

import dev.dreamwalker.bloodbornerp.BloodborneRp;
import dev.dreamwalker.bloodbornerp.content.AssetCatalog;
import dev.dreamwalker.bloodbornerp.mob.MobRegistry;
import dev.dreamwalker.bloodbornerp.mob.RpMobEntity;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Dedicated-server coverage for explicitly summoned RP encounter mobs. */
public final class RpMobGameTests implements FabricGameTest {
 private static final Set<String> MOB_IDS = Set.of(
  "cleric_beast", "vicar_amelia", "blood_starved_beast", "scourge_beast", "executioner",
  "huntsman_a", "huntsman_b", "huntsman_c", "huntsman_d", "huntsman_wheelchair",
  "large_huntsman", "carrion_crow", "giant_rat", "small_rat", "rabid_dog", "maneater_boar",
  "brick_troll", "rotted_corpse");

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="rp_mobs")
 public void allEncounterMobsRegisterEggsAndUseVanillaHealth(TestContext context) {
  ServerWorld world = context.getWorld();
  context.assertTrue(MobRegistry.TYPES.keySet().equals(MOB_IDS), "exactly the audited 18 encounter mobs are registered");
  context.assertTrue(MOB_IDS.stream().map(RpMobEntity::maxHealthFor).distinct().count() > 1,
   "encounter profiles use varied, conventional vanilla health values");
  for (String id : MOB_IDS) {
   EntityType<RpMobEntity> type = MobRegistry.TYPES.get(id);
   context.assertTrue(type != null && Registries.ENTITY_TYPE.getId(type).equals(BloodborneRp.id(id)), "entity type is registered for " + id);
   context.assertTrue(Registries.ITEM.containsId(BloodborneRp.id(id + "_spawn_egg")), "spawn egg is registered for " + id);
   RpMobEntity mob = type.create(world);
   context.assertTrue(mob != null, "entity factory creates " + id);
   BlockPos spawn = context.getAbsolutePos(new BlockPos(2, 2, 2));
   mob.refreshPositionAndAngles(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D, 0.0F, 0.0F);
   context.assertTrue(world.spawnEntity(mob), "entity spawns in server world: " + id);
   context.assertTrue(mob.getMaxHealth() == (float)RpMobEntity.maxHealthFor(id) && mob.getHealth() == mob.getMaxHealth(),
    "configured vanilla health initializes correctly for " + id);
   context.assertTrue(mob.usesRangedAttack() == (id.equals("huntsman_d") || id.equals("huntsman_wheelchair")),
    "only rifle and wheelchair huntsmen use the ranged attack path: " + id);
   float before = mob.getHealth();
   context.assertTrue(mob.damage(world.getDamageSources().generic(), 3.0F) && mob.getHealth() == before - 3.0F,
    "ordinary vanilla damage source affects " + id);
   mob.discard();
  }
  context.complete();
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=80,batchId="rp_mobs")
 public void freezeStatePersistsAndBossAssetsAreStable(TestContext context) {
  ServerWorld world = context.getWorld();
  RpMobEntity original = required("cleric_beast", world);
  original.setFrozen(true);
  NbtCompound saved = new NbtCompound();
  original.writeNbt(saved);
  RpMobEntity restored = required("cleric_beast", world);
  restored.readNbt(saved);
  context.assertTrue(restored.getFrozen() && restored.isAiDisabled(), "DM freeze survives an entity NBT round trip");
  for (String boss : Set.of("cleric_beast", "vicar_amelia", "blood_starved_beast")) {
   RpMobEntity first = required(boss, world);
   RpMobEntity second = required(boss, world);
   String displayName = AssetCatalog.get(boss).displayName();
   context.assertTrue(first.assetId().equals(boss) && first.asset() == second.asset()
    && displayName != null && !displayName.isBlank(), "boss reuses its named catalog asset: " + boss);
  }
  original.discard(); restored.discard(); context.complete();
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=80,batchId="rp_mobs")
 public void playerHealthAndOptionalSharedPartStayVanilla(TestContext context) {
  ServerWorld world = context.getWorld();
  var player = context.createMockSurvivalPlayer();
  float before = player.getHealth();
  context.assertTrue(player.damage(world.getDamageSources().generic(), 2.0F) && player.getHealth() == before - 2.0F,
   "mob subsystem does not replace ordinary player health or generic damage");
  Identifier sharedPart = new Identifier("bloodborne_blocks", "o_barrel");
  if(Boolean.getBoolean("bbrp.coexistence.required"))context.assertTrue(Registries.BLOCK.containsId(sharedPart),"current Bloodborne-Blocks is loaded for coexistence testing");
  if (Registries.BLOCK.containsId(sharedPart)) {
   Block part = Registries.BLOCK.get(sharedPart);
   BlockPos position = context.getAbsolutePos(new BlockPos(6, 2, 6));
   world.setBlockState(position, part.getDefaultState());
   RpMobEntity mob = required("huntsman_a", world);
   mob.refreshPositionAndAngles(position.getX() - 2.0D, position.getY(), position.getZ(), 0.0F, 0.0F);
   world.spawnEntity(mob);
   mob.setTarget(player);
   context.runAtTick(20, () -> {
    context.assertTrue(world.getBlockState(position).isOf(part), "a nearby mob attack leaves the optional shared architecture part unchanged");
    mob.discard(); player.discard(); context.complete();
   });
   return;
  }
  player.discard(); context.complete();
 }

 private static RpMobEntity required(String id, ServerWorld world) {
  EntityType<RpMobEntity> type = MobRegistry.TYPES.get(id);
  if (type == null) throw new AssertionError("missing RP mob " + id);
  RpMobEntity mob = type.create(world);
  if (mob == null) throw new AssertionError("factory returned null for " + id);
  return mob;
 }
}
