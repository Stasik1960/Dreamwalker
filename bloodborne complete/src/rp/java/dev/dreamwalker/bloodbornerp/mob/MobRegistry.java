package dev.dreamwalker.bloodbornerp.mob;

import dev.dreamwalker.bloodbornerp.BloodborneRp;
import dev.dreamwalker.bloodbornerp.content.AssetCatalog;
import dev.dreamwalker.bloodbornerp.content.AssetSpec;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/** Explicitly registered encounter mobs. No biome spawn rules are installed here. */
public final class MobRegistry {
 public static final Map<String, EntityType<RpMobEntity>> TYPES = new LinkedHashMap<>();
 private static boolean registered;
 private static final List<String> IDS = List.of(
  "cleric_beast", "vicar_amelia", "blood_starved_beast", "scourge_beast", "executioner",
  "huntsman_a", "huntsman_b", "huntsman_c", "huntsman_d", "huntsman_wheelchair",
  "large_huntsman", "carrion_crow", "giant_rat", "small_rat", "rabid_dog", "maneater_boar",
  "brick_troll", "rotted_corpse");

 private MobRegistry() {}

 public static void register() {
  if (registered) return;
  registered = true;
  for (String id : IDS) registerMob(id);
 }

 private static void registerMob(String id) {
  AssetSpec asset = AssetCatalog.get(id);
  EntityType<RpMobEntity> type = Registry.register(Registries.ENTITY_TYPE, BloodborneRp.id(id),
   FabricEntityTypeBuilder.<RpMobEntity>create(SpawnGroup.MONSTER,
    (entityType, world) -> new RpMobEntity(entityType, world, id))
    .dimensions(EntityDimensions.fixed(asset.width(), asset.height())).trackRangeChunks(8).build());
  TYPES.put(id, type);
  FabricDefaultAttributeRegistry.register(type, RpMobEntity.createAttributes(id));
  Registry.register(Registries.ITEM, BloodborneRp.id(id + "_spawn_egg"),
   new dev.dreamwalker.bloodbornedw.debug.CatalogueSpawnEggItem(type, eggPrimary(id), eggSecondary(id), new Item.Settings()));
 }

 private static int eggPrimary(String id) {
  return switch (id) {
   case "cleric_beast", "vicar_amelia", "blood_starved_beast" -> 0x6E4A3A;
   case "huntsman_wheelchair", "executioner", "brick_troll" -> 0x51463F;
   default -> 0x7A6556;
  };
 }

 private static int eggSecondary(String id) {
  return switch (id) {
   case "blood_starved_beast", "rabid_dog" -> 0x9B3124;
   case "carrion_crow", "rotted_corpse" -> 0x27201B;
   default -> 0xC9B69B;
  };
 }
}
