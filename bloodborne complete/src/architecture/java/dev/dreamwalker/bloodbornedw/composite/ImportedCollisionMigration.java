package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.architecture.SourceConversionScope;
import dev.dreamwalker.bloodbornedw.runtime.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import java.util.*;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;

/** Rebuild saved post-V12 physical defaults atomically, without changing art,
 * UUID, state or typed payload. Saved membership is the transaction's BEFORE;
 * using today's descriptor as BEFORE would leave stale cells or invent ownership.
 * The saved ledger is indexed once per world, never scanned once per object/tick.
 */
public final class ImportedCollisionMigration {
    private static final Map<ServerWorld, State> WORLDS = new WeakHashMap<>();
    private static final Set<String> RETIRED = Set.of("bloodborne_dw:source_jungle_trapdoor_bottom", "bloodborne_dw:source_jungle_trapdoor_open");
    private ImportedCollisionMigration() {}

    private static boolean imported(Owner owner) {
        return owner.registryId().startsWith("bloodborne_dw:source_");
    }
    private static final class State {
        final Map<Owner, Map<Cell, Footprint>> before = new HashMap<>();
        final Map<Long, Set<Owner>> chunks = new HashMap<>();
        State(ServerWorld world) {
            var ledger = CompositeLedger.get(world);
            if(ledger.sharded())return; // Incremental exact root-shard membership below.
            for (Cell cell : ledger.cells()) for (var entry : ledger.at(cell)) if (imported(entry.owner())) {
                before.computeIfAbsent(entry.owner(), unused -> new TreeMap<>()).put(cell.subtract(entry.owner().root()), entry.shape());
                chunks.computeIfAbsent(chunk(cell), unused -> new HashSet<>()).add(entry.owner());
            }
        }
        void done(Owner owner) { before.remove(owner); }
    }
    private static long chunk(Cell cell) { return ChunkPos.toLong(cell.x() >> 4, cell.z() >> 4); }
    private static boolean loaded(ServerWorld world, Cell cell) {
        return world.getChunkManager().getWorldChunk(cell.x() >> 4, cell.z() >> 4) != null;
    }
    private static State state(ServerWorld world) { return WORLDS.computeIfAbsent(world, State::new); }

    /** Called only from the deferred server mutation queue after CHUNK_LOAD. */
    public static void loadedChunk(ServerWorld world, ChunkPos chunk) {
        State state = state(world);
        for (Owner owner : List.copyOf(state.chunks.getOrDefault(chunk.toLong(), Set.of()))) rebuild(world, state, owner);
    }
    /** Avoid pruning old-only membership while the migration legitimately waits
     * for its complete old/new assembly and foreign-owner roots to be loaded. */
    public static boolean pending(ServerWorld world, Owner owner) {
        // Already queued legacy cleanup closures may still hold a contribution
        // after retirement removed its ledger entry. Never decode these two
        // deleted registry states through AIR's unrelated property schema.
        return ImportedTypeMigration.pending(world, owner) || RETIRED.contains(owner.registryId()) || imported(owner) && state(world).before.containsKey(owner);
    }
    public static void forget(ServerWorld world) { WORLDS.remove(world); }
    /** Root-shard index retains all old occupied cells for a future author
     * collision replacement, including cells outside the first loaded chunk. */
    public static void shardLoaded(ServerWorld world,Map<Cell,List<CompositeData.Contribution>> cells) {
        if(!CompositeLedger.get(world).sharded())return;var owners=new HashSet<Owner>();
        for(var entries:cells.values())for(var entry:entries)if(imported(entry.owner()))owners.add(entry.owner());
        var state=state(world);
        for(var entry:CompositeLedger.get(world).savedShardMemberships(owners).entrySet()) {
            state.before.put(entry.getKey(),new TreeMap<>(entry.getValue()));
            var keys=new HashSet<Long>();keys.add(chunk(entry.getKey().root()));
            for(var offset:entry.getValue().keySet())keys.add(chunk(entry.getKey().root().add(offset)));
            for(long key:keys)state.chunks.computeIfAbsent(key,ignored->new HashSet<>()).add(entry.getKey());
        }
    }
    public static void shardUnloaded(ServerWorld world,long unloaded) {
        var state=WORLDS.get(world);if(state==null)return;var expired=new HashSet<Owner>();
        for(var owner:state.chunks.getOrDefault(unloaded,Set.of())) {
            var mask=state.before.get(owner);boolean attached=mask!=null&&mask.keySet().stream().map(owner.root()::add).anyMatch(cell->chunk(cell)!=unloaded&&loaded(world,cell));
            if(!attached)expired.add(owner);
        }
        for(var owner:expired)state.before.remove(owner);
        for(var iterator=state.chunks.entrySet().iterator();iterator.hasNext();) {var row=iterator.next();row.getValue().removeAll(expired);if(row.getValue().isEmpty())iterator.remove();}
    }
    /** A committed type migration already rebuilt this owner's complete saved membership. */
    public static void discardOwner(ServerWorld world, Owner owner) {
        State state = WORLDS.get(world); if (state != null) state.done(owner);
    }
    /** NEW saved assemblies consume the authoritative pre-rebake membership,
     * including cells that today's simplified descriptor no longer occupies. */
    public static Optional<Map<Cell,Footprint>> savedMembership(ServerWorld world,Owner owner) {
        State state=WORLDS.get(world);Map<Cell,Footprint> saved=state==null?null:state.before.get(owner);
        return saved==null?Optional.empty():Optional.of(Collections.unmodifiableMap(new TreeMap<>(saved)));
    }

