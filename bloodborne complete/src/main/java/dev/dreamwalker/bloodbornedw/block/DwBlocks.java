package dev.dreamwalker.bloodbornedw.block;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import net.minecraft.block.*;
import dev.dreamwalker.bloodbornedw.mixin.AbstractBlockSettingsAccessor;
import dev.dreamwalker.bloodbornedw.mixin.AbstractBlockAccessor;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.DyeColor;
import net.minecraft.world.biome.Biome;
import net.minecraft.block.cauldron.CauldronBehavior;
import net.minecraft.block.sapling.SaplingGenerator;
import net.minecraft.util.math.intprovider.IntProvider;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.block.ShapeContext;
import net.minecraft.world.BlockView;
import net.minecraft.util.shape.VoxelShape;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.lang.reflect.Constructor;

/** Registers one native vanilla-family carrier for every catalog block. */
public final class DwBlocks {
    public static final EnumProperty<Visual> VISUAL = EnumProperty.of("visual", Visual.class);
    private static final Map<Identifier, Entry> BY_SOURCE = new LinkedHashMap<>();
    private static final Map<String, Entry> BY_ID = new LinkedHashMap<>();
    private static final Map<Block, Entry> BY_BLOCK = new LinkedHashMap<>();
    private static final Map<Block, Entry> BY_BLOCK_IDENTITY = new IdentityHashMap<>();
    private static boolean initialized;

    private DwBlocks() {}

    public static synchronized void initialize() {
        if (initialized) return;
        Catalog catalog = readCatalog();
        if (catalog.schema_version != 1 || catalog.blocks == null) throw new IllegalStateException("Unsupported DW block catalog");
        List<PendingEntry> pending = new ArrayList<>();
        List<String> unsupported = new ArrayList<>();
        for (CatalogBlock specification : catalog.blocks) {
            try { pending.add(prepare(specification)); }
            catch (IllegalStateException exception) { unsupported.add(exception.getMessage()); }
        }
        if (!unsupported.isEmpty()) throw new IllegalStateException("DW catalog preflight failed:\n" + String.join("\n", unsupported));
        for (PendingEntry entry : pending) register(entry);
        validateShapes();
        initialized = true;
    }

    public static Entry bySource(Identifier source) { initialize(); return BY_SOURCE.get(source); }
    public static Entry byId(String id) { initialize(); return BY_ID.get(id); }
    public static Collection<Entry> entries() { initialize(); return Collections.unmodifiableCollection(BY_ID.values()); }
    public static boolean isDw(Block block) {
        return BY_BLOCK_IDENTITY.containsKey(block);
    }
    public static Entry byBlock(Block block) { initialize(); return BY_BLOCK_IDENTITY.get(block); }
    public static boolean isCarrierOf(Block carrier, Block source) {
        Entry entry = BY_BLOCK.get(carrier);
        return entry != null && entry.sourceBlock == source;
    }

    /** Converts a DW carrier state back to its original vanilla state, excluding {@link #VISUAL}. */
    public static BlockState sourceState(BlockState state) {
        initialize();
        Entry entry = BY_BLOCK.get(state.getBlock());
        if (entry == null) return state;
        BlockState result = entry.sourceBlock.getDefaultState();
        for (Property<?> property : state.getProperties()) result = copy(result, state, property);
        return result;
    }

