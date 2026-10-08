package dev.dreamwalker.bloodbornedw.runtime;

import dev.dreamwalker.bloodbornedw.runtime.CellSnapshot.BlockData;
import dev.dreamwalker.bloodbornedw.runtime.CellSnapshot.Kind;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Footprint;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Server-thread transaction core, independent of registries and Minecraft classes.
 * Adapters must suppress neighbor callbacks until every cell has committed.
 */
public final class TransactionCore {
    private final CellSnapshot air;
    private final BlockData helper;
    private final int maxOwners;
    private boolean executing;

    public TransactionCore(CellSnapshot air, BlockData helper, int maxOwners) {
        if (air.kind() != Kind.AIR || maxOwners < 1) throw new IllegalArgumentException("Invalid runtime limits");
        this.air = air; this.helper = Objects.requireNonNull(helper); this.maxOwners = maxOwners;
    }

    public interface WorldAccess {
        void assertMutationThread();
        String dimensionKey();
        boolean isLoaded(Cell cell);
        boolean inBounds(Cell cell);
        /** Must not load a chunk or return a mutable view. */
        CellSnapshot read(Cell cell);
        /** Reconstruct exact catalog geometry from a loaded resident root and its functional state. */
        Optional<ObjectInstance> describeRoot(Owner owner, CellSnapshot root);
        /** Explicit carrier capability. A foreign block entity is never implicitly replaceable. */
        boolean supportsGuestBindings(CellSnapshot carrier);
        /** Only reviewed source intersections may opt into intersecting collision boxes. */
        default boolean allowsReviewedOverlap(ObjectInstance incoming, ObjectInstance existing, Cell cell) { return false; }
        boolean intersectsEntities(Cell cell, Footprint proposed);
        /** Apply full state + BE payload + bindings without callbacks. False may mean a partial write. */
        boolean writeSilently(Cell cell, CellSnapshot next);
        /** Cached shape/art data can change while the carrier's logical snapshot stays identical. */
        default boolean needsRefresh(Cell cell, CellSnapshot before, CellSnapshot next) { return false; }
        /** Restore captured adapter metadata as well as the logical snapshot. */
        default boolean restoreSilently(Cell cell, CellSnapshot previous) { return writeSilently(cell, previous); }
        /** Queue client updates and neighbor notifications after a stable commit. */
        void publishCommitted(List<Cell> changedCells);
    }

    @FunctionalInterface public interface PlacementCheck {
        /** Return a named rejection or null. Reads become immutable preconditions of this plan. */
        String validate(ObjectInstance proposed, ReadView reads);
    }
    @FunctionalInterface public interface ReadView { CellSnapshot require(Cell cell); }

    public record Plan(String dimensionKey, Map<Cell, CellSnapshot> expected, Map<Cell, CellSnapshot> writes,
                       Map<Cell, Footprint> entityChecks) {
        public Plan {
            Objects.requireNonNull(dimensionKey);
            expected = Collections.unmodifiableMap(new TreeMap<>(expected));
            writes = Collections.unmodifiableMap(new TreeMap<>(writes));
            entityChecks = Collections.unmodifiableMap(new TreeMap<>(entityChecks));
            if (!expected.keySet().containsAll(writes.keySet())) throw new IllegalArgumentException("Uncaptured write");
        }
    }
    public record Preparation(Plan plan, String reason) { public boolean accepted() { return plan != null; } }
    public enum Outcome { COMMITTED, REJECTED, ROLLED_BACK, ROLLBACK_FAILED }
    public record Result(Outcome outcome, int changedCells, String reason) {}
    public enum OwnerStatus { LIVE, DEFERRED, STALE }
    public record OwnerInspection(OwnerStatus status, ObjectInstance instance) {}
    public record Validation(List<Owner> live, List<Owner> deferred, List<Owner> stale) {
        public Validation { live = List.copyOf(live); deferred = List.copyOf(deferred); stale = List.copyOf(stale); }
    }