    private static void rebuild(ServerWorld world, State state, Owner owner) {
        if (ImportedTypeMigration.pending(world, owner)) return;
        Map<Cell, Footprint> before = state.before.get(owner);
        if (before != null && RETIRED.contains(owner.registryId())) {
            retireLoaded(world, state, owner, before); return;
        }
        if (before == null || !loaded(world, owner.root())) return;
        BlockPos root = CompositeData.pos(owner.root());
        var rootChunk = world.getChunkManager().getWorldChunk(root.getX() >> 4, root.getZ() >> 4);
        if (!(rootChunk.getBlockEntity(root) instanceof CompositeBlockEntity cached) || !owner.equals(cached.resident())) {
            state.done(owner); return;
        }
        BlockState current = rootChunk.getBlockState(root);
        if (!(current.getBlock() instanceof CompositeRootBlock block) || !block.spec.simplePhysicalCollision || !block.spec.id.toString().equals(owner.registryId())) {
            state.done(owner); return;
        }
        var payload = cached.payload();
        ObjectInstance after = CompositeRuntime.instance(world, root, current, owner.instanceId(), payload);
        if (before.equals(after.cells())) { state.done(owner); return; }
        // All typed art/offset fields are retained; the old cached physical
        // defaults alone are replaced. Essential/root reservations remain native.
        TreeSet<Cell> required = new TreeSet<>();
        before.keySet().forEach(offset -> required.add(owner.root().add(offset)));
        after.cells().keySet().forEach(offset -> required.add(owner.root().add(offset)));
        var ledger = CompositeLedger.get(world);
        for (Cell cell : List.copyOf(required)) for (var entry : ledger.at(cell)) required.add(entry.owner().root());
        boolean deferred = false;
        for (Cell cell : required) if (!loaded(world, cell)) {
            state.chunks.computeIfAbsent(chunk(cell), unused -> new HashSet<>()).add(owner); deferred = true;
        }
        if (deferred) return;
        ObjectInstance saved = new ObjectInstance(owner, CompositeData.data(current, payload), before);
        var access = new FabricCompositeWorld(world, Map.of(owner, after)) {
            // The same UUID/root already existed in this saved world. Rebaking
            // its defaults must finish even if a living body now occupies the
            // newly simplified volume; it neither creates nor moves the object.
            // This privilege exists only in this loaded-BE migration adapter.
            @Override public boolean intersectsEntities(Cell cell, Footprint proposed) { return false; }
            @Override public Optional<ObjectInstance> describeRoot(Owner candidate, CellSnapshot snapshot) {
                if (candidate.equals(owner) && candidate.equals(snapshot.resident())) return Optional.of(saved);
                Map<Cell, Footprint> otherSaved = state.before.get(candidate);
                if (otherSaved != null && candidate.equals(snapshot.resident()))
                    return Optional.of(new ObjectInstance(candidate, snapshot.data(), otherSaved));
                return super.describeRoot(candidate, snapshot);
            }
        };
        // Initial-instance privilege preserves already shared native/owned
        // cells. Entity admission above is separately scoped to this rebake;
        // ordinary placement, rotation and every protected V12 path stay native.
        var result = SourceConversionScope.initialInstances(Set.of(owner.instanceId()), () ->
                CompositeRuntime.execute(access, CompositeRuntime.core().transitionPayload(access, owner, after, List.of())));
        if (result.outcome() == TransactionCore.Outcome.COMMITTED) state.done(owner);
        else dev.dreamwalker.bloodbornedw.DreamwalkerBb.LOG.warn("Imported physical migration deferred for " + owner.registryId() + " " + owner.instanceId() + ": " + result.reason());
    }