    /** Converts a vanilla source state to this catalog's carrier state with the base visual. */
    public static BlockState carrierState(BlockState state) {
        Entry entry = bySource(Registries.BLOCK.getId(state.getBlock()));
        if (entry == null) return state;
        BlockState result = entry.block.getDefaultState();
        for (Property<?> property : state.getProperties()) result = copy(result, state, property);
        return result.with(VISUAL, Visual.BASE);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState copy(BlockState result, BlockState source, Property property) {
        return result.contains(property) ? result.with(property, source.get(property)) : result;
    }

    private static PendingEntry prepare(CatalogBlock specification) {
        if (specification.id == null || !specification.id.matches("\\d{5}")) throw new IllegalStateException("Invalid DW block id: " + specification.id);
        Identifier sourceId = Identifier.tryParse(specification.source);
        if (sourceId == null || !"minecraft".equals(sourceId.getNamespace())) throw new IllegalStateException("Invalid vanilla source for " + specification.id);
        if (BY_ID.containsKey(specification.id) || BY_SOURCE.containsKey(sourceId)) throw new IllegalStateException("Duplicate DW block catalog entry: " + specification.id);
        Block source = Registries.BLOCK.getOrEmpty(sourceId).orElseThrow(() -> new IllegalStateException("Unknown vanilla source " + sourceId));
        Block carrier = createCarrier(source, sourceId, specification);
        BlockState nativeDefault = carrier.getDefaultState();
        for (Property<?> property : source.getDefaultState().getProperties()) nativeDefault = copy(nativeDefault, source.getDefaultState(), property);
        ((dev.dreamwalker.bloodbornedw.mixin.BlockDefaultStateAccessor) carrier).bloodborneDw$setDefaultState(nativeDefault.with(VISUAL, Visual.BASE));
        return new PendingEntry(specification, sourceId, source, carrier);
    }

    private static void register(PendingEntry pending) {
        CatalogBlock specification = pending.specification;
        Identifier sourceId = pending.sourceId;
        Block source = pending.source;
        Block carrier = pending.carrier;
        Identifier id = new Identifier("bloodborne_dw", specification.id);
        Registry.register(Registries.BLOCK, id, carrier);
        Entry entry = new Entry(specification.id, id, sourceId, source, carrier, specification.frequency, specification.name_ru, specification.name_en);
        BY_ID.put(entry.id, entry);
        BY_SOURCE.put(sourceId, entry);
        BY_BLOCK.put(carrier, entry);
        BY_BLOCK_IDENTITY.put(carrier, entry);
    }

    private static Block createCarrier(Block source, Identifier sourceId, CatalogBlock specification) {
        AbstractBlock.Settings settings = AbstractBlock.Settings.copy(source);
        AbstractBlockSettingsAccessor original = (AbstractBlockSettingsAccessor) (Object) ((AbstractBlockAccessor) source).bloodborneDw$getSettings();
        AbstractBlockSettingsAccessor target = (AbstractBlockSettingsAccessor) (Object) settings;
        target.bloodborneDw$setAllowsSpawningPredicate(original.bloodborneDw$allowsSpawningPredicate());
        target.bloodborneDw$setSolidBlockPredicate(original.bloodborneDw$solidBlockPredicate());
        target.bloodborneDw$setSuffocationPredicate(original.bloodborneDw$suffocationPredicate());
        target.bloodborneDw$setBlockVisionPredicate(original.bloodborneDw$blockVisionPredicate());
        target.bloodborneDw$setPostProcessPredicate(original.bloodborneDw$postProcessPredicate());
        settings.jumpVelocityMultiplier(source.getJumpVelocityMultiplier());
        ((AbstractBlockSettingsAccessor) (Object) settings).bloodborneDw$setLootTableId(new Identifier("bloodborne_dw", "blocks/" + specification.id));
        if (specification.has_alpha || "translucent".equals(specification.render_layer)) settings.nonOpaque();
        return DwCarrierState.create(() -> {
            if (source instanceof OxidizableStairsBlock oxidizableStairs) return new DwOxidizableStairsBlock(oxidizableStairs.getDegradationLevel(), stairsBaseState(source), settings);
            if (source instanceof StairsBlock) return new DwStairsBlock(stairsBaseState(source), settings);
            if (source instanceof FenceBlock) return new FenceBlock(settings);
            if (source instanceof WallBlock) return new DwWallBlock(settings);
            if (source instanceof DoorBlock door) return new DwDoorBlock(settings, door.getBlockSetType());
            if (source instanceof ButtonBlock) return new DwButtonBlock(settings, blockSetType(sourceId), wooden(sourceId) ? 30 : 20, wooden(sourceId));
            if (source instanceof WeightedPressurePlateBlock) return new DwWeightedPressurePlateBlock(sourceId, settings, blockSetType(sourceId));
            if (source instanceof PressurePlateBlock) return new DwPressurePlateBlock(wooden(sourceId) ? PressurePlateBlock.ActivationRule.EVERYTHING : PressurePlateBlock.ActivationRule.MOBS, settings, blockSetType(sourceId));
            if (source instanceof SlabBlock) return new SlabBlock(settings);
            if (source instanceof FenceGateBlock) return new FenceGateBlock(settings, woodType(sourceId));
            if (source instanceof TrapdoorBlock) return new DwTrapdoorBlock(settings, blockSetType(sourceId));
            if (source instanceof LanternBlock) return new LanternBlock(settings);
            if (source instanceof FlowerPotBlock pot) return new DwFlowerPotBlock(pot.getContent(), settings);
            if (source instanceof WitherRoseBlock flower) return new WitherRoseBlock(flower.getEffectInStew(), settings);
            if (source instanceof FlowerBlock flower) return new FlowerBlock(flower.getEffectInStew(), flowerDurationSeconds(flower), settings);
            if (source instanceof RotatedInfestedBlock infested) return new RotatedInfestedBlock(infested.getRegularBlock(), settings);
            if (source instanceof InfestedBlock infested) return new InfestedBlock(infested.getRegularBlock(), settings);
            if (source instanceof DyedCarpetBlock) return new DyedCarpetBlock(dyeColor(sourceId), settings);
            if (source instanceof SaplingBlock sapling) return new DwSaplingBlock(typedField(sapling, SaplingBlock.class, SaplingGenerator.class), settings);
            if (source instanceof RedstoneOreBlock) return new RedstoneOreBlock(settings);
            if (source instanceof ConcretePowderBlock) return new DwConcretePowderBlock(hardenedConcrete(sourceId), settings);
            if (source.getClass() == ExperienceDroppingBlock.class) return new ExperienceDroppingBlock(settings, typedField(source, ExperienceDroppingBlock.class, IntProvider.class));
            if (source instanceof OxidizableBlock oxidizable) return new DwOxidizableBlock(oxidizable.getDegradationLevel(), settings);
            if (source instanceof GlazedTerracottaBlock) return new GlazedTerracottaBlock(settings);
            if (source instanceof MushroomBlock) return new MushroomBlock(settings);
            if (source instanceof NetherrackBlock) return new DwNetherrackBlock(settings);
            if (source instanceof WetSpongeBlock) return new DwWetSpongeBlock(settings);
            if (source instanceof SpongeBlock) return new DwSpongeBlock(settings);
            if (source instanceof SandBlock) return new SandBlock(sourceId.getPath().startsWith("red_") ? 11098145 : 14406560, settings);
            if (source instanceof RootedDirtBlock) return new RootedDirtBlock(settings);
            if (source.getClass() == FallingBlock.class) return new FallingBlock(settings);
            if (source instanceof HayBlock) return new HayBlock(settings);
            if (source instanceof ChainBlock) return new DwChainBlock(settings);
            if (source.getClass() == PillarBlock.class) return new PillarBlock(settings);
            if (source instanceof LeavesBlock) return new DwLeavesBlock(settings);
            if (source instanceof IceBlock) return new DwIceBlock(settings);
            if (source instanceof TintedGlassBlock) return new TintedGlassBlock(settings);
            if (source instanceof StainedGlassBlock) return new StainedGlassBlock(dyeColor(sourceId), settings);
            if (source instanceof GlassBlock) return new GlassBlock(settings);
            if (source.getClass() == TransparentBlock.class) return new DwTransparentBlock(settings);
            if (source instanceof GrassBlock) return new DwGrassBlock(settings);
            if (source instanceof MyceliumBlock) return new DwMyceliumBlock(settings);
            if (source instanceof DirtPathBlock) return new DwDirtPathBlock(settings);
            if (source instanceof FarmlandBlock) return new DwFarmlandBlock(settings);
            if (source instanceof SnowBlock) return new DwSnowBlock(settings);
            if (source instanceof PowderSnowBlock) return new PowderSnowBlock(settings);
            if (source instanceof PowderSnowCauldronBlock) return new PowderSnowCauldronBlock(settings, precipitation -> precipitation == Biome.Precipitation.SNOW, CauldronBehavior.POWDER_SNOW_CAULDRON_BEHAVIOR);
            if (source instanceof StainedGlassPaneBlock pane) return new StainedGlassPaneBlock(pane.getColor(), settings);
            if (source instanceof PaneBlock) return new DwPaneBlock(settings);
            if (source instanceof DetectorRailBlock) return new DetectorRailBlock(settings);
            if (source instanceof PoweredRailBlock) return new DwPoweredRailBlock(settings);
            if (source instanceof RailBlock) return new DwRailBlock(settings);
            if (source instanceof BeehiveBlock) return new BeehiveBlock(settings);
            if (source instanceof LecternBlock) return new DwLecternBlock(settings);
            if (source instanceof DaylightDetectorBlock) return new DaylightDetectorBlock(settings);
            if (source instanceof DeadCoralBlock) return new DwDeadCoralBlock(settings);
            if (source instanceof DeadCoralWallFanBlock) return new DwDeadCoralWallFanBlock(settings);
            if (source instanceof DeadCoralFanBlock) return new DwDeadCoralFanBlock(settings);
            if (source instanceof CandleBlock) return new CandleBlock(settings);
            if (source instanceof AmethystClusterBlock) return new DwAmethystClusterBlock(sourceId, settings);
            if (source.getClass() == Block.class) return new Block(settings);
            return sameClassSettingsCarrier(source, sourceId, settings);
        });
    }

    private static Block sameClassSettingsCarrier(Block source, Identifier sourceId, AbstractBlock.Settings settings) {
        try {
            Constructor<? extends Block> constructor = source.getClass().asSubclass(Block.class).getDeclaredConstructor(AbstractBlock.Settings.class);
            constructor.setAccessible(true);
            return constructor.newInstance(settings);
        } catch (ReflectiveOperationException | SecurityException exception) {
            throw new IllegalStateException("DW catalog source needs a native carrier factory: " + sourceId + " (" + source.getClass().getSimpleName() + ")", exception);
        }
    }

    private static void validateShapes() {
        for (Entry entry : BY_ID.values()) {
            for (BlockState state : entry.block.getStateManager().getStates()) {
                try {
                    if (state.getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN, ShapeContext.absent()) == null
                            || state.getOutlineShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN, ShapeContext.absent()) == null) {
                        throw new IllegalStateException("null shape");
                    }
                } catch (RuntimeException exception) {
                    throw new IllegalStateException("Invalid native carrier shape for " + entry.identifier, exception);
                }
            }
        }
    }

    private static BlockState stairsBaseState(Block source) {
        return typedField(source, StairsBlock.class, BlockState.class);
    }

    /** Production mappings rename fields, so select the sole constructor value by type. */
    private static <T> T typedField(Object source, Class<?> owner, Class<T> type) {
        try {
            java.lang.reflect.Field match = null;
            for (var field : owner.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || !type.isAssignableFrom(field.getType())) continue;
                if (match != null) throw new IllegalStateException("Ambiguous " + type.getSimpleName() + " field on " + owner.getSimpleName());
                match = field;
            }
            if (match == null) throw new IllegalStateException("Missing " + type.getSimpleName() + " field on " + owner.getSimpleName());
            match.setAccessible(true);
            return type.cast(match.get(source));
        } catch (IllegalAccessException | SecurityException exception) {
            throw new IllegalStateException("Cannot read native constructor field from " + owner.getSimpleName(), exception);
        }
    }

    private static WoodType woodType(Identifier id) {
        String path = id.getPath();
        if (path.startsWith("spruce_")) return WoodType.SPRUCE;
        if (path.startsWith("birch_")) return WoodType.BIRCH;
        if (path.startsWith("acacia_")) return WoodType.ACACIA;
        if (path.startsWith("cherry_")) return WoodType.CHERRY;
        if (path.startsWith("jungle_")) return WoodType.JUNGLE;
        if (path.startsWith("dark_oak_")) return WoodType.DARK_OAK;
        if (path.startsWith("crimson_")) return WoodType.CRIMSON;
        if (path.startsWith("warped_")) return WoodType.WARPED;
        if (path.startsWith("mangrove_")) return WoodType.MANGROVE;
        if (path.startsWith("bamboo_")) return WoodType.BAMBOO;
        return WoodType.OAK;
    }

    private static BlockSetType blockSetType(Identifier id) {
        String path = id.getPath();
        if (path.startsWith("iron_")) return BlockSetType.IRON;
        if (path.startsWith("gold_")) return BlockSetType.GOLD;
        if (path.startsWith("stone_")) return BlockSetType.STONE;
        if (path.startsWith("polished_blackstone_")) return BlockSetType.POLISHED_BLACKSTONE;
        return woodType(id).setType();
    }

    private static boolean wooden(Identifier id) {
        String path = id.getPath();
        return !(path.startsWith("stone_") || path.startsWith("polished_blackstone_") || path.startsWith("iron_") || path.startsWith("gold_"));
    }

    private static DyeColor dyeColor(Identifier id) {
        String path = id.getPath();
        for (DyeColor color : DyeColor.values()) if (path.startsWith(color.getName() + "_")) return color;
        throw new IllegalStateException("Missing dye color in source id " + id);
    }

    private static Block hardenedConcrete(Identifier id) {
        String path = id.getPath();
        Identifier concrete = new Identifier("minecraft", path.substring(0, path.length() - "_powder".length()));
        return Registries.BLOCK.getOrEmpty(concrete).orElseThrow(() -> new IllegalStateException("Missing hardened concrete for " + id));
    }

    /** FlowerBlock stores non-instant stew durations internally in ticks. */
    private static int flowerDurationSeconds(FlowerBlock flower) {
        int duration = flower.getEffectInStewDuration();
        return flower.getEffectInStew().isInstant() ? duration : duration / 20;
    }

    private static Catalog readCatalog() {
        var input = DwBlocks.class.getResourceAsStream("/assets/bloodborne_dw/catalog.json");
        if (input == null) throw new IllegalStateException("Missing DW block catalog");
        try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, Catalog.class);
        } catch (JsonParseException | java.io.IOException exception) {
            throw new IllegalStateException("Invalid DW block catalog", exception);
        }
    }

    public static final class Entry {
        private final String id;
        private final Identifier identifier;
        private final Identifier source;
        private final Block sourceBlock;
        private final Block block;
        private final int frequency;
        private final String nameRu;
        private final String nameEn;

        private Entry(String id, Identifier identifier, Identifier source, Block sourceBlock, Block block, int frequency, String nameRu, String nameEn) {
            this.id = id; this.identifier = identifier; this.source = source; this.sourceBlock = sourceBlock; this.block = block;
            this.frequency = frequency; this.nameRu = nameRu; this.nameEn = nameEn;
        }
        public String id() { return id; }
        public Identifier identifier() { return identifier; }
        public Identifier source() { return source; }
        public Block sourceBlock() { return sourceBlock; }
        public Block block() { return block; }
        public int frequency() { return frequency; }
        public String nameRu() { return nameRu; }
        public String nameEn() { return nameEn; }
    }

    private static final class Catalog { int schema_version; List<CatalogBlock> blocks = new ArrayList<>(); }
    private static final class CatalogBlock { String id; String source; int frequency; String name_ru; String name_en; boolean has_alpha; String render_layer; }
    private record PendingEntry(CatalogBlock specification, Identifier sourceId, Block source, Block carrier) {}

    private static final class DwStairsBlock extends StairsBlock {
        private DwStairsBlock(BlockState base, AbstractBlock.Settings settings) { super(base, settings); }
        @Override public boolean hasRandomTicks(BlockState state) { return false; }
    }
    private static final class DwWallBlock extends WallBlock {
        private DwWallBlock(AbstractBlock.Settings settings) { super(settings); }
        @Override public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return super.getOutlineShape(base(state), world, pos, context); }
        @Override public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return super.getCollisionShape(base(state), world, pos, context); }
        @Override public VoxelShape getCullingShape(BlockState state, BlockView world, BlockPos pos) { return super.getCullingShape(base(state), world, pos); }
        private BlockState base(BlockState state) { return state.with(VISUAL, Visual.BASE); }
    }
    private static final class DwOxidizableStairsBlock extends OxidizableStairsBlock {
        private DwOxidizableStairsBlock(Oxidizable.OxidationLevel level, BlockState base, AbstractBlock.Settings settings) { super(level, base, settings); }
        @Override public boolean hasRandomTicks(BlockState state) { return false; }
    }
    private static final class DwOxidizableBlock extends OxidizableBlock {
        private DwOxidizableBlock(Oxidizable.OxidationLevel level, AbstractBlock.Settings settings) { super(level, settings); }
        @Override public boolean hasRandomTicks(BlockState state) { return false; }
    }
    private static final class DwDoorBlock extends DoorBlock {
        private DwDoorBlock(AbstractBlock.Settings settings, BlockSetType type) { super(settings, type); }
    }
    private static final class DwFlowerPotBlock extends FlowerPotBlock {
        private DwFlowerPotBlock(Block content, AbstractBlock.Settings settings) { super(content, settings); }
        @Override public net.minecraft.util.ActionResult onUse(BlockState state, net.minecraft.world.World world, BlockPos pos, net.minecraft.entity.player.PlayerEntity player, net.minecraft.util.Hand hand, net.minecraft.util.hit.BlockHitResult hit) {
            var stack = player.getStackInHand(hand);
            Block plant = stack.getItem() instanceof net.minecraft.item.BlockItem item ? item.getBlock() : Blocks.AIR;
            Entry plantEntry = byBlock(plant);
            if (plantEntry != null) plant = plantEntry.sourceBlock();
            Entry potted = null;
            for (Entry entry : entries()) if (entry.sourceBlock() instanceof FlowerPotBlock pot && pot.getContent() == plant && plant != Blocks.AIR) { potted = entry; break; }
            if (potted != null) {
                if (getContent() != Blocks.AIR) return net.minecraft.util.ActionResult.CONSUME;
                world.setBlockState(pos, potted.block().getDefaultState().with(VISUAL, state.get(VISUAL)), Block.NOTIFY_ALL);
                player.incrementStat(net.minecraft.stat.Stats.POT_FLOWER);
                if (!player.getAbilities().creativeMode) stack.decrement(1);
                world.emitGameEvent(player, net.minecraft.world.event.GameEvent.BLOCK_CHANGE, pos);
                return net.minecraft.util.ActionResult.success(world.isClient);
            }
            // Native fallback retains compatibility with plants supplied by other mods.
            var result = super.onUse(state, world, pos, player, hand, hit);
            BlockState after = world.getBlockState(pos);
            BlockState carrier = carrierState(after);
            if (carrier != after && carrier.contains(VISUAL)) world.setBlockState(pos, carrier.with(VISUAL, state.get(VISUAL)), Block.NOTIFY_ALL);
            return result;
        }
    }
    private static final class DwNetherrackBlock extends NetherrackBlock {
        private DwNetherrackBlock(AbstractBlock.Settings settings) { super(settings); }
        @Override public boolean isFertilizable(net.minecraft.world.WorldView world, BlockPos pos, BlockState state, boolean isClient) { return false; }
    }
    private static final class DwButtonBlock extends ButtonBlock {
        private DwButtonBlock(AbstractBlock.Settings settings, BlockSetType type, int ticks, boolean wooden) { super(settings, type, ticks, wooden); }
    }
    private static final class DwPressurePlateBlock extends PressurePlateBlock {
        private DwPressurePlateBlock(ActivationRule rule, AbstractBlock.Settings settings, BlockSetType type) { super(rule, settings, type); }
    }
    private static final class DwWeightedPressurePlateBlock extends WeightedPressurePlateBlock {
        private DwWeightedPressurePlateBlock(Identifier id, AbstractBlock.Settings settings, BlockSetType type) { super(id.getPath().startsWith("heavy_") ? 150 : 15, settings, type); }
    }
    private static final class DwTrapdoorBlock extends TrapdoorBlock {
        private DwTrapdoorBlock(AbstractBlock.Settings settings, BlockSetType type) { super(settings, type); }
    }
    private static final class DwSnowBlock extends SnowBlock {
        private DwSnowBlock(AbstractBlock.Settings settings) { super(settings); }
        @Override public boolean hasRandomTicks(BlockState state) { return false; }
    }
    private static final class DwDirtPathBlock extends DirtPathBlock {
        private DwDirtPathBlock(AbstractBlock.Settings settings) { super(settings); }
        @Override public BlockState getPlacementState(net.minecraft.item.ItemPlacementContext context) { return getDefaultState(); }
        @Override public void scheduledTick(BlockState state, net.minecraft.server.world.ServerWorld world, BlockPos pos, net.minecraft.util.math.random.Random random) { }
    }
    /** Keep architectural artwork stable; retain the native collision and fall damage. */
    private static final class DwFarmlandBlock extends FarmlandBlock {
        private DwFarmlandBlock(AbstractBlock.Settings settings) { super(settings); }
        @Override public BlockState getPlacementState(net.minecraft.item.ItemPlacementContext context) { return getDefaultState(); }
        @Override public boolean hasRandomTicks(BlockState state) { return false; }
        @Override public void randomTick(BlockState state, net.minecraft.server.world.ServerWorld world, BlockPos pos, net.minecraft.util.math.random.Random random) { }
        @Override public void scheduledTick(BlockState state, net.minecraft.server.world.ServerWorld world, BlockPos pos, net.minecraft.util.math.random.Random random) { }
        @Override public void onLandedUpon(net.minecraft.world.World world, BlockState state, BlockPos pos, net.minecraft.entity.Entity entity, float distance) {
            entity.handleFallDamage(distance, 1.0F, entity.getDamageSources().fall());
        }
    }
    private static final class DwConcretePowderBlock extends ConcretePowderBlock {
        private DwConcretePowderBlock(Block hardened, AbstractBlock.Settings settings) { super(hardened, settings); }
        @Override public BlockState getPlacementState(net.minecraft.item.ItemPlacementContext context) { return getDefaultState(); }
        @Override public BlockState getStateForNeighborUpdate(BlockState state, net.minecraft.util.math.Direction direction, BlockState neighbor, net.minecraft.world.WorldAccess world, BlockPos pos, BlockPos neighborPos) {
            world.scheduleBlockTick(pos, this, getFallDelay());
            return state;
        }
        @Override public void onLanding(net.minecraft.world.World world, BlockPos pos, BlockState state, BlockState replaced, net.minecraft.entity.FallingBlockEntity entity) { }
    }
    private static final class DwWetSpongeBlock extends WetSpongeBlock {
        private DwWetSpongeBlock(AbstractBlock.Settings settings) { super(settings); }
        @Override public void onBlockAdded(BlockState state, net.minecraft.world.World world, BlockPos pos, BlockState oldState, boolean notify) { }
    }
    private static final class DwSpongeBlock extends SpongeBlock {
        private DwSpongeBlock(AbstractBlock.Settings settings) { super(settings); }
        @Override public void onBlockAdded(BlockState state, net.minecraft.world.World world, BlockPos pos, BlockState oldState, boolean notify) { }
        @Override public void neighborUpdate(BlockState state, net.minecraft.world.World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) { }
    }
    private static final class DwSaplingBlock extends SaplingBlock {
        private DwSaplingBlock(SaplingGenerator generator, AbstractBlock.Settings settings) { super(generator, settings); }
        @Override public boolean hasRandomTicks(BlockState state) { return false; }
    }
    private static final class DwLeavesBlock extends LeavesBlock {
        private DwLeavesBlock(AbstractBlock.Settings settings) { super(settings); }
        @Override public boolean hasRandomTicks(BlockState state) { return false; }
    }
    private static final class DwGrassBlock extends GrassBlock {
        private DwGrassBlock(AbstractBlock.Settings settings) { super(settings); }
        @Override public boolean hasRandomTicks(BlockState state) { return false; }
    }
    private static final class DwMyceliumBlock extends MyceliumBlock {
        private DwMyceliumBlock(AbstractBlock.Settings settings) { super(settings); }
        @Override public boolean hasRandomTicks(BlockState state) { return false; }
    }
    private static final class DwIceBlock extends IceBlock {
        private DwIceBlock(AbstractBlock.Settings settings) { super(settings); }
        @Override public boolean hasRandomTicks(BlockState state) { return false; }
    }
    private static final class DwPaneBlock extends PaneBlock {
        private DwPaneBlock(AbstractBlock.Settings settings) { super(settings); }
    }
    private static final class DwLecternBlock extends LecternBlock {
        private DwLecternBlock(AbstractBlock.Settings settings) { super(settings); }
    }
    private static final class DwDeadCoralBlock extends DeadCoralBlock {
        private DwDeadCoralBlock(AbstractBlock.Settings settings) { super(settings); }
    }
    private static final class DwDeadCoralFanBlock extends DeadCoralFanBlock {
        private DwDeadCoralFanBlock(AbstractBlock.Settings settings) { super(settings); }
    }
    private static final class DwDeadCoralWallFanBlock extends DeadCoralWallFanBlock {
        private DwDeadCoralWallFanBlock(AbstractBlock.Settings settings) { super(settings); }
    }
    private static final class DwTransparentBlock extends TransparentBlock {
        private DwTransparentBlock(AbstractBlock.Settings settings) { super(settings); }
    }
    private static final class DwRailBlock extends RailBlock {
        private DwRailBlock(AbstractBlock.Settings settings) { super(settings); }
    }
    private static final class DwPoweredRailBlock extends PoweredRailBlock {
        private DwPoweredRailBlock(AbstractBlock.Settings settings) { super(settings); }
    }
    private static final class DwChainBlock extends ChainBlock {
        private DwChainBlock(AbstractBlock.Settings settings) { super(settings); }
    }
    private static final class DwAmethystClusterBlock extends AmethystClusterBlock {
        private DwAmethystClusterBlock(Identifier id, AbstractBlock.Settings settings) { super(amethystHeight(id), amethystOffset(id), settings); }
        private static int amethystHeight(Identifier id) { return id.getPath().equals("small_amethyst_bud") ? 3 : id.getPath().equals("medium_amethyst_bud") ? 4 : id.getPath().equals("large_amethyst_bud") ? 5 : 7; }
        private static int amethystOffset(Identifier id) { return id.getPath().equals("small_amethyst_bud") ? 4 : id.getPath().equals("medium_amethyst_bud") ? 3 : id.getPath().equals("large_amethyst_bud") ? 3 : 3; }
    }
}