    public Preparation placement(WorldAccess world, ObjectInstance next, List<PlacementCheck> checks) {
        return prepare(world, null, Objects.requireNonNull(next), checks);
    }
    public Preparation transition(WorldAccess world, Owner current, ObjectInstance next, List<PlacementCheck> checks) {
        Objects.requireNonNull(current); Objects.requireNonNull(next);
        if (!current.instanceId().equals(next.owner().instanceId()) || !current.registryId().equals(next.owner().registryId()))
            return new Preparation(null, "transition_changes_object_identity");
        return prepare(world, current, next, checks);
    }
    /** Adapter has copied all prior typed fields and explicitly changed a functional payload field. */
    public Preparation transitionPayload(WorldAccess world, Owner current, ObjectInstance next, List<PlacementCheck> checks) {
        Objects.requireNonNull(current); Objects.requireNonNull(next);
        if (!current.instanceId().equals(next.owner().instanceId()) || !current.registryId().equals(next.owner().registryId()))
            return new Preparation(null, "transition_changes_object_identity");
        return prepare(world,current,next,checks,true);
    }
    public Preparation removal(WorldAccess world, Owner current) { return prepare(world, Objects.requireNonNull(current), null, List.of()); }

    private Preparation prepare(WorldAccess world, Owner current, ObjectInstance next, List<PlacementCheck> checks) {
        return prepare(world,current,next,checks,false);
    }
    private Preparation prepare(WorldAccess world, Owner current, ObjectInstance next, List<PlacementCheck> checks, boolean functionalPayload) {
        world.assertMutationThread();
        if (executing) return new Preparation(null, "mutation_in_progress");
        TreeMap<Cell, CellSnapshot> captured = new TreeMap<>(), writes = new TreeMap<>();
        TreeMap<Cell, Footprint> entityChecks = new TreeMap<>();
        ReadView reads = cell -> capture(world, captured, cell);
        try {
            ObjectInstance before = null;
            if (current != null) {
                CellSnapshot root = reads.require(current.root());
                before = exactOwner(world, current, root).orElseThrow(() -> rejected("stale_current_owner"));
            }
            Set<Cell> affected = new TreeSet<>();
            if (before != null) before.cells().keySet().forEach(offset -> affected.add(current.root().add(offset)));
            if (next != null) next.cells().keySet().forEach(offset -> affected.add(next.owner().root().add(offset)));
            for (Cell cell : affected) {
                CellSnapshot snapshot = reads.require(cell);
                validateBindings(world, reads, cell, snapshot, current);
                if (before != null && before.at(cell) != null) {
                    boolean owned = cell.equals(current.root()) ? current.equals(snapshot.resident()) : snapshot.guests().contains(current);
                    if (!owned) throw rejected("missing_owned_cell:" + cell);
                }
                writes.put(cell, detach(snapshot, current));
            }
            if (next != null) {
                // Functional rotations retain existing root BE bytes; ownership is separate metadata.
                BlockData nextData = next.rootData();
                if (before != null && !functionalPayload) nextData = nextData.withNbt(reads.require(current.root()).data().blockEntityNbt());
                for (var entry : next.cells().entrySet()) {
                    Cell cell = next.owner().root().add(entry.getKey());
                    CellSnapshot carrier = writes.get(cell);
                    if (cell.equals(next.owner().root())) {
                        if (carrier.kind() != Kind.AIR && carrier.kind() != Kind.HELPER) throw rejected("occupied_root:" + cell);
                        if (!carrier.guests().isEmpty() && !world.supportsGuestBindings(carrier)) throw rejected("unsupported_shared_root:" + cell);
                        carrier = new CellSnapshot(Kind.ROOT, nextData, next.owner(), carrier.guests());
                    } else {
                        if (carrier.kind() == Kind.AIR) carrier = new CellSnapshot(Kind.HELPER, helper, null, List.of());
                        if (!world.supportsGuestBindings(carrier)) throw rejected("foreign_occupied_cell:" + cell);
                        List<Owner> bindings = new ArrayList<>(carrier.guests());
                        bindings.add(next.owner());
                        carrier = carrier.withGuests(bindings);
                    }
                    int owners = carrier.guests().size() + (carrier.resident() == null ? 0 : 1);
                    if (owners > maxOwners) throw rejected("shared_owner_limit:" + cell);
                    for (Owner guest : owners(carrier)) {
                        if (guest.equals(next.owner())) continue;
                        ObjectInstance existing = exactOwner(world, guest, reads.require(guest.root()))
                                .orElseThrow(() -> rejected("stale_guest_owner:" + cell));
                        Footprint existingFootprint = existing.at(cell);
                        if (existingFootprint == null) throw rejected("guest_does_not_own_cell:" + cell);
                        if (entry.getValue().collides(existingFootprint) && !world.allowsReviewedOverlap(next, existing, cell))
                            throw rejected("collision_with_owned_object:" + cell);
                    }
                    Footprint previous = before == null ? null : before.at(cell);
                    boolean physicalChanged = previous == null || !previous.collision().equals(entry.getValue().collision());
                    if (physicalChanged && !entry.getValue().collision().isEmpty() && world.intersectsEntities(cell, entry.getValue()))
                        throw rejected("entity_obstruction:" + cell);
                    if (physicalChanged && !entry.getValue().collision().isEmpty()) entityChecks.put(cell, entry.getValue());
                    writes.put(cell, carrier);
                }
                for (PlacementCheck check : List.copyOf(checks)) {
                    String reason = check.validate(next, reads);
                    if (reason != null) throw rejected("support_or_policy:" + reason);
                }
            }
            writes.entrySet().removeIf(entry -> captured.get(entry.getKey()).equals(entry.getValue())
                    && !world.needsRefresh(entry.getKey(),captured.get(entry.getKey()),entry.getValue()));
            return new Preparation(new Plan(world.dimensionKey(), captured, writes, entityChecks), null);
        } catch (Rejected failure) {
            return new Preparation(null, failure.getMessage());
        }
    }