    /** Minecraft drops the two unknown root IDs when reading an old save, but
     * their serialized helper/foreign-carrier ledger can outlive those roots.
     * Remove only these explicitly retired owners from currently loaded cells.
     * Every other owner's bindings, physical data and foreign BE remain native.
     */
    private static void retireLoaded(ServerWorld world, State state, Owner owner, Map<Cell, Footprint> before) {
        var access = new FabricCompositeWorld(world, Map.of()) {
            @Override public Optional<ObjectInstance> describeRoot(Owner candidate, CellSnapshot snapshot) {
                Map<Cell, Footprint> saved = state.before.get(candidate);
                if (saved != null && candidate.equals(snapshot.resident()))
                    return Optional.of(new ObjectInstance(candidate, snapshot.data(), saved));
                return super.describeRoot(candidate, snapshot);
            }
        };
        TreeMap<Cell, CellSnapshot> expected = new TreeMap<>(), writes = new TreeMap<>();
        Set<Cell> loadedOffsets = new HashSet<>();
        for (Cell offset : before.keySet()) {
            Cell cell = owner.root().add(offset); if (!loaded(world, cell)) continue;
            CellSnapshot previous = access.read(cell); loadedOffsets.add(offset);
            List<Owner> guests = previous.guests().stream().filter(candidate -> !candidate.equals(owner)).toList();
            CellSnapshot next;
            if (owner.equals(previous.resident())) {
                next = guests.isEmpty() ? new CellSnapshot(CellSnapshot.Kind.AIR, CompositeData.data(net.minecraft.block.Blocks.AIR.getDefaultState(), new net.minecraft.nbt.NbtCompound()), null, List.of())
                        : new CellSnapshot(CellSnapshot.Kind.HELPER, CompositeData.data(CompositeArchitecture.CELL.getDefaultState(), new net.minecraft.nbt.NbtCompound()), null, guests);
            } else if (previous.kind() == CellSnapshot.Kind.HELPER && guests.isEmpty()) {
                next = new CellSnapshot(CellSnapshot.Kind.AIR, CompositeData.data(net.minecraft.block.Blocks.AIR.getDefaultState(), new net.minecraft.nbt.NbtCompound()), null, List.of());
            } else next = previous.withGuests(guests);
            expected.put(cell, previous); if (!next.equals(previous)) writes.put(cell, next);
        }
        if (loadedOffsets.isEmpty()) return;
        var plan = new TransactionCore.Plan(access.dimensionKey(), expected, writes, Map.of());
        var result = CompositeRuntime.execute(access, new TransactionCore.Preparation(plan, null));
        if (result.outcome() == TransactionCore.Outcome.COMMITTED) {
            loadedOffsets.forEach(before::remove); if (before.isEmpty()) state.done(owner);
        } else dev.dreamwalker.bloodbornedw.DreamwalkerBb.LOG.warn("Retired source-owner cleanup deferred: " + owner.registryId() + " " + result.reason());
    }
}
