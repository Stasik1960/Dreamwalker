package daot;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;
import daot.advancement.ModCriteriaTriggers;
import daot.advancement.VillageAdvancementHandler;
import daot.network.ArmorPotionDrinkPayload;
import daot.network.ModNetworking;
import daot.network.PoweredVillagerSyncPayload;
import daot.world.IceburstOreGenerator;
import daot.world.OverworldDocksGenerator;
import daot.world.ParadisBiomeSource;
import daot.world.ParadisChunkGenerator;
import daot.world.ParadisReturnPillarGenerator;
import daot.world.PathsChunkGenerator;
import daot.world.TitanCountryStructureGenerator;
import daot.world.VillagePopulator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.Load;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.Unload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStarted;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopped;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopping;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.StartTick;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import daot.compat.network.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents.StartTracking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Disconnect;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Join;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.dispenser.DispenserBehavior;
import net.minecraft.block.entity.BlockEntityType;
import daot.compat.components.ComponentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnRestriction.Location;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.EntityType.Builder;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ArmorMaterials;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.item.ArmorItem.Type;
import net.minecraft.item.Item.Settings;
import net.minecraft.world.World;
import net.minecraft.client.item.TooltipContext;
import daot.compat.network.PacketCodecs;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.SpecialRecipeSerializer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Position;
import net.minecraft.world.World;
import net.minecraft.world.GameRules.BooleanRule;
import net.minecraft.world.GameRules.Category;
import net.minecraft.world.GameRules.Key;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.noise.NoiseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DannysAot implements ModInitializer {
   public static final String MOD_ID = "dannys-aot";
   public static final Logger LOGGER = LoggerFactory.getLogger("dannys-aot");
   public static Key<BooleanRule> RULE_TITAN_GRIEFING;
   public static Key<BooleanRule> RULE_FAIR_TITAN_POWER_LOSS;
   public static Key<BooleanRule> RULE_VILLAGERS_SPAWN_WITH_POWERS;
   public static Key<BooleanRule> RULE_INJECT_OTHER_PLAYERS;
   public static Key<BooleanRule> RULE_ALLOW_ODM;
   public static Key<BooleanRule> RULE_ALLOW_SHIFTING;
   public static Key<BooleanRule> RULE_ALLOW_SELF_INJECT;
   public static Key<BooleanRule> RULE_ALLOW_THUNDER_SPEARS;
   public static Key<BooleanRule> RULE_THUNDER_SPEAR_GRIEFING;
   public static Key<BooleanRule> RULE_UNFAIR_PURE_TITANS;
   public static Key<BooleanRule> RULE_ALLOW_CUFFING;
   public static Key<BooleanRule> RULE_SHIFTER_SNITCHING;
   public static Key<BooleanRule> RULE_SHIFT_BOSS_BARS;
   public static Key<BooleanRule> RULE_SHIFTER_EXPLOSION_DAMAGE;
   public static Key<BooleanRule> RULE_YMIR_CURSE;
   public static Key<BooleanRule> RULE_TITAN_GORE;
   public static Key<BooleanRule> RULE_MULTIPLE_SHIFTERS;
   public static Key<BooleanRule> RULE_CONFIRMED_TITAN_SHIFTER_KILL;
   public static Key<BooleanRule> RULE_WEATHER_AFFECTS_ODM;
   public static Key<BooleanRule> RULE_KINETIC_ODM_DAMAGE;
   public static Key<BooleanRule> RULE_FORCE_SHIFTING;
   public static Key<BooleanRule> RULE_REALISTIC_RESOURCE_USE;
   public static Key<BooleanRule> RULE_ALLOW_REROLLS;
   public static Key<BooleanRule> RULE_ALLOW_OGRE_SPAWNS;
   public static Key<BooleanRule> RULE_GRASS_ODM;
   public static Key<BooleanRule> RULE_CHARGED_ODM_ATTACKS;
   public static StatusEffect CURSE_OF_YMIR_EFFECT;
   public static final ODMGearItem ODM_GEAR = new ODMGearItem(ArmorMaterials.DIAMOND, Type.LEGGINGS, new Settings().maxCount(1));
   public static final ODMAPGItem ODM_APG = new ODMAPGItem(ArmorMaterials.DIAMOND, Type.LEGGINGS, new Settings().maxCount(1));
   public static final ArmorMaterial APG_SUIT_MATERIAL = new daot.compat.BackportArmorMaterial(new Identifier("dannys-aot", "apg_suit"),
         Map.of(Type.HELMET, 0, Type.CHESTPLATE, 13, Type.LEGGINGS, 0, Type.BOOTS, 0),
         15,
         SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE,
         () -> Ingredient.EMPTY,
         0.0F,
         0.0F
      );
   public static final APGSuitItem APG_SUIT = new APGSuitItem(APG_SUIT_MATERIAL, Type.CHESTPLATE, new Settings().maxCount(1));
   public static final UniformItem UNIFORM = new UniformItem(ArmorMaterials.LEATHER, Type.CHESTPLATE, new Settings().maxCount(1));
   public static final GarrisonUniformItem GARRISON_UNIFORM = new GarrisonUniformItem(ArmorMaterials.LEATHER, Type.CHESTPLATE, new Settings().maxCount(1));
   public static final ArmorMaterial CULTIST_ROBE_MATERIAL = new daot.compat.BackportArmorMaterial(new Identifier("dannys-aot", "cultist_robe"),
         Map.of(Type.HELMET, 0, Type.CHESTPLATE, 4, Type.LEGGINGS, 0, Type.BOOTS, 0),
         15,
         SoundEvents.ITEM_ARMOR_EQUIP_LEATHER,
         () -> Ingredient.EMPTY,
         0.0F,
         0.0F
      );
   public static final CultistRobeItem CULTIST_ROBE = new CultistRobeItem(CULTIST_ROBE_MATERIAL, Type.CHESTPLATE, new Settings().maxCount(1));
   public static final ArmorMaterial RED_SCARF_MATERIAL = new daot.compat.BackportArmorMaterial(new Identifier("dannys-aot", "red_scarf"),
         Map.of(Type.HELMET, 3, Type.CHESTPLATE, 0, Type.LEGGINGS, 0, Type.BOOTS, 0),
         15,
         SoundEvents.ITEM_ARMOR_EQUIP_LEATHER,
         () -> Ingredient.EMPTY,
         0.0F,
         0.0F
      );
   public static final RedScarfItem RED_SCARF = new RedScarfItem(RED_SCARF_MATERIAL, Type.HELMET, new Settings().maxCount(1));
   public static final ScoutUniformItem SCOUT_UNIFORM = new ScoutUniformItem(ArmorMaterials.LEATHER, Type.CHESTPLATE, new Settings().maxCount(1));
   public static final MilitaryPoliceUniformItem MILITARY_POLICE_UNIFORM = new MilitaryPoliceUniformItem(
      ArmorMaterials.LEATHER, Type.CHESTPLATE, new Settings().maxCount(1)
   );
   public static final ArmorMaterial MARLEY_UNIFORM_MATERIAL = new daot.compat.BackportArmorMaterial(new Identifier("dannys-aot", "marley_uniform"),
         Map.of(Type.HELMET, 2, Type.CHESTPLATE, 6, Type.LEGGINGS, 4, Type.BOOTS, 2),
         15,
         SoundEvents.ITEM_ARMOR_EQUIP_LEATHER,
         () -> Ingredient.EMPTY,
         0.0F,
         0.0F
      );
   public static final MarleyUniformItem MARLEY_UNIFORM_HELMET = new MarleyUniformItem(MARLEY_UNIFORM_MATERIAL, Type.HELMET, new Settings().maxCount(1));
   public static final MarleyUniformItem MARLEY_UNIFORM_CHESTPLATE = new MarleyUniformItem(MARLEY_UNIFORM_MATERIAL, Type.CHESTPLATE, new Settings().maxCount(1));
   public static final MarleyUniformItem MARLEY_UNIFORM_LEGGINGS = new MarleyUniformItem(MARLEY_UNIFORM_MATERIAL, Type.LEGGINGS, new Settings().maxCount(1));
   public static final MarleyUniformItem MARLEY_UNIFORM_BOOTS = new MarleyUniformItem(MARLEY_UNIFORM_MATERIAL, Type.BOOTS, new Settings().maxCount(1));
   public static final MarleyEldianUniformItem MARLEY_ELDIAN_UNIFORM_HELMET = new MarleyEldianUniformItem(
      MARLEY_UNIFORM_MATERIAL, Type.HELMET, new Settings().maxCount(1)
   );
   public static final MarleyEldianUniformItem MARLEY_ELDIAN_UNIFORM_CHESTPLATE = new MarleyEldianUniformItem(
      MARLEY_UNIFORM_MATERIAL, Type.CHESTPLATE, new Settings().maxCount(1)
   );
   public static final CloakItem BLACK_CLOAK = new CloakItem(ArmorMaterials.IRON, new Settings().maxCount(1));
   public static final CloakItem GREEN_CLOAK = new CloakItem(ArmorMaterials.IRON, new Settings().maxCount(1));
   public static final CloakItem GREEN_SCOUT_CLOAK = new CloakItem(ArmorMaterials.IRON, new Settings().maxCount(1));
   public static final CloakItem GREEN_GARRISON_CLOAK = new CloakItem(ArmorMaterials.IRON, new Settings().maxCount(1));
   public static final CloakItem GREEN_MILITARY_POLICE_CLOAK = new CloakItem(ArmorMaterials.IRON, new Settings().maxCount(1));
   public static final CloakItem GOLD_CLOAK = new CloakItem(ArmorMaterials.IRON, new Settings().maxCount(1));
   public static final ArmorMaterial ROYAL_ARMOR_MATERIAL = new daot.compat.BackportArmorMaterial(new Identifier("dannys-aot", "royal"),
         Map.of(Type.HELMET, 4, Type.CHESTPLATE, 0, Type.LEGGINGS, 0, Type.BOOTS, 0),
         15,
         SoundEvents.ITEM_ARMOR_EQUIP_GOLD,
         () -> Ingredient.EMPTY,
         0.0F,
         0.0F
      );
   public static final CloakItem ROYAL_CLOAK = new CloakItem(ROYAL_ARMOR_MATERIAL, new Settings().maxCount(1).rarity(Rarity.EPIC));
   public static final BladeItem BLADE = new BladeItem();
   public static final BladeComponentItem BLADE_COMPONENT = new BladeComponentItem();
   public static final APGGunItem APG_GUN = new APGGunItem();
   public static final APGCartridgeItem APG_CARTRIDGE = new APGCartridgeItem();
   public static final ODMBootsItem ODM_BOOTS = new ODMBootsItem(ArmorMaterials.IRON, Type.BOOTS, new Settings().maxCount(1));
   public static final ZekesGlassesItem ZEKES_GLASSES = new ZekesGlassesItem(ArmorMaterials.LEATHER, Type.HELMET, new Settings().maxCount(1));
   public static final Block PATH_SAND = new PathSandBlock();
   public static final BlockItem PATH_SAND_ITEM = new BlockItem(PATH_SAND, new Settings());
   public static final Block DARK_PATH_SAND = new DarkPathSandBlock();
   public static final BlockItem DARK_PATH_SAND_ITEM = new BlockItem(DARK_PATH_SAND, new Settings());
   public static final Block PATH_SAND_SLAB = new PathSandSlabBlock();
   public static final BlockItem PATH_SAND_SLAB_ITEM = new BlockItem(PATH_SAND_SLAB, new Settings());
   public static final Block DARK_PATH_SAND_SLAB = new DarkPathSandSlabBlock();
   public static final BlockItem DARK_PATH_SAND_SLAB_ITEM = new BlockItem(DARK_PATH_SAND_SLAB, new Settings());
   public static final Block ICE_BURST_STONE = new IceBurstStoneBlock();
   public static final BlockItem ICE_BURST_STONE_ITEM = new BlockItem(ICE_BURST_STONE, new Settings());
   public static final Block BURSTING_ICE_BURST_STONE = new BurstingIceBurstStoneBlock();
   public static final BlockItem BURSTING_ICE_BURST_STONE_ITEM = new BlockItem(BURSTING_ICE_BURST_STONE, new Settings());
   public static final Block SMALL_ICE_BURST_SHARD = IceBurstShardBlock.createSmall();
   public static final BlockItem SMALL_ICE_BURST_SHARD_ITEM = new BlockItem(SMALL_ICE_BURST_SHARD, new Settings());
   public static final Block MEDIUM_ICE_BURST_SHARD = IceBurstShardBlock.createMedium();
   public static final BlockItem MEDIUM_ICE_BURST_SHARD_ITEM = new BlockItem(MEDIUM_ICE_BURST_SHARD, new Settings());
   public static final Block LARGE_ICE_BURST_SHARD = IceBurstShardBlock.createLarge();
   public static final BlockItem LARGE_ICE_BURST_SHARD_ITEM = new BlockItem(LARGE_ICE_BURST_SHARD, new Settings());
   public static final Block ICE_BURST_SHARD_CLUSTER = IceBurstShardBlock.createCluster();
   public static final BlockItem ICE_BURST_SHARD_CLUSTER_ITEM = new BlockItem(ICE_BURST_SHARD_CLUSTER, new Settings());
   public static final Block HARDENED_BLOCK = new HardenedBlock();
   public static final BlockItem HARDENED_BLOCK_ITEM = new BlockItem(HARDENED_BLOCK, new Settings());
   public static final Block HARDENED_SLAB = new HardenedSlabBlock();
   public static final BlockItem HARDENED_SLAB_ITEM = new BlockItem(HARDENED_SLAB, new Settings());
   public static final Block HARDENED_STAIRS = new HardenedStairBlock(HARDENED_BLOCK);
   public static final BlockItem HARDENED_STAIRS_ITEM = new BlockItem(HARDENED_STAIRS, new Settings());
   public static final Block ULTRA_HARDENED_BLOCK = new HardenedBlock();
   public static final BlockItem ULTRA_HARDENED_BLOCK_ITEM = new BlockItem(ULTRA_HARDENED_BLOCK, new Settings());
   public static final Block ULTRA_HARDENED_SLAB = new UltraHardenedSlabBlock();
   public static final BlockItem ULTRA_HARDENED_SLAB_ITEM = new BlockItem(ULTRA_HARDENED_SLAB, new Settings());
   public static final Block ULTRA_HARDENED_STAIRS = new UltraHardenedStairBlock(ULTRA_HARDENED_BLOCK);
   public static final BlockItem ULTRA_HARDENED_STAIRS_ITEM = new BlockItem(ULTRA_HARDENED_STAIRS, new Settings());
   public static final Block BLOCK_OF_ICEBURST = new BlockOfIceburst();
   public static final BlockItem BLOCK_OF_ICEBURST_ITEM = new BlockItem(BLOCK_OF_ICEBURST, new Settings());
   public static final Block BAMBOO_LEAVES = new BambooLeavesBlock();
   public static final BlockItem BAMBOO_LEAVES_ITEM = new BlockItem(BAMBOO_LEAVES, new Settings());
   public static final Block IRON_BAMBOO_SAPLING = new IronBambooSaplingBlock();
   public static final Block IRON_BAMBOO = new IronBambooBlock();
   public static final IronBambooItem IRON_BAMBOO_ITEM = new IronBambooItem(new Settings());
   public static final Item HARDENED_IRON_BAMBOO = new Item(new Settings());
   public static final Block ICEBURST_FURNACE = new IceburstFurnaceBlock(
      net.minecraft.block.AbstractBlock.Settings.create()
         .strength(3.5F)
         .sounds(BlockSoundGroup.METAL)
         .luminance(state -> state.get(IceburstFurnaceBlock.LIT) ? 13 : 0)
   );
   public static final BlockItem ICEBURST_FURNACE_ITEM = new BlockItem(ICEBURST_FURNACE, new Settings());
   public static BlockEntityType<IceburstFurnaceBlockEntity> ICEBURST_FURNACE_BLOCK_ENTITY;
   public static ScreenHandlerType<IceburstFurnaceMenu> ICEBURST_FURNACE_MENU;
   public static final Block PARADIS_PORTAL = new ParadisPortalBlock();
   public static BlockEntityType<ParadisPortalBlockEntity> PARADIS_PORTAL_BLOCK_ENTITY;
   public static final TitanDummyBlock TITAN_DUMMY_BLOCK = new TitanDummyBlock();
   public static final BlockItem TITAN_DUMMY_ITEM = new BlockItem(TITAN_DUMMY_BLOCK, new Settings()) {
      @Override
      public ActionResult useOnBlock(ItemUsageContext context) {
         if (context.getWorld().isClient()) {
            return ActionResult.SUCCESS;
         } else {
            BlockPos clickedPos = context.getBlockPos().up();
            if (context.getSide() != Direction.UP) {
               return ActionResult.FAIL;
            } else if (TitanDummyBlock.tryPlace(context.getWorld(), clickedPos)) {
               if (!context.getPlayer().getAbilities().creativeMode) {
                  context.getStack().decrement(1);
               }

               return ActionResult.CONSUME;
            } else {
               return ActionResult.FAIL;
            }
         }
      }
   };
   public static final EntityType<TitanDummyEntity> TITAN_DUMMY = Builder.<TitanDummyEntity>create(TitanDummyEntity::new, SpawnGroup.MISC)
      .setDimensions(2.0F, 11.5F)
      .disableSummon()
      .build("titan_dummy");
   public static final EntityType<TitanDummyNapeEntity> TITAN_DUMMY_NAPE = Builder.<TitanDummyNapeEntity>create(TitanDummyNapeEntity::new, SpawnGroup.MISC)
      .setDimensions(0.65F, 0.65F)
      .disableSummon()
      .build("titan_dummy_nape");
   public static final EntityType<TitanDummyEyeEntity> TITAN_DUMMY_EYE = Builder.<TitanDummyEyeEntity>create(TitanDummyEyeEntity::new, SpawnGroup.MISC)
      .setDimensions(0.8F, 0.8F)
      .disableSummon()
      .build("titan_dummy_eye");
   public static final EntityType<TitanEntity> TITAN = Builder.<TitanEntity>create(TitanEntity::new, SpawnGroup.MONSTER)
      .setDimensions(2.0F, 15.0F)
      .build("titan");
   public static final EntityType<ColossalTitanEntity> COLOSSAL_TITAN = Builder.<ColossalTitanEntity>create(ColossalTitanEntity::new, SpawnGroup.CREATURE)
      .setDimensions(9.3F, 60.0F)
      .maxTrackingRange(32)
      .build("colossal_titan");
   public static final EntityType<AttackTitanEntity> ATTACK_TITAN = Builder.<AttackTitanEntity>create(AttackTitanEntity::new, SpawnGroup.CREATURE)
      .setDimensions(2.3F, 15.0F)
      .maxTrackingRange(32)
      .build("attack_titan");
   public static final EntityType<OgreShifterTitanEntity> OGRE_SHIFTER_TITAN = Builder.<OgreShifterTitanEntity>create(OgreShifterTitanEntity::new, SpawnGroup.CREATURE)
      .setDimensions(1.7F, 10.0F)
      .maxTrackingRange(32)
      .disableSummon()
      .build("ogre_shifter_titan");
   public static final EntityType<TripleTTitanEntity> TRIPLE_T_TITAN = Builder.<TripleTTitanEntity>create(TripleTTitanEntity::new, SpawnGroup.CREATURE)
      .setDimensions(2.3F, 15.0F)
      .maxTrackingRange(32)
      .disableSummon()
      .build("triple_t_titan");
   public static final EntityType<TestShifterTitanEntity> TEST_SHIFTER_TITAN = Builder.<TestShifterTitanEntity>create(TestShifterTitanEntity::new, SpawnGroup.CREATURE)
      .setDimensions(0.8F, 5.5F)
      .maxTrackingRange(32)
      .disableSummon()
      .build("test_shifter_titan");
   public static final EntityType<CartShifterTitanEntity> CART_SHIFTER_TITAN = Builder.<CartShifterTitanEntity>create(CartShifterTitanEntity::new, SpawnGroup.CREATURE)
      .setDimensions(1.6F, 4.0F)
      .maxTrackingRange(32)
      .disableSummon()
      .build("cart_shifter_titan");
   public static final EntityType<ArmoredTitanEntity> ARMORED_TITAN = Builder.<ArmoredTitanEntity>create(ArmoredTitanEntity::new, SpawnGroup.CREATURE)
      .setDimensions(2.3F, 15.0F)
      .maxTrackingRange(32)
      .build("armored_titan");
   public static final EntityType<FemaleTitanEntity> FEMALE_TITAN = Builder.<FemaleTitanEntity>create(FemaleTitanEntity::new, SpawnGroup.CREATURE)
      .setDimensions(2.1F, 14.0F)
      .maxTrackingRange(32)
      .build("female_titan");
   public static final EntityType<WarhammerTitanEntity> WARHAMMER_TITAN = Builder.<WarhammerTitanEntity>create(WarhammerTitanEntity::new, SpawnGroup.CREATURE)
      .setDimensions(2.0F, 15.0F)
      .maxTrackingRange(32)
      .disableSummon()
      .build("warhammer_titan");
   public static final EntityType<SmallTitanEntity> SMALL_TITAN = Builder.<SmallTitanEntity>create(SmallTitanEntity::new, SpawnGroup.MONSTER)
      .setDimensions(0.7F, 4.0F)
      .build("small_titan");
   public static final EntityType<SmallTitanNapeEntity> SMALL_TITAN_NAPE = Builder.<SmallTitanNapeEntity>create(SmallTitanNapeEntity::new, SpawnGroup.MISC)
      .setDimensions(0.98F, 0.98F)
      .disableSummon()
      .build("small_titan_nape");
   public static final EntityType<SmallTitanEyeEntity> SMALL_TITAN_EYE = Builder.<SmallTitanEyeEntity>create(SmallTitanEyeEntity::new, SpawnGroup.MISC)
      .setDimensions(1.17F, 1.17F)
      .disableSummon()
      .build("small_titan_eye");
   public static final EntityType<SmallTitan2Entity> SMALL_TITAN_2 = Builder.<SmallTitan2Entity>create(SmallTitan2Entity::new, SpawnGroup.MONSTER)
      .setDimensions(1.2F, 6.0F)
      .build("small_titan_2");
   public static final EntityType<SmallTitan2NapeEntity> SMALL_TITAN_2_NAPE = Builder.<SmallTitan2NapeEntity>create(SmallTitan2NapeEntity::new, SpawnGroup.MISC)
      .setDimensions(0.94F, 0.94F)
      .disableSummon()
      .build("small_titan_2_nape");
   public static final EntityType<SmallTitan2EyeEntity> SMALL_TITAN_2_EYE = Builder.<SmallTitan2EyeEntity>create(SmallTitan2EyeEntity::new, SpawnGroup.MISC)
      .setDimensions(0.6F, 0.6F)
      .disableSummon()
      .build("small_titan_2_eye");
   public static final EntityType<SadTitanEntity> SAD_TITAN = Builder.<SadTitanEntity>create(SadTitanEntity::new, SpawnGroup.MONSTER)
      .setDimensions(0.8F, 6.0F)
      .build("sad_titan");
   public static final EntityType<SadTitanNapeEntity> SAD_TITAN_NAPE = Builder.<SadTitanNapeEntity>create(SadTitanNapeEntity::new, SpawnGroup.MISC)
      .setDimensions(0.98F, 0.98F)
      .disableSummon()
      .build("sad_titan_nape");
   public static final EntityType<SadTitanEyeEntity> SAD_TITAN_EYE = Builder.<SadTitanEyeEntity>create(SadTitanEyeEntity::new, SpawnGroup.MISC)
      .setDimensions(1.17F, 1.17F)
      .disableSummon()
      .build("sad_titan_eye");
   public static final EntityType<YellowTitanEntity> YELLOW_TITAN = Builder.<YellowTitanEntity>create(YellowTitanEntity::new, SpawnGroup.MONSTER)
      .setDimensions(0.9F, 5.0F)
      .build("yellow_titan");
   public static final EntityType<YellowTitanNapeEntity> YELLOW_TITAN_NAPE = Builder.<YellowTitanNapeEntity>create(YellowTitanNapeEntity::new, SpawnGroup.MISC)
      .setDimensions(0.78F, 0.78F)
      .disableSummon()
      .build("yellow_titan_nape");
   public static final EntityType<YellowTitanEyeEntity> YELLOW_TITAN_EYE = Builder.<YellowTitanEyeEntity>create(YellowTitanEyeEntity::new, SpawnGroup.MISC)
      .setDimensions(0.5F, 0.5F)
      .disableSummon()
      .build("yellow_titan_eye");
   public static final EntityType<CrawlerTitanEntity> CRAWLER_TITAN = Builder.<CrawlerTitanEntity>create(CrawlerTitanEntity::new, SpawnGroup.MONSTER)
      .setDimensions(1.3F, 3.0F)
      .build("crawler_titan");
   public static final EntityType<CrawlerTitanNapeEntity> CRAWLER_TITAN_NAPE = Builder.<CrawlerTitanNapeEntity>create(CrawlerTitanNapeEntity::new, SpawnGroup.MISC)
      .setDimensions(0.78F, 0.78F)
      .disableSummon()
      .build("crawler_titan_nape");
   public static final EntityType<CrawlerTitanEyeEntity> CRAWLER_TITAN_EYE = Builder.<CrawlerTitanEyeEntity>create(CrawlerTitanEyeEntity::new, SpawnGroup.MISC)
      .setDimensions(0.5F, 0.5F)
      .disableSummon()
      .build("crawler_titan_eye");
   public static final EntityType<FritzTitanEntity> FRITZ_TITAN = Builder.<FritzTitanEntity>create(FritzTitanEntity::new, SpawnGroup.MONSTER)
      .setDimensions(2.0F, 15.0F)
      .build("fritz_titan");
   public static final EntityType<FritzTitanNapeEntity> FRITZ_TITAN_NAPE = Builder.<FritzTitanNapeEntity>create(FritzTitanNapeEntity::new, SpawnGroup.MISC)
      .setDimensions(1.3F, 1.3F)
      .disableSummon()
      .build("fritz_titan_nape");
   public static final EntityType<FritzTitanEyeEntity> FRITZ_TITAN_EYE = Builder.<FritzTitanEyeEntity>create(FritzTitanEyeEntity::new, SpawnGroup.MISC)
      .setDimensions(1.0F, 1.0F)
      .disableSummon()
      .build("fritz_titan_eye");
   public static final EntityType<TitanBeardEntity> TITAN_BEARD = Builder.<TitanBeardEntity>create(TitanBeardEntity::new, SpawnGroup.MONSTER)
      .setDimensions(2.0F, 13.0F)
      .build("titan_beard");
   public static final EntityType<TitanBeardNapeEntity> TITAN_BEARD_NAPE = Builder.<TitanBeardNapeEntity>create(TitanBeardNapeEntity::new, SpawnGroup.MISC)
      .setDimensions(1.3F, 1.3F)
      .disableSummon()
      .build("titan_beard_nape");
   public static final EntityType<TitanBeardEyeEntity> TITAN_BEARD_EYE = Builder.<TitanBeardEyeEntity>create(TitanBeardEyeEntity::new, SpawnGroup.MISC)
      .setDimensions(1.0F, 1.0F)
      .disableSummon()
      .build("titan_beard_eye");
   public static final EntityType<TitanTropicalEntity> TITAN_TROPICAL = Builder.<TitanTropicalEntity>create(TitanTropicalEntity::new, SpawnGroup.MONSTER)
      .setDimensions(2.0F, 12.0F)
      .build("titan_tropical");
   public static final EntityType<TitanTropicalNapeEntity> TITAN_TROPICAL_NAPE = Builder.<TitanTropicalNapeEntity>create(TitanTropicalNapeEntity::new, SpawnGroup.MISC)
      .setDimensions(1.3F, 1.3F)
      .disableSummon()
      .build("titan_tropical_nape");
   public static final EntityType<TitanTropicalEyeEntity> TITAN_TROPICAL_EYE = Builder.<TitanTropicalEyeEntity>create(TitanTropicalEyeEntity::new, SpawnGroup.MISC)
      .setDimensions(1.0F, 1.0F)
      .disableSummon()
      .build("titan_tropical_eye");
   public static final EntityType<AbnormalTitanEntity> ABNORMAL_TITAN = Builder.<AbnormalTitanEntity>create(AbnormalTitanEntity::new, SpawnGroup.MONSTER)
      .setDimensions(1.5F, 11.0F)
      .build("abnormal_titan");
   public static final EntityType<AbnormalTitanNapeEntity> ABNORMAL_TITAN_NAPE = Builder.<AbnormalTitanNapeEntity>create(AbnormalTitanNapeEntity::new, SpawnGroup.MISC)
      .setDimensions(1.3F, 1.3F)
      .disableSummon()
      .build("abnormal_titan_nape");
   public static final EntityType<CrawlingAbnormalTitanEntity> CRAWLING_ABNORMAL_TITAN = Builder.<CrawlingAbnormalTitanEntity>create(CrawlingAbnormalTitanEntity::new, SpawnGroup.MONSTER)
      .setDimensions(1.5F, 6.0F)
      .build("crawling_abnormal_titan");
   public static final EntityType<CrawlingAbnormalTitanNapeEntity> CRAWLING_ABNORMAL_TITAN_NAPE = Builder.<CrawlingAbnormalTitanNapeEntity>create(
         CrawlingAbnormalTitanNapeEntity::new, SpawnGroup.MISC
      )
      .setDimensions(1.3F, 1.3F)
      .disableSummon()
      .build("crawling_abnormal_titan_nape");
   public static final EntityType<ConnieFatherEntity> CONNIE_FATHER = Builder.<ConnieFatherEntity>create(ConnieFatherEntity::new, SpawnGroup.MONSTER)
      .setDimensions(0.5F, 3.0F)
      .build("connie_father");
   public static final EntityType<ConnieFatherNapeEntity> CONNIE_FATHER_NAPE = Builder.<ConnieFatherNapeEntity>create(ConnieFatherNapeEntity::new, SpawnGroup.MISC)
      .setDimensions(0.78F, 0.78F)
      .disableSummon()
      .build("connie_father_nape");
   public static final EntityType<ConnieFatherEyeEntity> CONNIE_FATHER_EYE = Builder.<ConnieFatherEyeEntity>create(ConnieFatherEyeEntity::new, SpawnGroup.MISC)
      .setDimensions(0.9F, 0.9F)
      .disableSummon()
      .build("connie_father_eye");
   public static final EntityType<OgreTitanEntity> OGRE_TITAN = Builder.<OgreTitanEntity>create(OgreTitanEntity::new, SpawnGroup.MONSTER)
      .setDimensions(1.7F, 10.0F)
      .build("ogre_titan");
   public static final EntityType<OgreTitanNapeEntity> OGRE_TITAN_NAPE = Builder.<OgreTitanNapeEntity>create(OgreTitanNapeEntity::new, SpawnGroup.MISC)
      .setDimensions(1.1F, 1.1F)
      .disableSummon()
      .build("ogre_titan_nape");
   public static final EntityType<BeastTitanEntity> BEAST_TITAN = Builder.<BeastTitanEntity>create(BeastTitanEntity::new, SpawnGroup.MONSTER)
      .setDimensions(2.0F, 17.0F)
      .maxTrackingRange(32)
      .build("beast_titan");
   public static final EntityType<BeastTitanNapeEntity> BEAST_TITAN_NAPE = Builder.<BeastTitanNapeEntity>create(BeastTitanNapeEntity::new, SpawnGroup.MISC)
      .setDimensions(1.15F, 1.15F)
      .disableSummon()
      .build("beast_titan_nape");
   public static final EntityType<BeastTitanEyeEntity> BEAST_TITAN_EYE = Builder.<BeastTitanEyeEntity>create(BeastTitanEyeEntity::new, SpawnGroup.MISC)
      .setDimensions(1.0F, 1.0F)
      .disableSummon()
      .build("beast_titan_eye");
   public static final EntityType<BeastTitanGrabEntity> BEAST_TITAN_GRAB = Builder.<BeastTitanGrabEntity>create(BeastTitanGrabEntity::new, SpawnGroup.MISC)
      .setDimensions(1.5F, 1.5F)
      .disableSummon()
      .build("beast_titan_grab");
   public static final EntityType<FoundingTitanEntity> FOUNDING_TITAN = Builder.<FoundingTitanEntity>create(FoundingTitanEntity::new, SpawnGroup.CREATURE)
      .setDimensions(2.3F, 15.0F)
      .maxTrackingRange(32)
      .disableSummon()
      .build("founding_titan");
   public static final EntityType<TitanNapeEntity> TITAN_NAPE = Builder.<TitanNapeEntity>create(TitanNapeEntity::new, SpawnGroup.MISC)
      .setDimensions(1.3F, 1.3F)
      .disableSummon()
      .build("titan_nape");
   public static final EntityType<TitanEyeEntity> TITAN_EYE = Builder.<TitanEyeEntity>create(TitanEyeEntity::new, SpawnGroup.MISC)
      .setDimensions(1.0F, 1.0F)
      .disableSummon()
      .build("titan_eye");
   public static final EntityType<AttackTitanNapeEntity> ATTACK_TITAN_NAPE = Builder.<AttackTitanNapeEntity>create(AttackTitanNapeEntity::new, SpawnGroup.MISC)
      .setDimensions(1.15F, 1.15F)
      .disableSummon()
      .build("attack_titan_nape");
   public static final EntityType<AttackTitanEyeEntity> ATTACK_TITAN_EYE = Builder.<AttackTitanEyeEntity>create(AttackTitanEyeEntity::new, SpawnGroup.MISC)
      .setDimensions(1.15F, 1.15F)
      .disableSummon()
      .build("attack_titan_eye");
   public static final EntityType<AttackTitanGrabEntity> ATTACK_TITAN_GRAB = Builder.<AttackTitanGrabEntity>create(AttackTitanGrabEntity::new, SpawnGroup.MISC)
      .setDimensions(1.0F, 1.0F)
      .disableSummon()
      .build("attack_titan_grab");
   public static final EntityType<ArmoredTitanGrabEntity> ARMORED_TITAN_GRAB = Builder.<ArmoredTitanGrabEntity>create(ArmoredTitanGrabEntity::new, SpawnGroup.MISC)
      .setDimensions(1.0F, 1.0F)
      .disableSummon()
      .build("armored_titan_grab");
   public static final EntityType<ArmoredTitanNapeEntity> ARMORED_TITAN_NAPE = Builder.<ArmoredTitanNapeEntity>create(ArmoredTitanNapeEntity::new, SpawnGroup.MISC)
      .setDimensions(1.15F, 1.15F)
      .disableSummon()
      .build("armored_titan_nape");
   public static final EntityType<ArmoredTitanEyeEntity> ARMORED_TITAN_EYE = Builder.<ArmoredTitanEyeEntity>create(ArmoredTitanEyeEntity::new, SpawnGroup.MISC)
      .setDimensions(1.15F, 1.15F)
      .disableSummon()
      .build("armored_titan_eye");
   public static final EntityType<ArmoredTitanLegEntity> ARMORED_TITAN_LEG = Builder.<ArmoredTitanLegEntity>create(ArmoredTitanLegEntity::new, SpawnGroup.MISC)
      .setDimensions(1.5F, 2.5F)
      .disableSummon()
      .build("armored_titan_leg");
   public static final EntityType<FemaleTitanNapeEntity> FEMALE_TITAN_NAPE = Builder.<FemaleTitanNapeEntity>create(FemaleTitanNapeEntity::new, SpawnGroup.MISC)
      .setDimensions(1.15F, 1.15F)
      .disableSummon()
      .build("female_titan_nape");
   public static final EntityType<FemaleTitanEyeEntity> FEMALE_TITAN_EYE = Builder.<FemaleTitanEyeEntity>create(FemaleTitanEyeEntity::new, SpawnGroup.MISC)
      .setDimensions(1.15F, 1.15F)
      .disableSummon()
      .build("female_titan_eye");
   public static final EntityType<FemaleTitanGrabEntity> FEMALE_TITAN_GRAB = Builder.<FemaleTitanGrabEntity>create(FemaleTitanGrabEntity::new, SpawnGroup.MISC)
      .setDimensions(1.0F, 1.0F)
      .disableSummon()
      .build("female_titan_grab");
   public static final EntityType<WarhammerTitanNapeEntity> WARHAMMER_TITAN_NAPE = Builder.<WarhammerTitanNapeEntity>create(WarhammerTitanNapeEntity::new, SpawnGroup.MISC)
      .setDimensions(1.15F, 1.15F)
      .disableSummon()
      .build("warhammer_titan_nape");
   public static final EntityType<WarhammerTitanEyeEntity> WARHAMMER_TITAN_EYE = Builder.<WarhammerTitanEyeEntity>create(WarhammerTitanEyeEntity::new, SpawnGroup.MISC)
      .setDimensions(1.15F, 1.15F)
      .disableSummon()
      .build("warhammer_titan_eye");
   public static final EntityType<ColossalTitanNapeEntity> COLOSSAL_TITAN_NAPE = Builder.<ColossalTitanNapeEntity>create(ColossalTitanNapeEntity::new, SpawnGroup.MISC)
      .setDimensions(3.0F, 3.0F)
      .disableSummon()
      .build("colossal_titan_nape");
   public static final EntityType<ColossalTitanEyeEntity> COLOSSAL_TITAN_EYE = Builder.<ColossalTitanEyeEntity>create(ColossalTitanEyeEntity::new, SpawnGroup.MISC)
      .setDimensions(3.33F, 3.33F)
      .disableSummon()
      .build("colossal_titan_eye");
   public static final EntityType<ColossalTitanHandEntity> COLOSSAL_TITAN_HAND = Builder.<ColossalTitanHandEntity>create(ColossalTitanHandEntity::new, SpawnGroup.MISC)
      .setDimensions(4.0F, 4.0F)
      .disableSummon()
      .build("colossal_titan_hand");
   public static final EntityType<ThunderSpearEntity> THUNDER_SPEAR_ENTITY = Builder.<ThunderSpearEntity>create(
         (type, level) -> new ThunderSpearEntity(type, level), SpawnGroup.MISC
      )
      .setDimensions(0.5F, 0.5F)
      .maxTrackingRange(8)
      .trackingTickInterval(1)
      .build("thunder_spear");
   public static final EntityType<RockProjectileEntity> ROCK_PROJECTILE = Builder.<RockProjectileEntity>create(
         (type, level) -> new RockProjectileEntity(type, level), SpawnGroup.MISC
      )
      .setDimensions(0.625F, 0.625F)
      .maxTrackingRange(64)
      .trackingTickInterval(1)
      .build("rock_projectile");
   public static final EntityType<FlareProjectileEntity> FLARE_PROJECTILE = Builder.<FlareProjectileEntity>create(
         (type, level) -> new FlareProjectileEntity(type, level), SpawnGroup.MISC
      )
      .setDimensions(0.25F, 0.25F)
      .maxTrackingRange(64)
      .trackingTickInterval(1)
      .build("flare_projectile");
   public static final EntityType<APGProjectileEntity> APG_PROJECTILE = Builder.<APGProjectileEntity>create(
         (type, level) -> new APGProjectileEntity(type, level), SpawnGroup.MISC
      )
      .setDimensions(0.2F, 0.2F)
      .maxTrackingRange(64)
      .trackingTickInterval(1)
      .build("apg_projectile");
   public static final EntityType<GibEntity> GIB = Builder.<GibEntity>create((type, level) -> new GibEntity(type, level), SpawnGroup.MISC)
      .setDimensions(0.3F, 0.3F)
      .maxTrackingRange(64)
      .trackingTickInterval(20)
      .disableSummon()
      .build("gib");
   public static final EntityType<WarhammerSpikeEntity> WARHAMMER_SPIKE = Builder.<WarhammerSpikeEntity>create(
         (type, level) -> new WarhammerSpikeEntity(type, level), SpawnGroup.MISC
      )
      .setDimensions(3.0F, 21.0F)
      .maxTrackingRange(512)
      .trackingTickInterval(20)
      .disableSummon()
      .build("warhammer_spike");
   public static final EntityType<FemaleCrystalShellEntity> CRYSTAL_SHELL_FEMALE = Builder.<FemaleCrystalShellEntity>create(
         (type, level) -> new FemaleCrystalShellEntity(type, level), SpawnGroup.MISC
      )
      .setDimensions(2.5F, 3.5F)
      .maxTrackingRange(32)
      .disableSummon()
      .build("crystal_shell_female");
   public static final EntityType<CrystalShellWarhammerEntity> CRYSTAL_SHELL_WARHAMMER = Builder.<CrystalShellWarhammerEntity>create(
         (type, level) -> new CrystalShellWarhammerEntity(type, level), SpawnGroup.MISC
      )
      .setDimensions(2.5F, 3.5F)
      .maxTrackingRange(32)
      .disableSummon()
      .build("crystal_shell_warhammer");
   public static final DefaultParticleType FLARE_GREEN_PARTICLE = FabricParticleTypes.simple();
   public static final DefaultParticleType FLARE_RED_PARTICLE = FabricParticleTypes.simple();
   public static final DefaultParticleType FLARE_BLACK_PARTICLE = FabricParticleTypes.simple();
   public static final DefaultParticleType FLARE_PURPLE_PARTICLE = FabricParticleTypes.simple();
   public static final DefaultParticleType FLARE_BLUE_PARTICLE = FabricParticleTypes.simple();
   public static final DefaultParticleType FLARE_YELLOW_PARTICLE = FabricParticleTypes.simple();
   public static final DefaultParticleType NAPE_STEAM_PARTICLE = FabricParticleTypes.simple();
   public static final DefaultParticleType SHIFTER_TRAIL_PARTICLE = FabricParticleTypes.simple();
   public static final DefaultParticleType PLAYER_DISMOUNT_PARTICLE = FabricParticleTypes.simple();
   public static final DefaultParticleType NUKE_SMOKE_PARTICLE = FabricParticleTypes.simple();
   public static final DefaultParticleType NUKE_FIRE_PARTICLE = FabricParticleTypes.simple();
   public static final DefaultParticleType NUKE_DUST_PARTICLE = FabricParticleTypes.simple();
   public static final DefaultParticleType COLOSSAL_STEAM_PARTICLE = FabricParticleTypes.simple();
   public static final DefaultParticleType COLOSSAL_NAPE_STEAM_PARTICLE = FabricParticleTypes.simple();
   public static final DefaultParticleType COLOSSAL_NAPE_TRAIL_PARTICLE = FabricParticleTypes.simple();
   public static final DefaultParticleType COLOSSAL_BODY_STEAM_PARTICLE = FabricParticleTypes.simple();
   public static final ShifterMusclesArmorItem SHIFTER_MUSCLES = new ShifterMusclesArmorItem();
   public static final PureTitanSpawnEggItem PURE_TITAN_SPAWN_EGG = new PureTitanSpawnEggItem(new Settings());
   public static final SpawnEggItem COLOSSAL_TITAN_SPAWN_EGG = new SpawnEggItem(COLOSSAL_TITAN, 13937046, 12883338, new Settings());
   public static final SpawnEggItem ATTACK_TITAN_SPAWN_EGG = new SpawnEggItem(ATTACK_TITAN, 9127187, 3100495, new Settings());
   public static final SpawnEggItem ARMORED_TITAN_SPAWN_EGG = new SpawnEggItem(ARMORED_TITAN, 12092939, 6908265, new Settings());
   public static final SpawnEggItem FEMALE_TITAN_SPAWN_EGG = new SpawnEggItem(FEMALE_TITAN, 16738740, 16758465, new Settings());
   public static final SpawnEggItem WARHAMMER_TITAN_SPAWN_EGG = new SpawnEggItem(WARHAMMER_TITAN, 4915330, 9109643, new Settings());
   public static final ComponentType<SpinalFluidData> SPINAL_FLUID_DATA = ComponentType.<SpinalFluidData>builder()
      .codec(SpinalFluidData.CODEC)
      .packetCodec(SpinalFluidData.STREAM_CODEC)
      .build();
   public static final ComponentType<LacedFoodData> LACED_FOOD_DATA = ComponentType.<LacedFoodData>builder()
      .codec(LacedFoodData.CODEC)
      .packetCodec(LacedFoodData.STREAM_CODEC)
      .build();
   public static final ComponentType<String> CLOAK_SKIN = ComponentType.<String>builder().codec(Codec.STRING).packetCodec(PacketCodecs.STRING).build();
   public static final Set<UUID> TDXM_AUTHORIZED_UUIDS = Set.of(
      UUID.fromString("2b80f8d6-33d8-4409-bcdf-77fd4d273b59"),
      UUID.fromString("505b3a59-f55f-4648-9dd7-4d525ab46fe4"),
      UUID.fromString("94932331-4fa8-4884-9c40-a6cd9c4f7554"),
      UUID.fromString("aa9aa826-e776-4cb1-b05c-907c6989ee0a"),
      UUID.fromString("dbdb0111-da1c-4765-84a7-3cbbafa41df6"),
      UUID.fromString("d2793820-da3c-49e6-a87a-98ba0c638809"),
      UUID.fromString("3087350b-020c-46ce-85eb-f41112483f79")
   );
   public static final ComponentType<String> BLADE_SKIN = ComponentType.<String>builder().codec(Codec.STRING).packetCodec(PacketCodecs.STRING).build();
   public static final ComponentType<String> UNIFORM_SKIN = ComponentType.<String>builder().codec(Codec.STRING).packetCodec(PacketCodecs.STRING).build();
   public static final ComponentType<String> ODM_BOOTS_SKIN = ComponentType.<String>builder().codec(Codec.STRING).packetCodec(PacketCodecs.STRING).build();
   public static final ComponentType<String> ODM_GEAR_SKIN = ComponentType.<String>builder().codec(Codec.STRING).packetCodec(PacketCodecs.STRING).build();
   public static final ComponentType<String> TRENCH_COAT_SKIN = ComponentType.<String>builder().codec(Codec.STRING).packetCodec(PacketCodecs.STRING).build();
   public static final Set<UUID> TITAN_AUTHORIZED_UUIDS = Set.of(
      UUID.fromString("2b80f8d6-33d8-4409-bcdf-77fd4d273b59"),
      UUID.fromString("e478779b-e3c4-4a28-9b93-1d07072df930"),
      UUID.fromString("6949ce40-3471-468e-a1c7-a879cce4a596")
   );
   public static final Set<UUID> BERSERK_AUTHORIZED_UUIDS = Set.of(
      UUID.fromString("2b80f8d6-33d8-4409-bcdf-77fd4d273b59"),
      UUID.fromString("e478779b-e3c4-4a28-9b93-1d07072df930"),
      UUID.fromString("505b3a59-f55f-4648-9dd7-4d525ab46fe4"),
      UUID.fromString("aa9aa826-e776-4cb1-b05c-907c6989ee0a"),
      UUID.fromString("c294a4b8-af63-4221-bff9-5177fc81e010"),
      UUID.fromString("c1b2ec75-a4d5-4ad1-8b9a-8f65ccd70749"),
      UUID.fromString("d235afc7-d359-412e-9b82-50aa4ba810c3"),
      UUID.fromString("f26d7fb7-d212-429a-bf14-a902c6112fdc"),
      UUID.fromString("270bb12d-7c4c-441d-8f0d-0ecc47af2df0"),
      UUID.fromString("aef6e584-21e0-46fd-a467-8538533a9989"),
      UUID.fromString("551ae4c3-b638-4229-b56b-ba44893adff9"),
      UUID.fromString("cff1f12b-85c4-4273-b7ef-cc297bd2615d"),
      UUID.fromString("199590c0-738d-41a0-900b-2216cd4b6c59"),
      UUID.fromString("52229518-28dd-475a-9c5d-196c82b6870d"),
      UUID.fromString("6d502e52-9f7d-4cfe-a44f-6df038b8d915"),
      UUID.fromString("41cce3eb-c06e-4897-bd32-3993ee0cb855"),
      UUID.fromString("210749e9-c51b-47c2-8ec6-333bd1f81989"),
      UUID.fromString("4d9e210f-ff4c-46d3-9d8b-a71aedfca85f"),
      UUID.fromString("6d0be3c8-7d9e-4099-953e-b1f981ee1013"),
      UUID.fromString("5bbe1ba5-6044-4ffb-9372-85c92b745ca6"),
      UUID.fromString("074592f1-9a83-450c-9c06-bd47bc392f10"),
      UUID.fromString("658f3922-881f-436c-9010-5c679b1324b2"),
      UUID.fromString("6949ce40-3471-468e-a1c7-a879cce4a596")
   );
   public static final Set<UUID> KIRITO_AUTHORIZED_UUIDS = BERSERK_AUTHORIZED_UUIDS;
   public static final Set<UUID> NEEDLE_AUTHORIZED_UUIDS = Set.of(
      UUID.fromString("2b80f8d6-33d8-4409-bcdf-77fd4d273b59"),
      UUID.fromString("e478779b-e3c4-4a28-9b93-1d07072df930"),
      UUID.fromString("505b3a59-f55f-4648-9dd7-4d525ab46fe4"),
      UUID.fromString("2ed3d779-6f9f-4e02-8adc-bf7631b2acff"),
      UUID.fromString("b29baaea-c065-42b0-8903-5c8b77f72039"),
      UUID.fromString("6d502e52-9f7d-4cfe-a44f-6df038b8d915")
   );
   public static final Set<UUID> AARON_AUTHORIZED_UUIDS = Set.of(
      UUID.fromString("08cd0f59-47eb-4c10-831f-88be6d54f034"), UUID.fromString("2b80f8d6-33d8-4409-bcdf-77fd4d273b59")
   );
   public static final Set<UUID> HYPER_AUTHORIZED_UUIDS = Set.of(
      UUID.fromString("2b80f8d6-33d8-4409-bcdf-77fd4d273b59"),
      UUID.fromString("e478779b-e3c4-4a28-9b93-1d07072df930"),
      UUID.fromString("6d502e52-9f7d-4cfe-a44f-6df038b8d915")
   );
   public static final Set<UUID> ROYALTY_AUTHORIZED_UUIDS = Set.of(
      UUID.fromString("2b80f8d6-33d8-4409-bcdf-77fd4d273b59"),
      UUID.fromString("e478779b-e3c4-4a28-9b93-1d07072df930"),
      UUID.fromString("505b3a59-f55f-4648-9dd7-4d525ab46fe4"),
      UUID.fromString("551ae4c3-b638-4229-b56b-ba44893adff9")
   );
   public static final SyringeItem SYRINGE = new SyringeItem(new Settings().maxCount(1));
   public static final EmptySyringeItem EMPTY_SYRINGE = new EmptySyringeItem(new Settings().maxCount(1));
   public static final Item ICE_BURST_CLUSTER = new Item(new Settings());
   public static final GasCanisterItem GAS_CANISTER = new GasCanisterItem(new Settings().maxCount(1));
   public static final ArmorPotionItem ARMOR_POTION = new ArmorPotionItem(new Settings().maxCount(1));
   public static final ThunderSpearItem THUNDER_SPEAR = new ThunderSpearItem(new Settings().maxCount(1));
   public static final BloodlineRerollItem BLOODLINE_REROLL = new BloodlineRerollItem(new Settings().maxCount(1));
   public static final Item ULTRAHARD_STEEL_INGOT = new Item(new Settings());
   public static final FlareCartridgeItem RED_FLARE_CARTRIDGE = new FlareCartridgeItem(new Settings().maxCount(3), FlareCartridgeItem.FlareColor.RED);
   public static final FlareCartridgeItem BLACK_FLARE_CARTRIDGE = new FlareCartridgeItem(new Settings().maxCount(3), FlareCartridgeItem.FlareColor.BLACK);
   public static final FlareCartridgeItem PURPLE_FLARE_CARTRIDGE = new FlareCartridgeItem(new Settings().maxCount(3), FlareCartridgeItem.FlareColor.PURPLE);
   public static final FlareCartridgeItem BLUE_FLARE_CARTRIDGE = new FlareCartridgeItem(new Settings().maxCount(3), FlareCartridgeItem.FlareColor.BLUE);
   public static final FlareCartridgeItem GREEN_FLARE_CARTRIDGE = new FlareCartridgeItem(new Settings().maxCount(3), FlareCartridgeItem.FlareColor.GREEN);
   public static final FlareCartridgeItem YELLOW_FLARE_CARTRIDGE = new FlareCartridgeItem(new Settings().maxCount(3), FlareCartridgeItem.FlareColor.YELLOW);
   public static final FlareGunItem FLARE_GUN = new FlareGunItem(new Settings().maxCount(1));
   public static final HandcuffsItem HANDCUFFS = new HandcuffsItem(new Settings().maxCount(1));
   public static final HandcuffsKeyItem HANDCUFFS_KEY = new HandcuffsKeyItem(new Settings().maxCount(1));
   public static final BasementKeyItem BASEMENT_KEY = new BasementKeyItem(ArmorMaterials.LEATHER, Type.HELMET, new Settings().maxCount(1).rarity(Rarity.EPIC));
   public static final ArmorMaterial KENNY_HAT_MATERIAL = new daot.compat.BackportArmorMaterial(new Identifier("dannys-aot", "kennyhat"),
         Map.of(Type.HELMET, 2, Type.CHESTPLATE, 0, Type.LEGGINGS, 0, Type.BOOTS, 0),
         15,
         SoundEvents.ITEM_ARMOR_EQUIP_LEATHER,
         () -> Ingredient.EMPTY,
         0.0F,
         0.0F
      );
   public static final KennyHatItem KENNY_HAT = new KennyHatItem(KENNY_HAT_MATERIAL, Type.HELMET, new Settings().maxCount(1).rarity(Rarity.EPIC));
   public static final Item FLINSTOCK = new Item(new Settings().maxCount(1)) {
      @Override
      public void appendTooltip(ItemStack stack, World context, List<Text> tooltip, TooltipContext type) {
         tooltip.add(Text.literal("WIP").formatted(Formatting.GRAY));
         super.appendTooltip(stack, context, tooltip, type);
      }
   };
   public static final Item MUSKET = new Item(new Settings().maxCount(1)) {
      @Override
      public void appendTooltip(ItemStack stack, World context, List<Text> tooltip, TooltipContext type) {
         tooltip.add(Text.literal("WIP").formatted(Formatting.GRAY));
         super.appendTooltip(stack, context, tooltip, type);
      }
   };
   public static final Item ULTRAHARD_LEATHER = new Item(new Settings());
   public static BooleanSupplier canReadCannedHerring = () -> true;
   public static final Item VINTAGE_WINE = new Item(new Settings().maxCount(16)) {
      @Override
      public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
         if (!world.isClient) {
            user.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 400, 0));
            if (user instanceof ServerPlayerEntity serverPlayer) {
               ShifterMarkTracker.clearMarksAfterDelay(serverPlayer, 20);
            }
         }

         if (user instanceof PlayerEntity player) {
            player.incrementStat(Stats.USED.getOrCreateStat(this));
            if (!player.getAbilities().creativeMode) {
               stack.decrement(1);
            }
         }

         return stack;
      }

      @Override
      public int getMaxUseTime(ItemStack stack) {
         return 32;
      }

      @Override
      public UseAction getUseAction(ItemStack stack) {
         return UseAction.DRINK;
      }

      @Override
      public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
         return ItemUsage.consumeHeldItem(world, user, hand);
      }

      @Override
      public void appendTooltip(ItemStack stack, World context, List<Text> tooltip, TooltipContext type) {
         tooltip.add(Text.literal("Instantly removes shifter marks").formatted(Formatting.DARK_GRAY));
      }
   };
   public static final Item CANNED_HERRING = new Item(new Settings().maxCount(32)) {
      @Override
      public Text getName(ItemStack stack) {
         return DannysAot.cannedHerringLabel(super.getName(stack).getString());
      }

      @Override
      public void appendTooltip(ItemStack stack, World context, List<Text> tooltip, TooltipContext type) {
         tooltip.add(DannysAot.cannedHerringTooltip("Open with shears"));
         super.appendTooltip(stack, context, tooltip, type);
      }
   };
   public static final Item CANNED_HERRING_OPEN = new Item(
      new Settings().maxCount(32).food(new net.minecraft.item.FoodComponent.Builder().hunger(8).saturationModifier(0.8F).build())
   ) {
      @Override
      public Text getName(ItemStack stack) {
         return DannysAot.cannedHerringLabel(super.getName(stack).getString());
      }

      @Override
      public void appendTooltip(ItemStack stack, World context, List<Text> tooltip, TooltipContext type) {
         tooltip.add(DannysAot.cannedHerringTooltip("Smells like Aaron Frost"));
         super.appendTooltip(stack, context, tooltip, type);
      }
   };
   public static final SpecialRecipeSerializer<CannedHerringOpeningRecipe> CANNED_HERRING_OPENING_SERIALIZER = new SpecialRecipeSerializer<>(
      CannedHerringOpeningRecipe::new
   );
   public static final Item ODM_WIRES = new Item(new Settings());
   public static final Item ODM_SPRING = new Item(new Settings());
   public static final Item ODM_CYLINDER = new Item(new Settings());
   public static final TrenchCoatItem TRENCH_COAT = new TrenchCoatItem(
      ArmorMaterials.LEATHER,
      Type.CHESTPLATE,
      new Settings().maxCount(1),
      "geo/trench_coat.geo.json",
      "geo/trench_coat.geo.json",
      "textures/armor/trench_coat.png"
   );
   public static final TrenchCoatItem TRENCH_COAT_GARRISON = new TrenchCoatItem(
      ArmorMaterials.LEATHER,
      Type.CHESTPLATE,
      new Settings().maxCount(1),
      "geo/trench_coat_garrison.geo.json",
      "geo/trench_coat.geo.json",
      "textures/armor/trench_coat_garrison.png"
   );
   public static final TrenchCoatItem TRENCH_COAT_SCOUT = new TrenchCoatItem(
      ArmorMaterials.LEATHER,
      Type.CHESTPLATE,
      new Settings().maxCount(1),
      "geo/trench_coat_scout.geo.json",
      "geo/trench_coat.geo.json",
      "textures/armor/trench_coat_scout.png"
   );
   public static final TrenchCoatItem TRENCH_COAT_MILITARY_POLICE = new TrenchCoatItem(
      ArmorMaterials.LEATHER,
      Type.CHESTPLATE,
      new Settings().maxCount(1),
      "geo/trench_coat_military_police.geo.json",
      "geo/trench_coat.geo.json",
      "textures/armor/trench_coat_military_police.png"
   );
   public static final TrenchCoatItem COMMANDER_TRENCH_COAT = new TrenchCoatItem(
      ArmorMaterials.LEATHER,
      Type.CHESTPLATE,
      new Settings().maxCount(1),
      "geo/commander_trench_coat.geo.json",
      "geo/trench_coat_item.geo.json",
      "textures/armor/commander_trench_coat.png"
   );
   public static final TrenchCoatItem COMMANDER_TRENCH_COAT_GARRISON = new TrenchCoatItem(
      ArmorMaterials.LEATHER,
      Type.CHESTPLATE,
      new Settings().maxCount(1),
      "geo/commander_trench_coat_garrison.geo.json",
      "geo/trench_coat_item.geo.json",
      "textures/armor/commander_trench_coat_garrison.png"
   );
   public static final TrenchCoatItem COMMANDER_TRENCH_COAT_SCOUT = new TrenchCoatItem(
      ArmorMaterials.LEATHER,
      Type.CHESTPLATE,
      new Settings().maxCount(1),
      "geo/commander_trench_coat_scout.geo.json",
      "geo/trench_coat_item.geo.json",
      "textures/armor/commander_trench_coat_scout.png"
   );
   public static final TrenchCoatItem COMMANDER_TRENCH_COAT_MILITARY_POLICE = new TrenchCoatItem(
      ArmorMaterials.LEATHER,
      Type.CHESTPLATE,
      new Settings().maxCount(1),
      "geo/commander_trench_coat_military_police.geo.json",
      "geo/trench_coat_item.geo.json",
      "textures/armor/commander_trench_coat_military_police.png"
   );
   public static final TrenchCoatItem TRENCH_COAT_ROYAL = new TrenchCoatItem(
      ArmorMaterials.LEATHER,
      Type.CHESTPLATE,
      new Settings().maxCount(1),
      "geo/trench_coat_royal.geo.json",
      "geo/trench_coat.geo.json",
      "textures/armor/trench_coat_royal.png"
   );
   public static final TrenchCoatItem MARLEY_TRENCH_COAT = new TrenchCoatItem(
      ArmorMaterials.LEATHER,
      Type.CHESTPLATE,
      new Settings().maxCount(1),
      "geo/marley_trench_coat_noband.geo.json",
      "geo/marley_trench_coat_noband.geo.json",
      "textures/armor/marley_trench_coat.png"
   );
   public static final TrenchCoatItem MARLEY_CADET_TRENCH_COAT = new TrenchCoatItem(
      ArmorMaterials.LEATHER,
      Type.CHESTPLATE,
      new Settings().maxCount(1),
      "geo/marley_trench_coat.geo.json",
      "geo/marley_trench_coat.geo.json",
      "textures/armor/marley_trench_coat_warrior_candidate.png"
   );
   public static final TrenchCoatItem MARLEY_CADET_TRENCH_COAT_WARRIOR = new TrenchCoatItem(
      ArmorMaterials.LEATHER,
      Type.CHESTPLATE,
      new Settings().maxCount(1),
      "geo/marley_trench_coat_warrior.geo.json",
      "geo/marley_trench_coat.geo.json",
      "textures/armor/marley_trench_coat_warrior.png"
   );
   public static final MarleyArmbandItem MARLEY_ARMBAND_ELDIAN = new MarleyArmbandItem(
      ArmorMaterials.LEATHER, new Settings().maxCount(1), "geo/marley_armband_eldian.geo.json", "textures/armor/marley_armband_eldian.png"
   );
   public static final MarleyArmbandItem MARLEY_ARMBAND_SOLDIER = new MarleyArmbandItem(
      ArmorMaterials.LEATHER, new Settings().maxCount(1), "geo/marley_armband_soldier.geo.json", "textures/armor/marley_armband_soldier.png"
   );
   public static final MarleyArmbandItem MARLEY_ARMBAND_CADET = new MarleyArmbandItem(
      ArmorMaterials.LEATHER, new Settings().maxCount(1), "geo/marley_armband_cadet.geo.json", "textures/armor/marley_armband_cadet.png"
   );
   public static final MarleyArmbandItem MARLEY_ARMBAND_WARRIOR = new MarleyArmbandItem(
      ArmorMaterials.LEATHER, new Settings().maxCount(1), "geo/marley_armband_warrior.geo.json", "textures/armor/marley_armband_warrior.png"
   );
   public static final Block REGIMENT_BANNER = new RegimentBannerBlock();
   public static final Block WALL_REGIMENT_BANNER = new WallRegimentBannerBlock();
   public static BlockEntityType<RegimentBannerBlockEntity> REGIMENT_BANNER_BLOCK_ENTITY;
   public static final Block ARMOR_POTION_BLOCK = new ArmorPotionBlock();
   public static BlockEntityType<ArmorPotionBlockEntity> ARMOR_POTION_BLOCK_ENTITY;
   public static final Block STRWS_BLOCK = new StrwsBlock();
   public static final Block STRWS_PART_BLOCK = new StrwsPartBlock();
   public static BlockEntityType<StrwsBlockEntity> STRWS_BLOCK_ENTITY;
   public static final StrwsItem STRWS_ITEM = new StrwsItem(new Settings().maxCount(16));
   public static ScreenHandlerType<StrwsMenu> STRWS_MENU;
   public static final RegimentBannerItem GARRISON_BANNER = new RegimentBannerItem(REGIMENT_BANNER, WALL_REGIMENT_BANNER, RegimentType.GARRISON);
   public static final RegimentBannerItem MILITARY_POLICE_BANNER = new RegimentBannerItem(REGIMENT_BANNER, WALL_REGIMENT_BANNER, RegimentType.MILITARY_POLICE);
   public static final RegimentBannerItem SCOUT_BANNER = new RegimentBannerItem(REGIMENT_BANNER, WALL_REGIMENT_BANNER, RegimentType.SCOUT);
   public static final RegimentBannerItem TRAINING_CORPS_BANNER = new RegimentBannerItem(REGIMENT_BANNER, WALL_REGIMENT_BANNER, RegimentType.TRAINING);
   public static final RegimentBannerItem GOLD_CLOAK_BANNER = new RegimentBannerItem(REGIMENT_BANNER, WALL_REGIMENT_BANNER, RegimentType.GOLD_CLOAK);
   public static final ItemGroup AOT_TAB = FabricItemGroup.builder()
      .displayName(Text.translatable("itemGroup.dannys-aot.aot_items"))
      .icon(() -> new ItemStack(SYRINGE))
      .entries((parameters, output) -> {
         output.add(ODM_GEAR);
         output.add(BLADE);
         output.add(BLADE_COMPONENT);
         output.add(ODM_APG);
         output.add(APG_GUN);
         output.add(APG_CARTRIDGE);
         output.add(THUNDER_SPEAR);
         output.add(ZEKES_GLASSES);
         output.add(PURE_TITAN_SPAWN_EGG);
         output.add(SYRINGE);
         output.add(EMPTY_SYRINGE);
         output.add(IRON_BAMBOO_ITEM);
         output.add(HARDENED_IRON_BAMBOO);
         output.add(ULTRAHARD_STEEL_INGOT);
         output.add(ULTRAHARD_LEATHER);
         output.add(ODM_WIRES);
         output.add(ODM_SPRING);
         output.add(ODM_CYLINDER);
         output.add(ICE_BURST_CLUSTER);
         output.add(GAS_CANISTER);
         output.add(ARMOR_POTION);
         output.add(STRWS_ITEM);
         output.add(HANDCUFFS);
         output.add(HANDCUFFS_KEY);
         output.add(BASEMENT_KEY);
         output.add(FLINSTOCK);
         output.add(MUSKET);
         output.add(FLARE_GUN);
         output.add(RED_FLARE_CARTRIDGE);
         output.add(BLACK_FLARE_CARTRIDGE);
         output.add(PURPLE_FLARE_CARTRIDGE);
         output.add(BLUE_FLARE_CARTRIDGE);
         output.add(GREEN_FLARE_CARTRIDGE);
         output.add(YELLOW_FLARE_CARTRIDGE);
         output.add(CANNED_HERRING);
         output.add(CANNED_HERRING_OPEN);
         output.add(VINTAGE_WINE);
         output.add(BLOODLINE_REROLL);
      })
      .build();
   public static final ItemGroup AOT_CLOTHING_TAB = FabricItemGroup.builder()
      .displayName(Text.translatable("itemGroup.dannys-aot.aot_clothing"))
      .icon(() -> new ItemStack(TRENCH_COAT))
      .entries((parameters, output) -> {
         output.add(MARLEY_ARMBAND_ELDIAN);
         output.add(MARLEY_ARMBAND_SOLDIER);
         output.add(MARLEY_ARMBAND_CADET);
         output.add(MARLEY_ARMBAND_WARRIOR);
         output.add(MARLEY_TRENCH_COAT);
         output.add(MARLEY_CADET_TRENCH_COAT);
         output.add(MARLEY_CADET_TRENCH_COAT_WARRIOR);
         output.add(TRENCH_COAT);
         output.add(TRENCH_COAT_GARRISON);
         output.add(TRENCH_COAT_SCOUT);
         output.add(TRENCH_COAT_MILITARY_POLICE);
         output.add(COMMANDER_TRENCH_COAT);
         output.add(COMMANDER_TRENCH_COAT_GARRISON);
         output.add(COMMANDER_TRENCH_COAT_SCOUT);
         output.add(COMMANDER_TRENCH_COAT_MILITARY_POLICE);
         output.add(TRENCH_COAT_ROYAL);
         output.add(BLACK_CLOAK);
         output.add(GREEN_CLOAK);
         output.add(GREEN_SCOUT_CLOAK);
         output.add(GREEN_GARRISON_CLOAK);
         output.add(GREEN_MILITARY_POLICE_CLOAK);
         output.add(ROYAL_CLOAK);
         output.add(UNIFORM);
         output.add(GARRISON_UNIFORM);
         output.add(SCOUT_UNIFORM);
         output.add(MILITARY_POLICE_UNIFORM);
         output.add(CULTIST_ROBE);
         output.add(RED_SCARF);
         output.add(KENNY_HAT);
         output.add(MARLEY_UNIFORM_HELMET);
         output.add(MARLEY_UNIFORM_CHESTPLATE);
         output.add(MARLEY_UNIFORM_LEGGINGS);
         output.add(MARLEY_UNIFORM_BOOTS);
         output.add(MARLEY_ELDIAN_UNIFORM_HELMET);
         output.add(MARLEY_ELDIAN_UNIFORM_CHESTPLATE);
         output.add(ODM_BOOTS);
      })
      .build();
   public static final ItemGroup AOT_BLOCKS_TAB = FabricItemGroup.builder()
      .displayName(Text.translatable("itemGroup.dannys-aot.aot_blocks"))
      .icon(() -> new ItemStack(HARDENED_BLOCK_ITEM))
      .entries((parameters, output) -> {
         output.add(HARDENED_BLOCK_ITEM);
         output.add(HARDENED_SLAB_ITEM);
         output.add(HARDENED_STAIRS_ITEM);
         output.add(ULTRA_HARDENED_BLOCK_ITEM);
         output.add(ULTRA_HARDENED_SLAB_ITEM);
         output.add(ULTRA_HARDENED_STAIRS_ITEM);
         output.add(BLOCK_OF_ICEBURST_ITEM);
         output.add(ICE_BURST_STONE_ITEM);
         output.add(BURSTING_ICE_BURST_STONE_ITEM);
         output.add(ICE_BURST_SHARD_CLUSTER_ITEM);
         output.add(ICEBURST_FURNACE_ITEM);
         output.add(BAMBOO_LEAVES_ITEM);
         output.add(PATH_SAND_ITEM);
         output.add(DARK_PATH_SAND_ITEM);
         output.add(GARRISON_BANNER);
         output.add(MILITARY_POLICE_BANNER);
         output.add(SCOUT_BANNER);
         output.add(TRAINING_CORPS_BANNER);
         output.add(TITAN_DUMMY_ITEM);
      })
      .build();

   public static boolean isTitanGriefingEnabled(World level) {
      return level.getGameRules().getBoolean(RULE_TITAN_GRIEFING);
   }

   public static boolean isShifterExplosionDamageEnabled(World level) {
      return level.getGameRules().getBoolean(RULE_SHIFTER_EXPLOSION_DAMAGE);
   }

   public static boolean isYmirCurseEnabled(World level) {
      return level.getGameRules().getBoolean(RULE_YMIR_CURSE);
   }

   public static boolean isFairTitanPowerLossEnabled(World level) {
      return level.getGameRules().getBoolean(RULE_FAIR_TITAN_POWER_LOSS);
   }

   public static boolean doVillagersSpawnWithPowers(World level) {
      return level.getGameRules().getBoolean(RULE_VILLAGERS_SPAWN_WITH_POWERS);
   }

   public static boolean canInjectOtherPlayers(World level) {
      return level.getGameRules().getBoolean(RULE_INJECT_OTHER_PLAYERS);
   }

   public static boolean isODMAllowed(World level) {
      return level.getGameRules().getBoolean(RULE_ALLOW_ODM);
   }

   public static boolean isShiftingAllowed(World level) {
      return level.getGameRules().getBoolean(RULE_ALLOW_SHIFTING);
   }

   public static boolean isSelfInjectAllowed(World level) {
      return level.getGameRules().getBoolean(RULE_ALLOW_SELF_INJECT);
   }

   public static boolean areThunderSpearsAllowed(World level) {
      return level.getGameRules().getBoolean(RULE_ALLOW_THUNDER_SPEARS);
   }

   public static boolean isThunderSpearGriefingEnabled(World level) {
      return level.getGameRules().getBoolean(RULE_THUNDER_SPEAR_GRIEFING);
   }

   public static boolean isUnfairPureTitans(World level) {
      return level.getGameRules().getBoolean(RULE_UNFAIR_PURE_TITANS);
   }

   public static boolean isCuffingAllowed(World level) {
      return level.getGameRules().getBoolean(RULE_ALLOW_CUFFING);
   }

   public static boolean isShifterSnitchingEnabled(World level) {
      return level.getGameRules().getBoolean(RULE_SHIFTER_SNITCHING);
   }

   public static boolean areRerollsAllowed(World level) {
      return level.getGameRules().getBoolean(RULE_ALLOW_REROLLS);
   }

   public static boolean areOgreSpawnsAllowed(World level) {
      return level.getGameRules().getBoolean(RULE_ALLOW_OGRE_SPAWNS);
   }

   public static boolean isChargedODMAttacksEnabled(World level) {
      return level.getGameRules().getBoolean(RULE_CHARGED_ODM_ATTACKS);
   }

   public static boolean isODMGear(Item item) {
      return item == ODM_GEAR || item == ODM_APG;
   }

   public static boolean isAPG(Item item) {
      return item == ODM_APG;
   }

   public static boolean isValidGripForLeggings(ItemStack leggings, Item handItem) {
      return leggings.getItem() == ODM_APG ? handItem == APG_GUN : handItem == BLADE;
   }

   public static int getMaxGasForGear(ItemStack stack) {
      return stack.getItem() == ODM_APG ? 1000 : 500;
   }

   public static int getGasFromGear(ItemStack stack) {
      return stack.getItem() == ODM_APG ? ODMAPGItem.getGas(stack) : ODMGearItem.getGas(stack);
   }

   public static void setGasOnGear(ItemStack stack, int amount) {
      if (stack.getItem() == ODM_APG) {
         ODMAPGItem.setGas(stack, amount);
      } else {
         ODMGearItem.setGas(stack, amount);
      }
   }

   public static boolean consumeGasFromGear(ItemStack stack, int amount, PlayerEntity player) {
      return stack.getItem() == ODM_APG ? ODMAPGItem.consumeGas(stack, amount, player) : ODMGearItem.consumeGas(stack, amount, player);
   }

   public static boolean gearHasGas(ItemStack stack, PlayerEntity player) {
      return stack.getItem() == ODM_APG ? ODMAPGItem.hasGas(stack, player) : ODMGearItem.hasGas(stack, player);
   }

   private static Text cannedHerringLabel(String raw) {
      MutableText base = Text.literal(raw);
      return canReadCannedHerring.getAsBoolean() ? base : base.formatted(Formatting.OBFUSCATED);
   }

   private static Text cannedHerringTooltip(String raw) {
      MutableText base = Text.literal(raw).formatted(Formatting.DARK_GRAY);
      return canReadCannedHerring.getAsBoolean() ? base : base.formatted(Formatting.OBFUSCATED);
   }

   public void onInitialize() {
      daot.compat.BackportEffects.initialize();
      daot.compat.attributes.DaotEntityAttributes.initialize();
      IntegrityGuard.checkAndRefuse();
      LOGGER.info("Hello from Danny's AoT mod!");
      RULE_TITAN_GRIEFING = GameRuleRegistry.register("titanGriefing", Category.MOBS, GameRuleFactory.createBooleanRule(true));
      RULE_FAIR_TITAN_POWER_LOSS = GameRuleRegistry.register("fairTitanPowerLoss", Category.MOBS, GameRuleFactory.createBooleanRule(true));
      RULE_VILLAGERS_SPAWN_WITH_POWERS = GameRuleRegistry.register("villagersSpawnWithPowers", Category.MOBS, GameRuleFactory.createBooleanRule(true));
      RULE_INJECT_OTHER_PLAYERS = GameRuleRegistry.register("injectOtherPlayers", Category.PLAYER, GameRuleFactory.createBooleanRule(true));
      RULE_ALLOW_ODM = GameRuleRegistry.register(
         "allowODM", Category.PLAYER, GameRuleFactory.createBooleanRule(true, (server, rule) -> ModNetworking.broadcastAllowODM(server))
      );
      RULE_ALLOW_SHIFTING = GameRuleRegistry.register(
         "allowShifting", Category.PLAYER, GameRuleFactory.createBooleanRule(true, (server, rule) -> ModNetworking.broadcastAllowShifting(server))
      );
      RULE_ALLOW_SELF_INJECT = GameRuleRegistry.register("allowSelfInject", Category.PLAYER, GameRuleFactory.createBooleanRule(true));
      RULE_ALLOW_THUNDER_SPEARS = GameRuleRegistry.register(
         "allowThunderSpears", Category.PLAYER, GameRuleFactory.createBooleanRule(true, (server, rule) -> ModNetworking.broadcastAllowThunderSpears(server))
      );
      RULE_THUNDER_SPEAR_GRIEFING = GameRuleRegistry.register("thunderSpearGriefing", Category.MOBS, GameRuleFactory.createBooleanRule(true));
      RULE_UNFAIR_PURE_TITANS = GameRuleRegistry.register("unfairPureTitans", Category.MOBS, GameRuleFactory.createBooleanRule(false));
      RULE_ALLOW_CUFFING = GameRuleRegistry.register("allowCuffing", Category.PLAYER, GameRuleFactory.createBooleanRule(true));
      RULE_SHIFTER_SNITCHING = GameRuleRegistry.register("shifterSnitching", Category.PLAYER, GameRuleFactory.createBooleanRule(true));
      RULE_SHIFT_BOSS_BARS = GameRuleRegistry.register("shiftBossbars", Category.PLAYER, GameRuleFactory.createBooleanRule(true));
      RULE_SHIFTER_EXPLOSION_DAMAGE = GameRuleRegistry.register("ShifterExplosionDamage", Category.MOBS, GameRuleFactory.createBooleanRule(true));
      RULE_YMIR_CURSE = GameRuleRegistry.register("YmirCurse", Category.PLAYER, GameRuleFactory.createBooleanRule(false));
      RULE_TITAN_GORE = GameRuleRegistry.register("titanGore", Category.MOBS, GameRuleFactory.createBooleanRule(true));
      RULE_MULTIPLE_SHIFTERS = GameRuleRegistry.register("multipleShifters", Category.PLAYER, GameRuleFactory.createBooleanRule(false));
      RULE_CONFIRMED_TITAN_SHIFTER_KILL = GameRuleRegistry.register("confirmedTitanShifterKill", Category.PLAYER, GameRuleFactory.createBooleanRule(false));
      RULE_WEATHER_AFFECTS_ODM = GameRuleRegistry.register(
         "weatherAffectsODM", Category.PLAYER, GameRuleFactory.createBooleanRule(false, (server, rule) -> ModNetworking.broadcastWeatherAffectsODM(server))
      );
      RULE_KINETIC_ODM_DAMAGE = GameRuleRegistry.register(
         "kineticODMdamage", Category.PLAYER, GameRuleFactory.createBooleanRule(false, (server, rule) -> ModNetworking.broadcastKineticOdmDamage(server))
      );
      RULE_FORCE_SHIFTING = GameRuleRegistry.register("forceShifting", Category.PLAYER, GameRuleFactory.createBooleanRule(false));
      RULE_REALISTIC_RESOURCE_USE = GameRuleRegistry.register(
         "realisticResourceUse",
         Category.PLAYER,
         GameRuleFactory.createBooleanRule(false, (server, rule) -> ModNetworking.broadcastRealisticResourceUse(server))
      );
      RULE_ALLOW_REROLLS = GameRuleRegistry.register("allowRerolls", Category.PLAYER, GameRuleFactory.createBooleanRule(true));
      RULE_ALLOW_OGRE_SPAWNS = GameRuleRegistry.register("allowOgreSpawns", Category.MOBS, GameRuleFactory.createBooleanRule(true));
      RULE_GRASS_ODM = GameRuleRegistry.register(
         "grassODM", Category.PLAYER, GameRuleFactory.createBooleanRule(true, (server, rule) -> ModNetworking.broadcastGrassODM(server))
      );
      RULE_CHARGED_ODM_ATTACKS = GameRuleRegistry.register(
         "chargedODMAttacks", Category.PLAYER, GameRuleFactory.createBooleanRule(false, (server, rule) -> ModNetworking.broadcastChargedODMAttacks(server))
      );
      CURSE_OF_YMIR_EFFECT = Registry.register(
         Registries.STATUS_EFFECT, new Identifier("dannys-aot", "curse_of_ymir"), new StatusEffect(StatusEffectCategory.HARMFUL, 4868682) {}
      );
      YmirCurseHandler.register();
      ModConfig.load();
      ServerLifecycleEvents.SERVER_STARTED.register((ServerStarted)server -> {
         RegistryKey<World> paradisKey = RegistryKey.of(RegistryKeys.WORLD, new Identifier("dannys-aot", "paradis"));
         ServerWorld paradisLevel = server.getWorld(paradisKey);
         if (paradisLevel != null) {
            NoiseConfig randomState = paradisLevel.getChunkManager().getNoiseConfig();
            long seed = randomState.getOrCreateRandomDeriver(new Identifier("paradis_seed")).split(0, 0, 0).nextLong();
            ParadisChunkGenerator.setWorldSeed(seed);
            LOGGER.info("Initialized Paradis worldSeed on server start");
         }
      });
      ServerLifecycleEvents.SERVER_STOPPING.register((ServerStopping)server -> {
         ParadisChunkGenerator.clearStoredHousePositions();
         LOGGER.info("Cleared Paradis chunk generator caches");
      });
      ServerLifecycleEvents.SERVER_STARTED.register(PowerAuthority::refreshBloodlineSnapshot);
      ServerLifecycleEvents.SERVER_STOPPED.register((ServerStopped)server -> PowerAuthority.reset());
      ServerLifecycleEvents.SERVER_STARTED.register(IntegrityGuard::onServerStarted);
      ModSounds.initialize();
      ModEffects.initialize();
      ModCriteriaTriggers.register();
      VillageAdvancementHandler.register();
      ModNetworking.register();
      HomelanderFlightServerHandler.register();
      HomelanderGrabServerHandler.register();
      SoldierboyNeckGrabServerHandler.register();
      EntityTrackingEvents.START_TRACKING.register((StartTracking)(entity, tracker) -> {
         if (entity instanceof ServerPlayerEntity flyer) {
            ModNetworking.sendHomelanderFlightStateForPlayerTo(flyer, tracker);
         }
      });
      UseEntityCallback.EVENT.register((UseEntityCallback)(player, world, hand, entity, hitResult) -> {
         if (player instanceof ServerPlayerEntity sp) {
            if (BloodlineData.get(sp.getServerWorld()).getBloodline(sp.getUuid()) != BloodlineType.HOMELANDER) {
               return ActionResult.PASS;
            } else {
               return !HomelanderFlightServerHandler.isFlying(sp.getUuid()) ? ActionResult.PASS : ActionResult.FAIL;
            }
         } else {
            return ActionResult.PASS;
         }
      });
      SyringeInteractionHandler.register();
      VillagePopulator.register();
      HoodTracker.registerServerTick();
      ShifterMarkTracker.registerServerTick();
      HomelanderBloodTracker.registerServerTick();
      DismountSmokeHelper.registerServerTick();
      ServerTickEvents.START_SERVER_TICK.register((StartTick)server -> {
         for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (ServerHookTracker.hasDismountFallImmunity(player.getUuid())) {
               int ticks = ServerHookTracker.getDismountImmunityTicks(player.getUuid());
               ServerHookTracker.tickDismountImmunity(player.getUuid());
               player.onLanding();
               if (ticks >= 10 && player.isOnGround()) {
                  ServerHookTracker.consumeDismountFallImmunity(player.getUuid());
               }

               if (ticks >= 200) {
                  ServerHookTracker.consumeDismountFallImmunity(player.getUuid());
               }
            }
         }
      });
      PushAuraTracker.registerEvents();
      ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
         PushAuraTracker.serverTick(server);
         HandcuffsTracker.serverTick(server);
         if (TitanPowerData.isConfigDirty()) {
            TitanPowerData.clearConfigDirty();
            writeShifterConfig(server);
         }
      });
      ServerPlayConnectionEvents.JOIN
         .register(
            (Join)(handler, sender, server) -> {
               ServerPlayerEntity joiningPlayer = handler.getPlayer();
               HandcuffsTracker.onPlayerJoin(joiningPlayer);
               server.execute(() -> {
                  boolean has = joiningPlayer.getCommandTags().contains("has_hardening");
                  ServerPlayNetworking.send(joiningPlayer, new ArmorPotionDrinkPayload(has, false));
               });
               server.execute(() -> GeassManager.onPlayerJoin(joiningPlayer));
               server.execute(() -> VanishManager.onPlayerJoin(joiningPlayer));
               server.execute(() -> PushAuraTracker.onPlayerLogin(joiningPlayer));
               server.execute(
                  () -> {
                     ServerWorld ow = server.getWorld(World.OVERWORLD);
                     if (ow != null) {
                        TitanPowerData pd = TitanPowerData.get(ow);
                        if (!DannyAccess.mayHoldDiscreetShifter(joiningPlayer)) {
                           boolean grandfather = ModConfig.get().grandfatherLegacyShifterTags;

                           for (TitanPowerType power : TitanPowerType.values()) {
                              String tag = power.getTagName();
                              if (joiningPlayer.getCommandTags().contains(tag) && !pd.playerHoldsPower(joiningPlayer.getUuid(), power)) {
                                 if (grandfather) {
                                    pd.addPlayerToPower(joiningPlayer.getUuid(), power, GrantProvenance.migrated());
                                    LOGGER.info(
                                       "Adopted pre-existing '{}' tag for {} as a migrated grant (grandfatherLegacyShifterTags is on)",
                                       tag,
                                       joiningPlayer.getName().getString()
                                    );
                                 } else {
                                    joiningPlayer.removeScoreboardTag(tag);
                                    joiningPlayer.sendMessage(
                                       Text.literal("Your " + power.getDisplayName() + " power was removed (not issued by Danny's AoT)")
                                          .formatted(Formatting.RED),
                                       false
                                    );
                                    LOGGER.warn(
                                       "Stripped unbacked '{}' tag from {} on join — no grant record. Shifter powers must be issued through /daot shifter set. If this server predates grant records, set grandfatherLegacyShifterTags=true in config/dannys-aot.json to adopt existing tags instead.",
                                       tag,
                                       joiningPlayer.getName().getString()
                                    );
                                 }
                              }
                           }
                        }
                     }
                  }
               );
               if (joiningPlayer.getCommandTags().contains("dannysaot_combat_logged")) {
                  joiningPlayer.removeScoreboardTag("dannysaot_combat_logged");
                  server.execute(() -> {
                     ServerWorld level = joiningPlayer.getServerWorld();
                     TitanPowerData data = TitanPowerData.get(level);
                     TitanPowerType lostPower = null;

                     for (TitanPowerType power : TitanPowerType.values()) {
                        if (joiningPlayer.getCommandTags().contains(power.getTagName())) {
                           lostPower = power;
                           break;
                        }
                     }

                     if (lostPower != null) {
                        joiningPlayer.removeScoreboardTag(lostPower.getTagName());
                        data.removePlayerFromPower(joiningPlayer.getUuid(), lostPower);
                        if (!data.playerHasPower(lostPower)) {
                           VillagerEntity villager = new VillagerEntity(EntityType.VILLAGER, level);
                           villager.setPosition(joiningPlayer.getX(), joiningPlayer.getY(), joiningPlayer.getZ());
                           level.spawnEntity(villager);
                           data.setPower(villager.getUuid(), lostPower);
                           PoweredVillagerSyncPayload syncPayload = PoweredVillagerSyncPayload.addPower(villager.getId(), lostPower.ordinal());

                           for (ServerPlayerEntity p : level.getPlayers()) {
                              ServerPlayNetworking.send(p, syncPayload);
                           }
                        }
                     }

                     joiningPlayer.kill();
                     Text deathMessage = Text.literal(joiningPlayer.getDisplayName().getString() + " tried to combat log");

                     for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                        p.sendMessage(deathMessage);
                     }

                     LOGGER.info("Killed {} for combat logging while in defeated titan", joiningPlayer.getName().getString());
                  });
               }
            }
         );
      CommandRegistrationCallback.EVENT.register((CommandRegistrationCallback)(dispatcher, registryAccess, environment) -> {
         ModCommands.register(dispatcher);
         DmnkCommands.register(dispatcher);
      });
      LOGGER.info("Registered /daot commands");
      ServerTickEvents.END_SERVER_TICK.register(GeassManager::tick);
      ServerTickEvents.END_SERVER_TICK.register(VanishManager::tick);
      ServerTickEvents.END_SERVER_TICK.register(DefeatedCarryTracker::tick);
      BladeSkinEnforcer.register();
      ServerTickEvents.END_SERVER_TICK.register(StrwsRestraintTracker::tick);
      ServerLifecycleEvents.SERVER_STOPPED.register((ServerStopped)server -> StrwsRestraintTracker.clearAll());
      ServerPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, server) -> {
         ModCommands.removeFounderChat(handler.getPlayer().getUuid());
         PowerAuthority.onPlayerDisconnect(handler.getPlayer().getUuid());
         OgreHealAbility.onPlayerDisconnect(handler.getPlayer().getUuid());
         GeassManager.handleDisconnect(handler.getPlayer().getUuid(), server);
         VanishManager.onPlayerLeave(handler.getPlayer());
         HomelanderFlightServerHandler.onPlayerLeave(handler.getPlayer().getUuid());
         HomelanderGrabServerHandler.onPlayerLeave(handler.getPlayer().getUuid());
         ShifterMarkTracker.onPlayerLeave(handler.getPlayer().getUuid(), server);
         HomelanderBloodTracker.onPlayerLeave(handler.getPlayer().getUuid(), server);
         PushAuraTracker.onPlayerLogout(handler.getPlayer());
      });
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "odm_gear"), ODM_GEAR);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "uniform"), UNIFORM);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "garrison_uniform"), GARRISON_UNIFORM);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "scout_uniform"), SCOUT_UNIFORM);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "military_police_uniform"), MILITARY_POLICE_UNIFORM);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "cultist_robe"), CULTIST_ROBE);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "red_scarf"), RED_SCARF);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "marley_uniform_helmet"), MARLEY_UNIFORM_HELMET);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "marley_uniform_chestplate"), MARLEY_UNIFORM_CHESTPLATE);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "marley_uniform_leggings"), MARLEY_UNIFORM_LEGGINGS);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "marley_uniform_boots"), MARLEY_UNIFORM_BOOTS);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "marley_eldian_uniform_helmet"), MARLEY_ELDIAN_UNIFORM_HELMET);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "marley_eldian_uniform_chestplate"), MARLEY_ELDIAN_UNIFORM_CHESTPLATE);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "blade"), BLADE);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "odm_apg"), ODM_APG);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "apg_suit"), APG_SUIT);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "odm_boots"), ODM_BOOTS);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "zekes_glasses"), ZEKES_GLASSES);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "black_cloak"), BLACK_CLOAK);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "green_cloak"), GREEN_CLOAK);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "green_scout_cloak"), GREEN_SCOUT_CLOAK);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "green_garrison_cloak"), GREEN_GARRISON_CLOAK);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "green_military_police_cloak"), GREEN_MILITARY_POLICE_CLOAK);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "gold_cloak"), GOLD_CLOAK);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "royal_cloak"), ROYAL_CLOAK);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "blade_component"), BLADE_COMPONENT);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "apg_gun"), APG_GUN);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "apg_cartridge"), APG_CARTRIDGE);
      daot.compat.components.Components.register(new Identifier("dannys-aot", "spinal_fluid_data"), SPINAL_FLUID_DATA);
      daot.compat.components.Components.register(new Identifier("dannys-aot", "laced_food_data"), LACED_FOOD_DATA);
      daot.compat.components.Components.register(new Identifier("dannys-aot", "cloak_skin"), CLOAK_SKIN);
      daot.compat.components.Components.register(new Identifier("dannys-aot", "blade_skin"), BLADE_SKIN);
      daot.compat.components.Components.register(new Identifier("dannys-aot", "uniform_skin"), UNIFORM_SKIN);
      daot.compat.components.Components.register(new Identifier("dannys-aot", "odm_boots_skin"), ODM_BOOTS_SKIN);
      daot.compat.components.Components.register(new Identifier("dannys-aot", "odm_gear_skin"), ODM_GEAR_SKIN);
      daot.compat.components.Components.register(new Identifier("dannys-aot", "trench_coat_skin"), TRENCH_COAT_SKIN);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "syringe"), SYRINGE);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "empty_syringe"), EMPTY_SYRINGE);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "ice_burst_cluster"), ICE_BURST_CLUSTER);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "gas_canister"), GAS_CANISTER);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "armor_potion"), ARMOR_POTION);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "thunder_spear"), THUNDER_SPEAR);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "bloodline_reroll"), BLOODLINE_REROLL);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "ultrahard_steel_ingot"), ULTRAHARD_STEEL_INGOT);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "vintage_wine"), VINTAGE_WINE);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "canned_herring"), CANNED_HERRING);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "canned_herring_open"), CANNED_HERRING_OPEN);
      Registry.register(Registries.RECIPE_SERIALIZER, new Identifier("dannys-aot", "canned_herring_opening"), CANNED_HERRING_OPENING_SERIALIZER);
      Registry.register(Registries.BLOCK, new Identifier("dannys-aot", "path_sand"), PATH_SAND);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "path_sand"), PATH_SAND_ITEM);
      Registry.register(Registries.BLOCK, new Identifier("dannys-aot", "dark_path_sand"), DARK_PATH_SAND);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "dark_path_sand"), DARK_PATH_SAND_ITEM);
      Registry.register(Registries.BLOCK, new Identifier("dannys-aot", "path_sand_slab"), PATH_SAND_SLAB);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "path_sand_slab"), PATH_SAND_SLAB_ITEM);
      Registry.register(Registries.BLOCK, new Identifier("dannys-aot", "dark_path_sand_slab"), DARK_PATH_SAND_SLAB);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "dark_path_sand_slab"), DARK_PATH_SAND_SLAB_ITEM);
      Registry.register(Registries.BLOCK, new Identifier("dannys-aot", "ice_burst_stone"), ICE_BURST_STONE);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "ice_burst_stone"), ICE_BURST_STONE_ITEM);
      Registry.register(Registries.BLOCK, new Identifier("dannys-aot", "bursting_ice_burst_stone"), BURSTING_ICE_BURST_STONE);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "bursting_ice_burst_stone"), BURSTING_ICE_BURST_STONE_ITEM);
      Registry.register(Registries.BLOCK, new Identifier("dannys-aot", "small_ice_burst_shard"), SMALL_ICE_BURST_SHARD);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "small_ice_burst_shard"), SMALL_ICE_BURST_SHARD_ITEM);
      Registry.register(Registries.BLOCK, new Identifier("dannys-aot", "medium_ice_burst_shard"), MEDIUM_ICE_BURST_SHARD);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "medium_ice_burst_shard"), MEDIUM_ICE_BURST_SHARD_ITEM);
      Registry.register(Registries.BLOCK, new Identifier("dannys-aot", "large_ice_burst_shard"), LARGE_ICE_BURST_SHARD);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "large_ice_burst_shard"), LARGE_ICE_BURST_SHARD_ITEM);
      Registry.register(Registries.BLOCK, new Identifier("dannys-aot", "ice_burst_shard_cluster"), ICE_BURST_SHARD_CLUSTER);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "ice_burst_shard_cluster"), ICE_BURST_SHARD_CLUSTER_ITEM);
      Registry.register(Registries.BLOCK, new Identifier("dannys-aot", "hardened_block"), HARDENED_BLOCK);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "hardened_block"), HARDENED_BLOCK_ITEM);
      Registry.register(Registries.BLOCK, new Identifier("dannys-aot", "hardened_slab"), HARDENED_SLAB);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "hardened_slab"), HARDENED_SLAB_ITEM);
      Registry.register(Registries.BLOCK, new Identifier("dannys-aot", "hardened_stairs"), HARDENED_STAIRS);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "hardened_stairs"), HARDENED_STAIRS_ITEM);
      Registry.register(Registries.BLOCK, new Identifier("dannys-aot", "ultra_hardened_block"), ULTRA_HARDENED_BLOCK);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "ultra_hardened_block"), ULTRA_HARDENED_BLOCK_ITEM);
      Registry.register(Registries.BLOCK, new Identifier("dannys-aot", "ultra_hardened_slab"), ULTRA_HARDENED_SLAB);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "ultra_hardened_slab"), ULTRA_HARDENED_SLAB_ITEM);
      Registry.register(Registries.BLOCK, new Identifier("dannys-aot", "ultra_hardened_stairs"), ULTRA_HARDENED_STAIRS);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "ultra_hardened_stairs"), ULTRA_HARDENED_STAIRS_ITEM);
      Registry.register(Registries.BLOCK, new Identifier("dannys-aot", "block_of_iceburst"), BLOCK_OF_ICEBURST);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "block_of_iceburst"), BLOCK_OF_ICEBURST_ITEM);
      Registry.register(Registries.BLOCK, new Identifier("dannys-aot", "bamboo_leaves"), BAMBOO_LEAVES);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "bamboo_leaves"), BAMBOO_LEAVES_ITEM);
      Registry.register(Registries.BLOCK, new Identifier("dannys-aot", "iron_bamboo_sapling"), IRON_BAMBOO_SAPLING);
      Registry.register(Registries.BLOCK, new Identifier("dannys-aot", "iron_bamboo"), IRON_BAMBOO);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "iron_bamboo"), IRON_BAMBOO_ITEM);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "hardened_iron_bamboo"), HARDENED_IRON_BAMBOO);
      Registry.register(Registries.BLOCK, new Identifier("dannys-aot", "iceburst_furnace"), ICEBURST_FURNACE);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "iceburst_furnace"), ICEBURST_FURNACE_ITEM);
      ICEBURST_FURNACE_BLOCK_ENTITY = Registry.register(
         Registries.BLOCK_ENTITY_TYPE,
         new Identifier("dannys-aot", "iceburst_furnace"),
         net.minecraft.block.entity.BlockEntityType.Builder.create(IceburstFurnaceBlockEntity::new, ICEBURST_FURNACE).build(null)
      );
      ICEBURST_FURNACE_MENU = Registry.register(
         Registries.SCREEN_HANDLER,
         new Identifier("dannys-aot", "iceburst_furnace"),
         new ScreenHandlerType<>(IceburstFurnaceMenu::new, FeatureFlags.DEFAULT_ENABLED_FEATURES)
      );
      STRWS_MENU = Registry.register(
         Registries.SCREEN_HANDLER, new Identifier("dannys-aot", "strws"), new ScreenHandlerType<>(StrwsMenu::new, FeatureFlags.DEFAULT_ENABLED_FEATURES)
      );
      Registry.register(Registries.BLOCK, new Identifier("dannys-aot", "paradis_portal"), PARADIS_PORTAL);
      PARADIS_PORTAL_BLOCK_ENTITY = Registry.register(
         Registries.BLOCK_ENTITY_TYPE,
         new Identifier("dannys-aot", "paradis_portal"),
         net.minecraft.block.entity.BlockEntityType.Builder.create(ParadisPortalBlockEntity::new, PARADIS_PORTAL).build(null)
      );
      Registry.register(Registries.BLOCK, new Identifier("dannys-aot", "titan_dummy"), TITAN_DUMMY_BLOCK);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "titan_dummy"), TITAN_DUMMY_ITEM);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "titan_dummy"), TITAN_DUMMY);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "titan_dummy_nape"), TITAN_DUMMY_NAPE);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "titan_dummy_eye"), TITAN_DUMMY_EYE);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "titan"), TITAN);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "titan_nape"), TITAN_NAPE);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "titan_eye"), TITAN_EYE);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "colossal_titan"), COLOSSAL_TITAN);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "colossal_titan_nape"), COLOSSAL_TITAN_NAPE);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "colossal_titan_eye"), COLOSSAL_TITAN_EYE);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "colossal_titan_hand"), COLOSSAL_TITAN_HAND);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "attack_titan"), ATTACK_TITAN);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "attack_titan_nape"), ATTACK_TITAN_NAPE);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "attack_titan_eye"), ATTACK_TITAN_EYE);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "attack_titan_grab"), ATTACK_TITAN_GRAB);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "ogre_shifter_titan"), OGRE_SHIFTER_TITAN);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "triple_t_titan"), TRIPLE_T_TITAN);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "test_shifter_titan"), TEST_SHIFTER_TITAN);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "cart_shifter_titan"), CART_SHIFTER_TITAN);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "armored_titan"), ARMORED_TITAN);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "armored_titan_nape"), ARMORED_TITAN_NAPE);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "armored_titan_eye"), ARMORED_TITAN_EYE);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "armored_titan_grab"), ARMORED_TITAN_GRAB);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "armored_titan_leg"), ARMORED_TITAN_LEG);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "female_titan"), FEMALE_TITAN);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "female_titan_nape"), FEMALE_TITAN_NAPE);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "female_titan_eye"), FEMALE_TITAN_EYE);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "female_titan_grab"), FEMALE_TITAN_GRAB);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "warhammer_titan"), WARHAMMER_TITAN);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "warhammer_titan_nape"), WARHAMMER_TITAN_NAPE);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "warhammer_titan_eye"), WARHAMMER_TITAN_EYE);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "small_titan"), SMALL_TITAN);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "small_titan_nape"), SMALL_TITAN_NAPE);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "small_titan_eye"), SMALL_TITAN_EYE);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "sad_titan"), SAD_TITAN);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "sad_titan_nape"), SAD_TITAN_NAPE);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "sad_titan_eye"), SAD_TITAN_EYE);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "small_titan_2"), SMALL_TITAN_2);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "small_titan_2_nape"), SMALL_TITAN_2_NAPE);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "small_titan_2_eye"), SMALL_TITAN_2_EYE);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "yellow_titan"), YELLOW_TITAN);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "yellow_titan_nape"), YELLOW_TITAN_NAPE);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "yellow_titan_eye"), YELLOW_TITAN_EYE);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "crawler_titan"), CRAWLER_TITAN);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "crawler_titan_nape"), CRAWLER_TITAN_NAPE);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "crawler_titan_eye"), CRAWLER_TITAN_EYE);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "fritz_titan"), FRITZ_TITAN);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "fritz_titan_nape"), FRITZ_TITAN_NAPE);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "fritz_titan_eye"), FRITZ_TITAN_EYE);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "titan_beard"), TITAN_BEARD);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "titan_beard_nape"), TITAN_BEARD_NAPE);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "titan_beard_eye"), TITAN_BEARD_EYE);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "titan_tropical"), TITAN_TROPICAL);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "titan_tropical_nape"), TITAN_TROPICAL_NAPE);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "titan_tropical_eye"), TITAN_TROPICAL_EYE);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "abnormal_titan"), ABNORMAL_TITAN);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "abnormal_titan_nape"), ABNORMAL_TITAN_NAPE);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "crawling_abnormal_titan"), CRAWLING_ABNORMAL_TITAN);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "crawling_abnormal_titan_nape"), CRAWLING_ABNORMAL_TITAN_NAPE);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "connie_father"), CONNIE_FATHER);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "connie_father_nape"), CONNIE_FATHER_NAPE);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "connie_father_eye"), CONNIE_FATHER_EYE);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "ogre_titan"), OGRE_TITAN);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "ogre_titan_nape"), OGRE_TITAN_NAPE);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "beast_titan"), BEAST_TITAN);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "beast_titan_nape"), BEAST_TITAN_NAPE);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "beast_titan_eye"), BEAST_TITAN_EYE);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "beast_titan_grab"), BEAST_TITAN_GRAB);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "founding_titan"), FOUNDING_TITAN);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "thunder_spear"), THUNDER_SPEAR_ENTITY);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "rock_projectile"), ROCK_PROJECTILE);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "warhammer_spike"), WARHAMMER_SPIKE);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "crystal_shell_female"), CRYSTAL_SHELL_FEMALE);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "crystal_shell_warhammer"), CRYSTAL_SHELL_WARHAMMER);
      FabricDefaultAttributeRegistry.register(TITAN_DUMMY, TitanDummyEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(TITAN_DUMMY_NAPE, TitanDummyNapeEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(TITAN_DUMMY_EYE, TitanDummyEyeEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(TITAN, TitanEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(TITAN_NAPE, TitanNapeEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(TITAN_EYE, TitanEyeEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(COLOSSAL_TITAN, ColossalTitanEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(COLOSSAL_TITAN_NAPE, ColossalTitanNapeEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(COLOSSAL_TITAN_EYE, ColossalTitanEyeEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(COLOSSAL_TITAN_HAND, ColossalTitanHandEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(ATTACK_TITAN, AttackTitanEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(ATTACK_TITAN_NAPE, AttackTitanNapeEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(ATTACK_TITAN_EYE, AttackTitanEyeEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(ATTACK_TITAN_GRAB, AttackTitanGrabEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(OGRE_SHIFTER_TITAN, OgreShifterTitanEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(TRIPLE_T_TITAN, TripleTTitanEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(TEST_SHIFTER_TITAN, TestShifterTitanEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(CART_SHIFTER_TITAN, CartShifterTitanEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(ARMORED_TITAN, ArmoredTitanEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(ARMORED_TITAN_GRAB, ArmoredTitanGrabEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(ARMORED_TITAN_NAPE, ArmoredTitanNapeEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(ARMORED_TITAN_EYE, ArmoredTitanEyeEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(ARMORED_TITAN_LEG, ArmoredTitanLegEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(FEMALE_TITAN, FemaleTitanEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(FEMALE_TITAN_NAPE, FemaleTitanNapeEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(FEMALE_TITAN_EYE, FemaleTitanEyeEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(FEMALE_TITAN_GRAB, FemaleTitanGrabEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(WARHAMMER_TITAN, WarhammerTitanEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(WARHAMMER_TITAN_NAPE, WarhammerTitanNapeEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(WARHAMMER_TITAN_EYE, WarhammerTitanEyeEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(SAD_TITAN, SadTitanEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(SAD_TITAN_NAPE, SadTitanNapeEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(SAD_TITAN_EYE, SadTitanEyeEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(SMALL_TITAN, SmallTitanEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(SMALL_TITAN_NAPE, SmallTitanNapeEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(SMALL_TITAN_EYE, SmallTitanEyeEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(SMALL_TITAN_2, SmallTitan2Entity.createAttributes());
      FabricDefaultAttributeRegistry.register(SMALL_TITAN_2_NAPE, SmallTitan2NapeEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(SMALL_TITAN_2_EYE, SmallTitan2EyeEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(YELLOW_TITAN, YellowTitanEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(YELLOW_TITAN_NAPE, YellowTitanNapeEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(YELLOW_TITAN_EYE, YellowTitanEyeEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(CRAWLER_TITAN, CrawlerTitanEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(CRAWLER_TITAN_NAPE, CrawlerTitanNapeEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(CRAWLER_TITAN_EYE, CrawlerTitanEyeEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(FRITZ_TITAN, FritzTitanEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(FRITZ_TITAN_NAPE, FritzTitanNapeEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(FRITZ_TITAN_EYE, FritzTitanEyeEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(TITAN_BEARD, TitanBeardEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(TITAN_BEARD_NAPE, TitanBeardNapeEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(TITAN_BEARD_EYE, TitanBeardEyeEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(TITAN_TROPICAL, TitanTropicalEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(TITAN_TROPICAL_NAPE, TitanTropicalNapeEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(TITAN_TROPICAL_EYE, TitanTropicalEyeEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(ABNORMAL_TITAN, AbnormalTitanEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(ABNORMAL_TITAN_NAPE, FritzTitanNapeEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(CRAWLING_ABNORMAL_TITAN, CrawlingAbnormalTitanEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(CRAWLING_ABNORMAL_TITAN_NAPE, FritzTitanNapeEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(CONNIE_FATHER, ConnieFatherEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(CONNIE_FATHER_NAPE, ConnieFatherNapeEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(CONNIE_FATHER_EYE, ConnieFatherEyeEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(OGRE_TITAN, OgreTitanEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(OGRE_TITAN_NAPE, OgreTitanNapeEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(BEAST_TITAN, BeastTitanEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(BEAST_TITAN_NAPE, BeastTitanNapeEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(BEAST_TITAN_EYE, BeastTitanEyeEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(BEAST_TITAN_GRAB, BeastTitanGrabEntity.createAttributes());
      FabricDefaultAttributeRegistry.register(FOUNDING_TITAN, FoundingTitanEntity.createAttributes());
      RegistryKey<Biome> TITAN_COUNTRY = RegistryKey.of(RegistryKeys.BIOME, new Identifier("dannys-aot", "titan_country"));
      RegistryKey<Biome> GIANT_FOREST = RegistryKey.of(RegistryKeys.BIOME, new Identifier("dannys-aot", "giant_forest"));
      RegistryKey<Biome> TITAN_HIGHLANDS = RegistryKey.of(RegistryKeys.BIOME, new Identifier("dannys-aot", "titan_highlands"));
      RegistryKey<Biome> TITAN_HILLS = RegistryKey.of(RegistryKeys.BIOME, new Identifier("dannys-aot", "titan_hills"));
      RegistryKey<Biome> TITAN_MOUNTAINS = RegistryKey.of(RegistryKeys.BIOME, new Identifier("dannys-aot", "titan_mountains"));
      Predicate<BiomeSelectionContext> paradisBiomes = BiomeSelectors.includeByKey(
         new RegistryKey[]{TITAN_COUNTRY, GIANT_FOREST, TITAN_HIGHLANDS, TITAN_HILLS, TITAN_MOUNTAINS}
      );
      SpawnRestriction.register(
         TITAN, Location.ON_GROUND, net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, TitanEntity::checkTitanSpawnRules
      );
      SpawnRestriction.register(
         SAD_TITAN, Location.ON_GROUND, net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, SadTitanEntity::checkSadTitanSpawnRules
      );
      SpawnRestriction.register(
         SMALL_TITAN, Location.ON_GROUND, net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, SmallTitanEntity::checkSmallTitanSpawnRules
      );
      SpawnRestriction.register(
         SMALL_TITAN_2,
         Location.ON_GROUND,
         net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
         SmallTitan2Entity::checkSmallTitan2SpawnRules
      );
      SpawnRestriction.register(
         YELLOW_TITAN,
         Location.ON_GROUND,
         net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
         YellowTitanEntity::checkYellowTitanSpawnRules
      );
      SpawnRestriction.register(
         CRAWLER_TITAN, Location.ON_GROUND, net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, CrawlerTitanEntity::checkCrawlerSpawnRules
      );
      SpawnRestriction.register(
         FRITZ_TITAN, Location.ON_GROUND, net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, FritzTitanEntity::checkFritzTitanSpawnRules
      );
      SpawnRestriction.register(
         TITAN_BEARD, Location.ON_GROUND, net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, TitanBeardEntity::checkTitanBeardSpawnRules
      );
      SpawnRestriction.register(
         TITAN_TROPICAL,
         Location.ON_GROUND,
         net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
         TitanTropicalEntity::checkTitanTropicalSpawnRules
      );
      SpawnRestriction.register(
         ABNORMAL_TITAN,
         Location.ON_GROUND,
         net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
         AbnormalTitanEntity::checkAbnormalTitanSpawnRules
      );
      SpawnRestriction.register(
         CRAWLING_ABNORMAL_TITAN,
         Location.ON_GROUND,
         net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
         CrawlingAbnormalTitanEntity::checkCrawlingAbnormalTitanSpawnRules
      );
      SpawnRestriction.register(
         CONNIE_FATHER,
         Location.ON_GROUND,
         net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
         ConnieFatherEntity::checkConnieFatherSpawnRules
      );
      SpawnRestriction.register(
         OGRE_TITAN, Location.ON_GROUND, net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, OgreTitanEntity::checkOgreTitanSpawnRules
      );
      ModConfig cfg = ModConfig.get();
      if (cfg.titanSpawnWeight > 0) {
         BiomeModifications.addSpawn(paradisBiomes, SpawnGroup.MONSTER, TITAN, cfg.titanSpawnWeight, 1, 1);
      }

      if (cfg.smallTitanSpawnWeight > 0) {
         BiomeModifications.addSpawn(paradisBiomes, SpawnGroup.MONSTER, SMALL_TITAN, cfg.smallTitanSpawnWeight, 1, 2);
         BiomeModifications.addSpawn(paradisBiomes, SpawnGroup.MONSTER, SAD_TITAN, cfg.smallTitanSpawnWeight, 1, 1);
      }

      if (cfg.smallTitan2SpawnWeight > 0) {
         BiomeModifications.addSpawn(paradisBiomes, SpawnGroup.MONSTER, SMALL_TITAN_2, cfg.smallTitan2SpawnWeight, 1, 1);
         BiomeModifications.addSpawn(paradisBiomes, SpawnGroup.MONSTER, YELLOW_TITAN, cfg.smallTitan2SpawnWeight, 1, 1);
         BiomeModifications.addSpawn(paradisBiomes, SpawnGroup.MONSTER, CRAWLER_TITAN, 4, 1, 1);
      }

      if (cfg.fritzTitanSpawnWeight > 0) {
         BiomeModifications.addSpawn(paradisBiomes, SpawnGroup.MONSTER, FRITZ_TITAN, cfg.fritzTitanSpawnWeight, 1, 1);
      }

      if (cfg.titanBeardSpawnWeight > 0) {
         BiomeModifications.addSpawn(paradisBiomes, SpawnGroup.MONSTER, TITAN_BEARD, cfg.titanBeardSpawnWeight, 1, 1);
      }

      if (cfg.titanTropicalSpawnWeight > 0) {
         BiomeModifications.addSpawn(paradisBiomes, SpawnGroup.MONSTER, TITAN_TROPICAL, cfg.titanTropicalSpawnWeight, 1, 1);
      }

      if (cfg.abnormalTitanSpawnWeight > 0) {
         BiomeModifications.addSpawn(paradisBiomes, SpawnGroup.MONSTER, ABNORMAL_TITAN, cfg.abnormalTitanSpawnWeight, 1, 1);
      }

      if (cfg.crawlingAbnormalTitanSpawnWeight > 0) {
         BiomeModifications.addSpawn(paradisBiomes, SpawnGroup.MONSTER, CRAWLING_ABNORMAL_TITAN, cfg.crawlingAbnormalTitanSpawnWeight, 1, 1);
      }

      if (cfg.connieFatherSpawnWeight > 0) {
         BiomeModifications.addSpawn(paradisBiomes, SpawnGroup.MONSTER, CONNIE_FATHER, cfg.connieFatherSpawnWeight, 1, 2);
      }

      BiomeModifications.addSpawn(paradisBiomes, SpawnGroup.MONSTER, OGRE_TITAN, 1, 1, 1);
      ServerEntityEvents.ENTITY_LOAD
         .register(
            (Load)(entity, world) -> {
               if (entity instanceof TitanEntity
                  || entity instanceof SmallTitanEntity
                  || entity instanceof SmallTitan2Entity
                  || entity instanceof FritzTitanEntity
                  || entity instanceof TitanBeardEntity
                  || entity instanceof TitanTropicalEntity
                  || entity instanceof SadTitanEntity
                  || entity instanceof ConnieFatherEntity
                  || entity instanceof OgreTitanEntity
                  || entity instanceof CrawlerTitanEntity) {
                  TitanEntity.onTitanLoaded();
               }
            }
         );
      ServerEntityEvents.ENTITY_UNLOAD
         .register(
            (Unload)(entity, world) -> {
               if (entity instanceof TitanEntity
                  || entity instanceof SmallTitanEntity
                  || entity instanceof SmallTitan2Entity
                  || entity instanceof FritzTitanEntity
                  || entity instanceof TitanBeardEntity
                  || entity instanceof TitanTropicalEntity
                  || entity instanceof SadTitanEntity
                  || entity instanceof ConnieFatherEntity
                  || entity instanceof OgreTitanEntity
                  || entity instanceof CrawlerTitanEntity) {
                  TitanEntity.onTitanUnloaded();
               }
            }
         );
      ServerPlayConnectionEvents.DISCONNECT
         .register(
            (Disconnect)(handler, server) -> {
               ServerPlayerEntity player = handler.getPlayer();
               if (HandcuffsTracker.isCuffed(player.getUuid())) {
                  ServerWorld level = player.getServerWorld();
                  TitanPowerData data = TitanPowerData.get(level);
                  TitanPowerType lostPower = null;

                  for (TitanPowerType power : TitanPowerType.values()) {
                     if (player.getCommandTags().contains(power.getTagName())) {
                        lostPower = power;
                        break;
                     }
                  }

                  if (lostPower != null) {
                     player.removeScoreboardTag(lostPower.getTagName());
                     data.removePlayerFromPower(player.getUuid(), lostPower);
                     if (data.playerHasPower(lostPower)) {
                        LOGGER.info(
                           "Player {} disconnected while cuffed - lost {} power (other holders remain)",
                           player.getName().getString(),
                           lostPower.getDisplayName()
                        );
                     } else {
                        VillagerEntity villager = new VillagerEntity(EntityType.VILLAGER, level);
                        villager.setPosition(player.getX(), player.getY(), player.getZ());
                        level.spawnEntity(villager);
                        data.setPower(villager.getUuid(), lostPower);
                        villager.addCommandTag("handcuffed");
                        villager.getNavigation().stop();
                        villager.setAiDisabled(true);
                        PoweredVillagerSyncPayload syncPayload = PoweredVillagerSyncPayload.addPower(villager.getId(), lostPower.ordinal());

                        for (ServerPlayerEntity p : level.getPlayers()) {
                           ServerPlayNetworking.send(p, syncPayload);
                        }

                        LOGGER.info(
                           "Player {} disconnected while cuffed - lost {} power, spawned cuffed powered villager",
                           player.getName().getString(),
                           lostPower.getDisplayName()
                        );
                     }
                  }

                  player.addCommandTag("dannysaot_combat_logged");
                  HandcuffsTracker.forceRemoveCuffed(player.getUuid());
               }

               Entity vehicle = player.getVehicle();
               boolean isInKnockoutState = false;
               if (player.hasStatusEffect(StatusEffects.WEAKNESS)) {
                  StatusEffectInstance weaknessEffect = player.getStatusEffect(StatusEffects.WEAKNESS);
                  isInKnockoutState = weaknessEffect != null && weaknessEffect.getAmplifier() >= 4;
               }

               if (isInKnockoutState) {
                  player.addCommandTag("dannysaot_combat_logged");
                  LOGGER.info("Player {} combat logged while in defeated titan state", player.getName().getString());
               }

               if (ModNetworking.isAwakenActive(player.getUuid())) {
                  ModNetworking.releaseAllTitansOnDisconnect(player);
               }

               boolean shouldDie = false;
               if (vehicle instanceof SmallTitanEntity smallTitan && smallTitan.isSelfInjectionPassenger(player)) {
                  shouldDie = true;
               } else if (vehicle instanceof SmallTitan2Entity smallTitan2 && smallTitan2.isSelfInjectionPassenger(player)) {
                  shouldDie = true;
               } else if (vehicle instanceof TitanEntity titan && titan.isSelfInjectionPassenger(player)) {
                  shouldDie = true;
               } else if (vehicle instanceof FritzTitanEntity fritzTitan && fritzTitan.isSelfInjectionPassenger(player)) {
                  shouldDie = true;
               } else if (vehicle instanceof TitanBeardEntity beardTitan && beardTitan.isSelfInjectionPassenger(player)) {
                  shouldDie = true;
               } else if (vehicle instanceof TitanTropicalEntity tropicalTitan && tropicalTitan.isSelfInjectionPassenger(player)) {
                  shouldDie = true;
               } else if (vehicle instanceof YellowTitanEntity yellowTitan && yellowTitan.isSelfInjectionPassenger(player)) {
                  shouldDie = true;
               } else if (vehicle instanceof SadTitanEntity sadTitan && sadTitan.isSelfInjectionPassenger(player)) {
                  shouldDie = true;
               } else if (vehicle instanceof CrawlerTitanEntity crawlerTitan && crawlerTitan.isSelfInjectionPassenger(player)) {
                  shouldDie = true;
               } else if (vehicle instanceof OgreTitanEntity ogreTitan && ogreTitan.isSelfInjectionPassenger(player)) {
                  shouldDie = true;
               }

               if (shouldDie) {
                  player.setInvisible(false);
                  player.stopRiding();
                  player.kill();
                  LOGGER.info("Player {} tried to escape their fate by disconnecting - killed", player.getName().getString());
               }

               if (vehicle instanceof ColossalTitanEntity colossalTitan && player.getUuid().equals(colossalTitan.getShifterUUID())) {
                  player.setInvisible(false);
                  player.stopRiding();
                  colossalTitan.discard();
                  LOGGER.info("Player {} disconnected while shifted as Colossal Titan - titan discarded", player.getName().getString());
               } else if (vehicle instanceof AttackTitanEntity attackTitan && player.getUuid().equals(attackTitan.getShifterUUID())) {
                  player.setInvisible(false);
                  player.stopRiding();
                  attackTitan.discard();
                  LOGGER.info("Player {} disconnected while shifted as Attack Titan - titan discarded", player.getName().getString());
               } else if (vehicle instanceof ArmoredTitanEntity armoredTitan && player.getUuid().equals(armoredTitan.getShifterUUID())) {
                  player.setInvisible(false);
                  player.stopRiding();
                  armoredTitan.discard();
                  LOGGER.info("Player {} disconnected while shifted as Armored Titan - titan discarded", player.getName().getString());
               } else if (vehicle instanceof FemaleTitanEntity femaleTitan && player.getUuid().equals(femaleTitan.getShifterUUID())) {
                  player.setInvisible(false);
                  player.stopRiding();
                  femaleTitan.discard();
                  LOGGER.info("Player {} disconnected while shifted as Female Titan - titan discarded", player.getName().getString());
               } else if (vehicle instanceof BeastTitanEntity beastTitan && player.getUuid().equals(beastTitan.getShifterUUID())) {
                  player.setInvisible(false);
                  player.stopRiding();
                  beastTitan.discard();
                  LOGGER.info("Player {} disconnected while shifted as Beast Titan - titan discarded", player.getName().getString());
               } else if (vehicle instanceof WarhammerTitanEntity warhammerTitan && player.getUuid().equals(warhammerTitan.getShifterUUID())) {
                  player.setInvisible(false);
                  player.stopRiding();
                  warhammerTitan.discard();
                  LOGGER.info("Player {} disconnected while shifted as Warhammer Titan - titan discarded", player.getName().getString());
               }
            }
         );
      LOGGER.info("Registered Titan and Small Titan spawns in Paradis biomes");
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "titan_spawn_egg"), PURE_TITAN_SPAWN_EGG);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "colossal_titan_spawn_egg"), COLOSSAL_TITAN_SPAWN_EGG);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "attack_titan_spawn_egg"), ATTACK_TITAN_SPAWN_EGG);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "armored_titan_spawn_egg"), ARMORED_TITAN_SPAWN_EGG);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "female_titan_spawn_egg"), FEMALE_TITAN_SPAWN_EGG);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "warhammer_titan_spawn_egg"), WARHAMMER_TITAN_SPAWN_EGG);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "red_flare_cartridge"), RED_FLARE_CARTRIDGE);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "black_flare_cartridge"), BLACK_FLARE_CARTRIDGE);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "purple_flare_cartridge"), PURPLE_FLARE_CARTRIDGE);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "blue_flare_cartridge"), BLUE_FLARE_CARTRIDGE);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "green_flare_cartridge"), GREEN_FLARE_CARTRIDGE);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "yellow_flare_cartridge"), YELLOW_FLARE_CARTRIDGE);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "flare_gun"), FLARE_GUN);
      Registry.register(Registries.BLOCK, new Identifier("dannys-aot", "regiment_banner"), REGIMENT_BANNER);
      Registry.register(Registries.BLOCK, new Identifier("dannys-aot", "wall_regiment_banner"), WALL_REGIMENT_BANNER);
      REGIMENT_BANNER_BLOCK_ENTITY = Registry.register(
         Registries.BLOCK_ENTITY_TYPE,
         new Identifier("dannys-aot", "regiment_banner"),
         net.minecraft.block.entity.BlockEntityType.Builder.create(RegimentBannerBlockEntity::new, REGIMENT_BANNER, WALL_REGIMENT_BANNER).build(null)
      );
      Registry.register(Registries.BLOCK, new Identifier("dannys-aot", "armor_potion_block"), ARMOR_POTION_BLOCK);
      ARMOR_POTION_BLOCK_ENTITY = Registry.register(
         Registries.BLOCK_ENTITY_TYPE,
         new Identifier("dannys-aot", "armor_potion_block"),
         net.minecraft.block.entity.BlockEntityType.Builder.create(ArmorPotionBlockEntity::new, ARMOR_POTION_BLOCK).build(null)
      );
      Registry.register(Registries.BLOCK, new Identifier("dannys-aot", "strws"), STRWS_BLOCK);
      Registry.register(Registries.BLOCK, new Identifier("dannys-aot", "strws_part"), STRWS_PART_BLOCK);
      STRWS_BLOCK_ENTITY = Registry.register(
         Registries.BLOCK_ENTITY_TYPE,
         new Identifier("dannys-aot", "strws"),
         net.minecraft.block.entity.BlockEntityType.Builder.create(StrwsBlockEntity::new, STRWS_BLOCK).build(null)
      );
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "strws"), STRWS_ITEM);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "garrison_banner"), GARRISON_BANNER);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "military_police_banner"), MILITARY_POLICE_BANNER);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "scout_banner"), SCOUT_BANNER);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "training_corps_banner"), TRAINING_CORPS_BANNER);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "gold_cloak_banner"), GOLD_CLOAK_BANNER);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "handcuffs"), HANDCUFFS);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "handcuffs_key"), HANDCUFFS_KEY);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "basement_key"), BASEMENT_KEY);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "shifter_muscles"), SHIFTER_MUSCLES);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "kennyhat"), KENNY_HAT);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "flinstock"), FLINSTOCK);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "musket"), MUSKET);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "ultrahard_leather"), ULTRAHARD_LEATHER);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "odm_wires"), ODM_WIRES);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "odm_spring"), ODM_SPRING);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "odm_cyllinder"), ODM_CYLINDER);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "trench_coat"), TRENCH_COAT);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "trench_coat_garrison"), TRENCH_COAT_GARRISON);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "trench_coat_scout"), TRENCH_COAT_SCOUT);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "trench_coat_military_police"), TRENCH_COAT_MILITARY_POLICE);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "commander_trench_coat"), COMMANDER_TRENCH_COAT);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "commander_trench_coat_garrison"), COMMANDER_TRENCH_COAT_GARRISON);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "commander_trench_coat_scout"), COMMANDER_TRENCH_COAT_SCOUT);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "commander_trench_coat_military_police"), COMMANDER_TRENCH_COAT_MILITARY_POLICE);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "trench_coat_royal"), TRENCH_COAT_ROYAL);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "marley_trench_coat"), MARLEY_TRENCH_COAT);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "marley_cadet_trench_coat"), MARLEY_CADET_TRENCH_COAT);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "marley_trench_coat_warrior"), MARLEY_CADET_TRENCH_COAT_WARRIOR);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "marley_armband_eldian"), MARLEY_ARMBAND_ELDIAN);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "marley_armband_soldier"), MARLEY_ARMBAND_SOLDIER);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "marley_armband_cadet"), MARLEY_ARMBAND_CADET);
      Registry.register(Registries.ITEM, new Identifier("dannys-aot", "marley_armband_warrior"), MARLEY_ARMBAND_WARRIOR);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "flare_projectile"), FLARE_PROJECTILE);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "apg_projectile"), APG_PROJECTILE);
      Registry.register(Registries.ENTITY_TYPE, new Identifier("dannys-aot", "gib"), GIB);
      Registry.register(Registries.PARTICLE_TYPE, new Identifier("dannys-aot", "flare_green"), FLARE_GREEN_PARTICLE);
      Registry.register(Registries.PARTICLE_TYPE, new Identifier("dannys-aot", "flare_red"), FLARE_RED_PARTICLE);
      Registry.register(Registries.PARTICLE_TYPE, new Identifier("dannys-aot", "flare_black"), FLARE_BLACK_PARTICLE);
      Registry.register(Registries.PARTICLE_TYPE, new Identifier("dannys-aot", "flare_purple"), FLARE_PURPLE_PARTICLE);
      Registry.register(Registries.PARTICLE_TYPE, new Identifier("dannys-aot", "flare_blue"), FLARE_BLUE_PARTICLE);
      Registry.register(Registries.PARTICLE_TYPE, new Identifier("dannys-aot", "flare_yellow"), FLARE_YELLOW_PARTICLE);
      Registry.register(Registries.PARTICLE_TYPE, new Identifier("dannys-aot", "nape_steam"), NAPE_STEAM_PARTICLE);
      Registry.register(Registries.PARTICLE_TYPE, new Identifier("dannys-aot", "shifter_trail"), SHIFTER_TRAIL_PARTICLE);
      Registry.register(Registries.PARTICLE_TYPE, new Identifier("dannys-aot", "player_dismount"), PLAYER_DISMOUNT_PARTICLE);
      Registry.register(Registries.PARTICLE_TYPE, new Identifier("dannys-aot", "nuke_smoke"), NUKE_SMOKE_PARTICLE);
      Registry.register(Registries.PARTICLE_TYPE, new Identifier("dannys-aot", "nuke_fire"), NUKE_FIRE_PARTICLE);
      Registry.register(Registries.PARTICLE_TYPE, new Identifier("dannys-aot", "nuke_dust"), NUKE_DUST_PARTICLE);
      Registry.register(Registries.PARTICLE_TYPE, new Identifier("dannys-aot", "colossal_steam"), COLOSSAL_STEAM_PARTICLE);
      Registry.register(Registries.PARTICLE_TYPE, new Identifier("dannys-aot", "colossal_nape_steam"), COLOSSAL_NAPE_STEAM_PARTICLE);
      Registry.register(Registries.PARTICLE_TYPE, new Identifier("dannys-aot", "colossal_nape_trail"), COLOSSAL_NAPE_TRAIL_PARTICLE);
      Registry.register(Registries.PARTICLE_TYPE, new Identifier("dannys-aot", "colossal_body_steam"), COLOSSAL_BODY_STEAM_PARTICLE);
      Registry.register(Registries.ITEM_GROUP, new Identifier("dannys-aot", "aot_items"), AOT_TAB);
      Registry.register(Registries.ITEM_GROUP, new Identifier("dannys-aot", "aot_items_clothing"), AOT_CLOTHING_TAB);
      Registry.register(Registries.ITEM_GROUP, new Identifier("dannys-aot", "aot_items_z_blocks"), AOT_BLOCKS_TAB);
      Registry.register(Registries.CHUNK_GENERATOR, new Identifier("dannys-aot", "paths"), PathsChunkGenerator.CODEC);
      LOGGER.info("Registered Paths dimension chunk generator");
      Registry.register(Registries.CHUNK_GENERATOR, new Identifier("dannys-aot", "paradis"), ParadisChunkGenerator.CODEC);
      LOGGER.info("Registered Paradis dimension chunk generator");
      Registry.register(Registries.BIOME_SOURCE, new Identifier("dannys-aot", "paradis"), ParadisBiomeSource.CODEC);
      LOGGER.info("Registered Paradis biome source");
      OverworldDocksGenerator.register();
      ParadisReturnPillarGenerator.register();
      IceburstOreGenerator.register();
      TitanCountryStructureGenerator.register();
      DispenserBehavior flareBehavior = (source, stack) -> {
         World level = source.getWorld();
         Direction facing = source.getBlockState().get(DispenserBlock.FACING);
         Position pos = DispenserBlock.getOutputLocation(source);
         int colorOrd = stack.getItem() instanceof FlareCartridgeItem fc ? fc.getColor().ordinal() : 0;
         FlareProjectileEntity flare = new FlareProjectileEntity(level, null, colorOrd);
         flare.setPosition(pos.getX(), pos.getY(), pos.getZ());
         flare.setVelocity(facing.getOffsetX(), facing.getOffsetY() + 0.1, facing.getOffsetZ(), 2.0F, 1.0F);
         level.spawnEntity(flare);
         stack.decrement(1);
         return stack;
      };
      DispenserBlock.registerBehavior(RED_FLARE_CARTRIDGE, flareBehavior);
      DispenserBlock.registerBehavior(BLACK_FLARE_CARTRIDGE, flareBehavior);
      DispenserBlock.registerBehavior(PURPLE_FLARE_CARTRIDGE, flareBehavior);
      DispenserBlock.registerBehavior(BLUE_FLARE_CARTRIDGE, flareBehavior);
      DispenserBlock.registerBehavior(GREEN_FLARE_CARTRIDGE, flareBehavior);
      DispenserBlock.registerBehavior(YELLOW_FLARE_CARTRIDGE, flareBehavior);
   }

   private static void writeShifterConfig(MinecraftServer server) {
      try {
         ServerWorld overworld = server.getOverworld();
         TitanPowerData data = TitanPowerData.get(overworld);
         StringBuilder sb = new StringBuilder();
         sb.append("=== Danny's AoT - Current Titan Shifters ===\n");
         sb.append("(Updated: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append(")\n\n");

         for (TitanPowerType power : TitanPowerType.values()) {
            Set<UUID> holders = data.getPlayersWithPower(power);
            if (holders.isEmpty()) {
               sb.append(power.getDisplayName()).append(": (unclaimed)\n");
            } else {
               List<String> entries = new ArrayList<>();

               for (UUID holderUuid : holders) {
                  String playerName = null;
                  ServerPlayerEntity onlinePlayer = server.getPlayerManager().getPlayer(holderUuid);
                  if (onlinePlayer != null) {
                     playerName = onlinePlayer.getName().getString();
                  } else {
                     Optional<GameProfile> profile = server.getUserCache().getByUuid(holderUuid);
                     if (profile.isPresent()) {
                        playerName = profile.get().getName();
                     }
                  }

                  entries.add((playerName != null ? playerName : "Unknown") + " (" + holderUuid + ")");
               }

               sb.append(power.getDisplayName()).append(": ").append(String.join(", ", entries)).append("\n");
            }
         }

         Path configDir = FabricLoader.getInstance().getConfigDir();
         Files.createDirectories(configDir);
         Path configFile = configDir.resolve("daot_shifters.txt");
         Files.writeString(configFile, sb.toString());
      } catch (Exception var15) {
         LOGGER.warn("Failed to write shifter config file: {}", var15.getMessage());
      }
   }
}