    private CellSnapshot detach(CellSnapshot snapshot, Owner current) {
        if (current == null) return snapshot;
        List<Owner> guests = snapshot.guests().stream().filter(owner -> !owner.equals(current)).toList();
        if (current.equals(snapshot.resident())) return guests.isEmpty() ? air : new CellSnapshot(Kind.HELPER, helper, null, guests);
        if (snapshot.kind() == Kind.HELPER && guests.isEmpty()) return air;
        return snapshot.withGuests(guests);
    }

    private static CellSnapshot capture(WorldAccess world, Map<Cell, CellSnapshot> snapshots, Cell cell) {
        if (!world.isLoaded(cell)) throw rejected("unloaded_cell:" + cell);
        if (!world.inBounds(cell)) throw rejected("outside_build_bounds:" + cell);
        return snapshots.computeIfAbsent(cell, key -> Objects.requireNonNull(world.read(key)));
    }
    private static void validateBindings(WorldAccess world, ReadView reads, Cell cell, CellSnapshot carrier, Owner ignored) {
        for (Owner owner : owners(carrier)) {
            if (owner.equals(ignored)) continue;
            if (!world.isLoaded(owner.root())) throw rejected("unloaded_guest_owner:" + owner.root());
            ObjectInstance instance = exactOwner(world, owner, reads.require(owner.root()))
                    .orElseThrow(() -> rejected("stale_guest_owner:" + cell));
            if (instance.at(cell) == null || (carrier.guests().contains(owner) && cell.equals(owner.root())))
                throw rejected("invalid_guest_membership:" + cell);
        }
    }
    static List<Owner> owners(CellSnapshot snapshot) {
        List<Owner> owners = new ArrayList<>(snapshot.guests());
        if (snapshot.resident() != null) owners.add(snapshot.resident());
        owners.sort(Owner::compareTo);
        return List.copyOf(owners);
    }
    private static Optional<ObjectInstance> exactOwner(WorldAccess world, Owner owner, CellSnapshot root) {
        if (!owner.equals(root.resident())) return Optional.empty();
        return world.describeRoot(owner, root).filter(instance -> instance.owner().equals(owner));
    }

    /** Deferred roots are distinct from stale roots, including after a helper-first chunk load. */
    public static OwnerInspection inspectOwner(WorldAccess world, Owner owner, Cell claimedCell) {
        if (!world.isLoaded(owner.root())) return new OwnerInspection(OwnerStatus.DEFERRED, null);
        Optional<ObjectInstance> instance = exactOwner(world, owner, world.read(owner.root()));
        if (instance.isEmpty() || instance.get().at(claimedCell) == null)
            return new OwnerInspection(OwnerStatus.STALE, null);
        return new OwnerInspection(OwnerStatus.LIVE, instance.get());
    }
    public static Validation validateCarrier(WorldAccess world, Cell cell) {
        if (!world.isLoaded(cell)) throw new IllegalArgumentException("Carrier is unloaded");
        List<Owner> live = new ArrayList<>(), deferred = new ArrayList<>(), stale = new ArrayList<>();
        for (Owner owner : world.read(cell).guests()) {
            switch (inspectOwner(world, owner, cell).status()) {
                case LIVE -> live.add(owner);
                case DEFERRED -> deferred.add(owner);
                case STALE -> stale.add(owner);
            }
        }
        return new Validation(live, deferred, stale);
    }

