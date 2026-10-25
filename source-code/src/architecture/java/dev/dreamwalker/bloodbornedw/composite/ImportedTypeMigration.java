package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.architecture.SourceConversionScope;
import dev.dreamwalker.bloodbornedw.runtime.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import java.util.*;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;

/** Loaded-assembly-only canonicalization of reviewed post-V12 types and states.
 * Saved ledger membership is authoritative BEFORE. No removal/event callback fires.
 */
public final class ImportedTypeMigration {
    private static final Map<ServerWorld, State> WORLDS = new WeakHashMap<>();
    private ImportedTypeMigration() {}
    private static final class State {
        final Map<Owner, Map<Cell, Footprint>> before = new HashMap<>();
        final Map<Long, Set<Owner>> chunks = new HashMap<>();
        final Map<Owner,Set<Long>> ownerChunks=new HashMap<>();
        void index(Owner owner,long key){chunks.computeIfAbsent(key,unused->new LinkedHashSet<>()).add(owner);ownerChunks.computeIfAbsent(owner,unused->new HashSet<>()).add(key);}
        State(ServerWorld world) {
            var ledger = CompositeLedger.get(world);
            if(ledger.sharded())return; // Incremental exact root-shard membership below.
            for (Cell cell : ledger.cells()) for (var entry : ledger.at(cell))
                if (ImportedTypeAliases.merged(entry.owner().registryId())
                        && (!ImportedTypeAliases.canonicalRegistry(entry.owner().registryId()).equals(entry.owner().registryId())
                        || CompositeData.nbt(entry.rootData().blockEntityNbt()).getInt(ImportedTypeAliases.VERSION_KEY) < ImportedTypeAliases.VERSION)) {
                    before.computeIfAbsent(entry.owner(), unused -> new TreeMap<>()).put(cell.subtract(entry.owner().root()), entry.shape());
                    index(entry.owner(),chunk(cell));
                    index(entry.owner(),chunk(entry.owner().root()));
                }
        }
        void done(Owner owner) {before.remove(owner);var keys=ownerChunks.remove(owner);if(keys!=null)for(long key:keys){var owners=chunks.get(key);if(owners!=null){owners.remove(owner);if(owners.isEmpty())chunks.remove(key);}}}
    }
    private static long chunk(Cell cell) { return ChunkPos.toLong(cell.x() >> 4, cell.z() >> 4); }
    private static boolean loaded(ServerWorld world, Cell cell) {
        return world.getChunkManager().getWorldChunk(cell.x() >> 4, cell.z() >> 4) != null;
    }
    private static State state(ServerWorld world) { return WORLDS.computeIfAbsent(world, State::new); }
    public static boolean pending(ServerWorld world, Owner owner) {
        return ImportedTypeAliases.merged(owner.registryId()) && state(world).before.containsKey(owner);
    }
    public static void forget(ServerWorld world) { WORLDS.remove(world); }
    /** Opt-in shards publish complete saved BEFORE membership, never the partial
     * collection of cells from just the chunk that happened to load first. */
    public static void shardLoaded(ServerWorld world,Map<Cell,List<CompositeData.Contribution>> cells) {
        if(!CompositeLedger.get(world).sharded())return;Map<Owner,CompositeData.Contribution> candidates=new HashMap<>();
        for(var entries:cells.values())for(var entry:entries)if(ImportedTypeAliases.merged(entry.owner().registryId())
            &&(!ImportedTypeAliases.canonicalRegistry(entry.owner().registryId()).equals(entry.owner().registryId())
                ||CompositeData.nbt(entry.rootData().blockEntityNbt()).getInt(ImportedTypeAliases.VERSION_KEY)<ImportedTypeAliases.VERSION))candidates.put(entry.owner(),entry);
        var state=state(world);
        for(var entry:CompositeLedger.get(world).savedShardMemberships(candidates.keySet()).entrySet()) {
            if(entry.getValue().equals(state.before.get(entry.getKey())))continue;
            state.before.put(entry.getKey(),new TreeMap<>(entry.getValue()));
            var keys=new HashSet<Long>();keys.add(chunk(entry.getKey().root()));
            for(var offset:entry.getValue().keySet())keys.add(chunk(entry.getKey().root().add(offset)));
            for(long key:keys)state.index(entry.getKey(),key);
        }
    }
    public static void shardUnloaded(ServerWorld world,long unloaded) {
        var state=WORLDS.get(world);if(state==null)return;
        for(var owner:List.copyOf(state.chunks.getOrDefault(unloaded,Set.of()))) {
            var mask=state.before.get(owner);boolean attached=mask!=null&&mask.keySet().stream().map(owner.root()::add).anyMatch(cell->chunk(cell)!=unloaded&&loaded(world,cell));
            if(!attached)state.done(owner);
        }
    }
    /** Deferred server mutation queue only, after the loading chunk's FULL future. */
    public static void loadedChunk(ServerWorld world, ChunkPos chunk) {
        State state = state(world);
        for (Owner owner : List.copyOf(state.chunks.getOrDefault(chunk.toLong(), Set.of()))) rebuild(world, state, owner);
    }
    /** Explicit root retry for a helper-first assembly or focused compatibility test. */
    public static void loadedRoot(ServerWorld world, Owner owner) { rebuild(world, state(world), owner); }

