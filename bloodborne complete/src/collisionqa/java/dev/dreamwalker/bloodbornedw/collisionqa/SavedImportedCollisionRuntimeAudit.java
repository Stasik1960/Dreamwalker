package dev.dreamwalker.bloodbornedw.collisionqa;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.item.*;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;

/** Actual v12.1 saved gallery copy; never the user's original v16 map. */
final class SavedImportedCollisionRuntimeAudit {
    private record Entry(String registry, BlockPos root, Owner owner, BlockState state, NbtCompound payload,
                         Map<Cell, Footprint> before, NbtCompound fullBefore, boolean legacy) {}
    private record Foreign(BlockState state, NbtCompound fullNbt) {}
    final ServerWorld world;
    final List<Entry> entries = new ArrayList<>();
    final Map<Owner, Integer> retiredBefore = new LinkedHashMap<>();
    final Map<BlockPos, Foreign> foreignBefore = new LinkedHashMap<>();
    ArmorStandEntity occupiedWitness;
    final JsonObject result = new JsonObject();
    int ticks;
    boolean finished;
    SavedImportedCollisionRuntimeAudit(ServerWorld world, Path manifestPath) throws IOException {
        this.world = world;
        JsonObject manifest = JsonParser.parseString(Files.readString(manifestPath)).getAsJsonObject();
        require(manifest.get("productionJarSha256").getAsString().equals("9797fb4bb4b6c4103b60b994fb0c4ad4f88c46bc7a19de42eb596e7f0c57e117"), "Saved migration fixture must be the exact earlier v12.1 gallery");
        result.addProperty("schema", "dreamwalker-v122-saved-new-collision-migration-v1"); result.addProperty("status", "RUNNING");
        result.addProperty("source", "Independent byte-copied prior v12.1 gallery world, not original v16 map");
        Set<String> paths = new LinkedHashSet<>(List.of("prototype_roof", "prototype_tree", "prototype_ladder", "prototype_double_door", "source_bricks", "source_potted_flowering_azalea_bush", "source_hold_ladder_01", "source_hold_ladder_02", "source_hold_ladder_03", "source_deepslate_iron_ore", "source_acacia_fence_gate", "source_jungle_trapdoor_bottom", "source_jungle_trapdoor_open"));
        for (JsonElement element : manifest.getAsJsonArray("exhibits")) {
            JsonObject row = element.getAsJsonObject(); String registry = row.has("registry") ? row.get("registry").getAsString() : "";
            boolean retiredRegistry = Set.of("bloodborne_dw:source_jungle_trapdoor_bottom", "bloodborne_dw:source_jungle_trapdoor_open").contains(registry);
            if (!registry.startsWith("bloodborne_dw:")) continue;
            boolean selected = paths.remove(registry.substring("bloodborne_dw:".length()));
            if (!selected && !retiredRegistry) continue;
            JsonArray p = row.getAsJsonArray("root"); BlockPos root = new BlockPos(p.get(0).getAsInt(), p.get(1).getAsInt(), p.get(2).getAsInt());
            world.setChunkForced(root.getX() >> 4, root.getZ() >> 4, true); world.getChunk(root);
            if (retiredRegistry) {
                Owner retired = new Owner(UUID.fromString(row.get("uuid").getAsString()), registry, CompositeData.cell(root));
                int bindings = 0;
                for (Cell cell : CompositeLedger.get(world).cells()) for (var contribution : CompositeLedger.get(world).at(cell)) if (contribution.owner().equals(retired)) {
                    bindings++; BlockPos position = CompositeData.pos(cell); world.setChunkForced(position.getX() >> 4, position.getZ() >> 4, true); world.getChunk(position);
                }
                require(bindings > 0, "Old red fixture has no saved bindings to clean " + registry); retiredBefore.put(retired, bindings); continue;
            }
            require(world.getBlockEntity(root) instanceof CompositeBlockEntity, "Old saved fixture root has no typed owner " + registry);
            var entity = (CompositeBlockEntity) world.getBlockEntity(root); Owner owner = entity.resident();
            require(owner != null && owner.instanceId().toString().equals(row.get("uuid").getAsString()), "Saved UUID differs from old manifest " + registry);
            BlockState state = world.getBlockState(root); boolean legacy = !registry.startsWith("bloodborne_dw:source_");
            Map<Cell, Footprint> before = new TreeMap<>();
            var ledger = CompositeLedger.get(world);
            for (Cell cell : ledger.cells()) for (var entry : ledger.at(cell)) if (entry.owner().equals(owner)) before.put(cell.subtract(owner.root()), entry.shape());
            require(legacy || before.containsKey(Cell.ORIGIN), "Old ledger has no NEW source root membership " + registry);
            var current = CompositeRuntime.instance(world, root, state, owner.instanceId(), entity.payload());
            Set<Cell> required = new TreeSet<>(before.keySet()); required.addAll(current.cells().keySet());
            for (Cell cell : required) {
                BlockPos position = root.add(cell.x(), cell.y(), cell.z());
                world.setChunkForced(position.getX() >> 4, position.getZ() >> 4, true); world.getChunk(position);
            }
            entries.add(new Entry(registry, root, owner, state, entity.payload(), before, entity.createNbt(), legacy));
        }
        require(paths.isEmpty(), "Saved fixture did not contain requested roots " + paths);
        require(retiredBefore.size() == 3, "Saved fixture must cover both retired registries and all three offered red variants");
        // A real vanilla inventory sharing a saved NEW object's old helper cell
        // exercises migration's foreign-carrier preservation on this QA copy.
        Entry witness = entries.stream().filter(e -> !e.legacy && e.before.keySet().stream().anyMatch(c -> !c.equals(Cell.ORIGIN))).findFirst().orElseThrow();
        Cell offset = witness.before.keySet().stream().filter(c -> !c.equals(Cell.ORIGIN)).findFirst().orElseThrow();
        BlockPos chestPos = witness.root.add(offset.x(), offset.y(), offset.z());
        world.setBlockState(chestPos, Blocks.CHEST.getDefaultState(), Block.FORCE_STATE | Block.SKIP_DROPS);
        ((ChestBlockEntity) world.getBlockEntity(chestPos)).setStack(0, new ItemStack(Items.DIAMOND, 3));
        result.addProperty("foreignInventoryWitness", "QA-copy-only vanilla chest sharing a saved NEW owner cell");
        for (Entry e : entries) if (!e.legacy) for (Cell c : e.before.keySet()) {
            BlockPos pos = e.root.add(c.x(), c.y(), c.z()); BlockState s = world.getBlockState(pos);
            if (s.isAir() || s.getBlock() instanceof CompositeRootBlock || s.isOf(CompositeArchitecture.CELL)) continue;
            var be = world.getBlockEntity(pos); foreignBefore.putIfAbsent(pos, new Foreign(s, be == null ? new NbtCompound() : be.createNbt()));
        }
        Entry occupied = entries.stream().filter(e -> !e.legacy).filter(e -> {
            var now = CompositeRuntime.instance(world, e.root, e.state, e.owner.instanceId(), e.payload);
            return now.cells().entrySet().stream().anyMatch(c -> !c.getValue().collision().isEmpty() && (e.before.get(c.getKey()) == null || !c.getValue().collision().equals(e.before.get(c.getKey()).collision())));
        }).findFirst().orElseThrow();
        var occupiedNow = CompositeRuntime.instance(world, occupied.root, occupied.state, occupied.owner.instanceId(), occupied.payload);
        Cell occupiedOffset = occupiedNow.cells().entrySet().stream().filter(c -> !c.getValue().collision().isEmpty() && (occupied.before.get(c.getKey()) == null || !c.getValue().collision().equals(occupied.before.get(c.getKey()).collision()))).map(Map.Entry::getKey).findFirst().orElseThrow();
        Cell occupiedCell = occupied.owner.root().add(occupiedOffset);
        var physical = occupiedNow.cells().get(occupiedOffset).collision().get(0);
        occupiedWitness = new ArmorStandEntity(EntityType.ARMOR_STAND, world); occupiedWitness.setNoGravity(true); occupiedWitness.setInvulnerable(true);
        occupiedWitness.refreshPositionAndAngles(occupiedCell.x() + (physical.minX() + physical.maxX()) / 2, occupiedCell.y() + physical.minY() + .001, occupiedCell.z() + (physical.minZ() + physical.maxZ()) / 2, 0, 0);
        require(world.spawnEntity(occupiedWitness), "Cannot add actual living entity witness to saved NEW root");
        result.addProperty("occupiedNewRootWitness", "Actual living entity retained inside NEW replacement volume on isolated QA copy");
        result.addProperty("occupiedRegistry", occupied.registry); result.addProperty("occupiedCell", occupiedCell.toString());
        // Reproduce the ordinary adapter's refusal on the actual living body;
        // the saved-only rebake must admit this already existing installation.
        var beforeInstance = new dev.dreamwalker.bloodbornedw.runtime.ObjectInstance(occupied.owner, CompositeData.data(occupied.state, occupied.payload), occupied.before);
        var ordinary = new FabricCompositeWorld(world, Map.of(occupied.owner, occupiedNow)) {
            @Override public Optional<dev.dreamwalker.bloodbornedw.runtime.ObjectInstance> describeRoot(Owner owner, CellSnapshot snapshot) {
                if (owner.equals(occupied.owner) && owner.equals(snapshot.resident())) return Optional.of(beforeInstance);
                return super.describeRoot(owner, snapshot);
            }
        };
        var denial = dev.dreamwalker.bloodbornedw.architecture.SourceConversionScope.initialInstances(Set.of(occupied.owner.instanceId()), () -> CompositeRuntime.core().transitionPayload(ordinary, occupied.owner, occupiedNow, List.of()));
        result.addProperty("ordinaryOccupiedRebakeRefusal", denial.reason());
        result.addProperty("witnessIntersectionChecked", occupiedWitness.intersectionChecked);
        result.addProperty("witnessIsCollidable", occupiedWitness.isCollidable());
        result.addProperty("ordinaryAdapterEntityIntersection", ordinary.intersectsEntities(occupiedCell, occupiedNow.cells().get(occupiedOffset)));
        result.addProperty("selectedSavedObjects", entries.size()); write();
        require(denial.reason() != null && denial.reason().startsWith("entity_obstruction:"), "Actual occupied migration preflight did not reproduce ordinary entity rejection: " + denial.reason());
    }
    void tick() {
        if (++ticks < 30) return;
        JsonArray cases = new JsonArray(); int changed = 0, legacy = 0;
        for (Entry fixture : entries) {
            require(world.getBlockState(fixture.root).equals(fixture.state), "Saved root state changed " + fixture.registry);
            var entity = (CompositeBlockEntity) world.getBlockEntity(fixture.root);
            require(fixture.owner.equals(entity.resident()) && fixture.payload.equals(entity.payload()), "Migration changed UUID or typed payload " + fixture.registry);
            Map<Cell, Footprint> actual = new TreeMap<>();
            var ledger = CompositeLedger.get(world);
            for (Cell cell : ledger.cells()) for (var entry : ledger.at(cell)) if (entry.owner().equals(fixture.owner)) actual.put(cell.subtract(fixture.owner.root()), entry.shape());
            JsonObject row = new JsonObject(); row.addProperty("registryId", fixture.registry); row.addProperty("uuid", fixture.owner.instanceId().toString());
            if (fixture.legacy) {
                require(fixture.before.equals(actual) && fixture.fullBefore.equals(entity.createNbt()), "V12 owner, shape or NBT changed " + fixture.registry);
                legacy++; row.addProperty("status", "PASS_V12_TYPED_NBT_EXACT");
            } else {
                var expected = CompositeRuntime.instance(world, fixture.root, fixture.state, fixture.owner.instanceId(), fixture.payload);
                require(expected.cells().equals(actual), "Old saved NEW object still uses old physical shapes or stale membership " + fixture.registry);
                boolean differs = !fixture.before.equals(actual); if (differs) changed++;
                row.addProperty("status", differs ? "PASS_SAVED_NEW_REBAKED" : "PASS_ALREADY_SIMPLE_CONTROL");
                row.addProperty("beforePhysicalParts", fixture.before.values().stream().mapToInt(f -> f.collision().size()).sum());
                row.addProperty("afterPhysicalParts", actual.values().stream().mapToInt(f -> f.collision().size()).sum());
            }
            cases.add(row);
        }
        require(changed >= 4 && legacy == 4, "Insufficient actual old saved shape migrations or V12 controls");
        for (var entry : retiredBefore.entrySet()) {
            for (Cell cell : CompositeLedger.get(world).cells()) require(CompositeLedger.get(world).at(cell).stream().noneMatch(c -> c.owner().equals(entry.getKey())), "Retired red owner left invisible saved collision bindings " + entry.getKey().registryId());
            require(world.getBlockState(CompositeData.pos(entry.getKey().root())).isAir(), "Retired red root was restored");
            JsonObject row = new JsonObject(); row.addProperty("registryId", entry.getKey().registryId()); row.addProperty("beforeBindings", entry.getValue()); row.addProperty("afterBindings", 0); row.addProperty("status", "PASS_RETIRED_ROOT_AND_CACHED_PHYSICS_REMOVED"); cases.add(row);
        }
        for (var entry : foreignBefore.entrySet()) {
            require(world.getBlockState(entry.getKey()).equals(entry.getValue().state), "Migration changed foreign carrier state " + entry.getKey());
            var be = world.getBlockEntity(entry.getKey()); NbtCompound actual = be == null ? new NbtCompound() : be.createNbt();
            require(actual.equals(entry.getValue().fullNbt), "Migration changed foreign inventory/NBT " + entry.getKey());
        }
        result.addProperty("unchangedForeignCarriers", foreignBefore.size());
        result.addProperty("foreignInventoryAndCarrierNbt", "PASS_ACTUAL_VANILLA_INVENTORY_AND_NATIVE_STATES_EXACT");
        require(occupiedWitness.isAlive() && world.getEntity(occupiedWitness.getUuid()) == occupiedWitness, "Migration destroyed or replaced existing living entity");
        result.addProperty("existingLivingEntityRetained", "PASS");
        result.addProperty("status", "PASS"); result.addProperty("changedSavedNewObjects", changed); result.addProperty("unchangedV12Controls", legacy); result.add("cases", cases);
        world.getServer().save(false, true, true); finished = true; write();
    }
    void fail(Throwable failure) { finished = true; result.addProperty("status", "FAIL"); result.addProperty("failure", failure.toString()); StringWriter s = new StringWriter(); failure.printStackTrace(new PrintWriter(s)); result.addProperty("trace", s.toString()); write(); }
    void write() { try { Files.writeString(Path.of("collision-saved-migration-audit.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n"); } catch (IOException failure) { throw new UncheckedIOException(failure); } }
    static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