    /** Pruning only proven stale links preserves every unloaded owner's binding and BE payload. */
    public Preparation pruneStale(WorldAccess world, Cell cell) {
        world.assertMutationThread();
        TreeMap<Cell, CellSnapshot> expected = new TreeMap<>();
        try {
            CellSnapshot snapshot = capture(world, expected, cell);
            Set<Owner> stale = new LinkedHashSet<>();
            for (Owner owner : snapshot.guests()) {
                if (!world.isLoaded(owner.root())) continue;
                capture(world, expected, owner.root());
                if (inspectOwner(world, owner, cell).status() == OwnerStatus.STALE) stale.add(owner);
            }
            List<Owner> retained = snapshot.guests().stream().filter(owner -> !stale.contains(owner)).toList();
            CellSnapshot next = snapshot.kind() == Kind.HELPER && retained.isEmpty() ? air : snapshot.withGuests(retained);
            Map<Cell, CellSnapshot> writes = next.equals(snapshot) ? Map.of() : Map.of(cell, next);
            return new Preparation(new Plan(world.dimensionKey(), expected, writes, Map.of()), null);
        } catch (Rejected failure) { return new Preparation(null, failure.getMessage()); }
    }

    /** Revalidate every captured state, then restore all attempted cells in reverse order on failure. */
    public Result execute(WorldAccess world, Preparation prepared) {
        world.assertMutationThread();
        if (executing) return new Result(Outcome.REJECTED, 0, "mutation_in_progress");
        if (!prepared.accepted()) return new Result(Outcome.REJECTED, 0, prepared.reason());
        Plan plan = prepared.plan();
        if (!plan.dimensionKey().equals(world.dimensionKey())) return new Result(Outcome.REJECTED, 0, "wrong_dimension");
        for (var expected : plan.expected().entrySet()) {
            if (!world.isLoaded(expected.getKey())) return new Result(Outcome.REJECTED, 0, "unloaded_since_preflight:" + expected.getKey());
            if (!world.inBounds(expected.getKey())) return new Result(Outcome.REJECTED, 0, "bounds_changed_since_preflight:" + expected.getKey());
            if (!expected.getValue().equals(world.read(expected.getKey())))
                return new Result(Outcome.REJECTED, 0, "world_changed_since_preflight:" + expected.getKey());
        }
        for (var check : plan.entityChecks().entrySet())
            if (world.intersectsEntities(check.getKey(), check.getValue())) return new Result(Outcome.REJECTED, 0, "entity_obstruction_since_preflight:" + check.getKey());
        List<Cell> attempted = new ArrayList<>();
        executing = true;
        try {
            for (var write : plan.writes().entrySet()) {
                attempted.add(write.getKey());
                if (!world.writeSilently(write.getKey(), write.getValue())) throw new IllegalStateException("write_rejected:" + write.getKey());
                if (!write.getValue().equals(world.read(write.getKey()))) throw new IllegalStateException("write_not_exact:" + write.getKey());
            }
            for (var write : plan.writes().entrySet())
                if (!write.getValue().equals(world.read(write.getKey()))) throw new IllegalStateException("commit_not_exact:" + write.getKey());
            if (!attempted.isEmpty()) world.publishCommitted(List.copyOf(attempted));
            return new Result(Outcome.COMMITTED, attempted.size(), null);
        } catch (RuntimeException failure) {
            List<String> restorationFailures = new ArrayList<>();
            for (int index = attempted.size() - 1; index >= 0; index--) {
                Cell cell = attempted.get(index);
                try {
                    CellSnapshot previous = plan.expected().get(cell);
                    if (!world.restoreSilently(cell, previous) || !previous.equals(world.read(cell))) restorationFailures.add(cell.toString());
                } catch (RuntimeException rollbackFailure) { restorationFailures.add(cell + ":" + rollbackFailure.getClass().getSimpleName()); }
            }
            if (!attempted.isEmpty()) {
                try { world.publishCommitted(List.copyOf(attempted)); }
                catch (RuntimeException notificationFailure) { restorationFailures.add("rollback_notifications:" + notificationFailure.getClass().getSimpleName()); }
            }
            return new Result(restorationFailures.isEmpty() ? Outcome.ROLLED_BACK : Outcome.ROLLBACK_FAILED, 0,
                    failure.getMessage() + (restorationFailures.isEmpty() ? "" : ";restore_failed=" + restorationFailures));
        } finally { executing = false; }
    }
    private static Rejected rejected(String message) { return new Rejected(message); }
    private static final class Rejected extends RuntimeException { private Rejected(String message) { super(message); } }
}