    private static void rebuild(ServerWorld world, State state, Owner owner) {
        Map<Cell, Footprint> before = state.before.get(owner);
        if (before == null || !loaded(world, owner.root())) return;
        var root = CompositeData.pos(owner.root());
        var loaded = world.getChunkManager().getWorldChunk(root.getX() >> 4, root.getZ() >> 4);
        if (!(loaded.getBlockEntity(root) instanceof CompositeBlockEntity cached) || !owner.equals(cached.resident())) {
            state.done(owner); ImportedCollisionMigration.discardOwner(world, owner); return;
        }
        BlockState current = loaded.getBlockState(root);
        if (!(current.getBlock() instanceof CompositeRootBlock block) || !block.spec.id.toString().equals(owner.registryId())) {
            state.done(owner); ImportedCollisionMigration.discardOwner(world, owner); return;
        }
        var previousPayload = cached.payload();
        if (!ImportedTypeAliases.needsMigration(current, previousPayload)) { state.done(owner); return; }
        BlockState next = ImportedTypeAliases.target(current);
        var payload = ImportedTypeAliases.transformPayload(current, previousPayload);
        ObjectInstance after = CompositeRuntime.instance(world, root, next, owner.instanceId(), payload);
        TreeSet<Cell> required = new TreeSet<>();
        before.keySet().forEach(offset -> required.add(owner.root().add(offset)));
        after.cells().keySet().forEach(offset -> required.add(owner.root().add(offset)));
        var ledger = CompositeLedger.get(world);
        for (Cell cell : List.copyOf(required)) for (var entry : ledger.at(cell)) required.add(entry.owner().root());
        boolean deferred = false;
        for (Cell cell : required) if (!loaded(world, cell)) {
            state.chunks.computeIfAbsent(chunk(cell), unused -> new LinkedHashSet<>()).add(owner); deferred = true;
        }
        if (deferred) return;
        ObjectInstance saved = new ObjectInstance(owner, CompositeData.data(current, previousPayload), before);
        var access = new FabricCompositeWorld(world, Map.of(after.owner(), after)) {
            @Override public boolean intersectsEntities(Cell cell, Footprint proposed) { return false; }
            @Override public Optional<ObjectInstance> describeRoot(Owner candidate, CellSnapshot snapshot) {
                if (candidate.equals(owner) && candidate.equals(snapshot.resident())) return Optional.of(saved);
                Map<Cell, Footprint> otherSaved = state.before.get(candidate);
                if (otherSaved != null && candidate.equals(snapshot.resident()))
                    return Optional.of(new ObjectInstance(candidate, snapshot.data(), otherSaved));
                return super.describeRoot(candidate, snapshot);
            }
        };
        var result = SourceConversionScope.initialInstances(Set.of(owner.instanceId()), () ->
                CompositeRuntime.execute(access, CompositeRuntime.core().migrateAlias(access, owner, after, List.of())));
        if (result.outcome() == TransactionCore.Outcome.COMMITTED) {
            state.done(owner); ImportedCollisionMigration.discardOwner(world, owner);
        } else dev.dreamwalker.bloodbornedw.DreamwalkerBb.LOG.warn("Imported type migration deferred for " + owner.registryId()
                + " " + owner.instanceId() + ": " + result.reason());
    }
}
