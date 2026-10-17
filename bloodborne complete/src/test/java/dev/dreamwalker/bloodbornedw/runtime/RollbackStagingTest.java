package dev.dreamwalker.bloodbornedw.runtime;

import dev.dreamwalker.bloodbornedw.runtime.CellSnapshot.BlockData;
import dev.dreamwalker.bloodbornedw.runtime.CellSnapshot.Kind;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Footprint;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore.Outcome;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Checks rollback staging independently of Minecraft serialization and registries. */
public final class RollbackStagingTest {
    private static final CellSnapshot AIR = new CellSnapshot(Kind.AIR,
            new BlockData("minecraft:air", Map.of(), new byte[0]), null, List.of());
    private static final BlockData HELPER = new BlockData("test:internal_part", Map.of(), new byte[0]);
    private static final Footprint FULL = new Footprint(List.of(new Box(0, 0, 0, 1, 1, 1)), List.of());
    private static final Cell ROOT = new Cell(0, 64, 0), SECOND = new Cell(1, 64, 0), THIRD = new Cell(2, 64, 0);
    private static int tests;

    public static void main(String[] args) {
        run("read-only owner checks and unchanged cleanup stage no rollback", RollbackStagingTest::readOnlyCleanup);
        run("rejected checks and stale plans stage no rollback", RollbackStagingTest::rejectedChecks);
        run("actual writes alone are captured once after validation and before any write", RollbackStagingTest::preciseWriteSet);
        run("stale entity checks reject before rollback capture", RollbackStagingTest::entityRevalidation);
        run("unchanged logical refresh is still an actual write", RollbackStagingTest::refreshWrite);
        run("capture failure has no writes or publication", RollbackStagingTest::captureFailure);
        run("partial write restores exact current adapter metadata in reverse order", RollbackStagingTest::partialFailure);
        run("reused access reads current state and stages current rollback metadata", RollbackStagingTest::freshReusedAccess);
        System.out.println("PASS " + tests + " rollback staging checks; Java " + System.getProperty("java.version"));
    }

    private static TransactionCore core() { return new TransactionCore(AIR, HELPER, 32); }
    private static ObjectInstance object(TrackingWorld world) {
        ObjectInstance object = new ObjectInstance(new Owner(UUID.randomUUID(), "test:staging", ROOT),
                new BlockData("test:staging", Map.of("shape", "original"), new byte[]{1, 2}),
                Map.of(Cell.ORIGIN, FULL, new Cell(1, 0, 0), FULL, new Cell(2, 0, 0), FULL));
        world.remember(object); return object;
    }
    private static void place(TransactionCore core, TrackingWorld world, ObjectInstance object) {
        var result = core.execute(world, core.placement(world, object, List.of()));
        require(result.outcome() == Outcome.COMMITTED, "fixture placement: " + result);
    }

    private static void readOnlyCleanup() {
        TrackingWorld world = new TrackingWorld(); TransactionCore core = core(); ObjectInstance object = object(world);
        place(core, world, object); world.resetEvidence();
        for (int i = 0; i < 5; i++) {
            require(TransactionCore.inspectOwner(world, object.owner(), SECOND).status() == TransactionCore.OwnerStatus.LIVE, "live root remains inspectable");
            require(TransactionCore.validateCarrier(world, SECOND).live().equals(List.of(object.owner())), "guest validation retains live owner");
            var plan = core.pruneStale(world, SECOND);
            require(plan.accepted() && plan.plan().writes().isEmpty(), "unchanged cleanup has no writes");
            require(core.execute(world, plan).outcome() == Outcome.COMMITTED, "unchanged cleanup is a committed no-op");
        }
        require(world.reads > 20 && world.captureCalls == 0 && world.captured.isEmpty(), "repeated checks create no rollback snapshot");
        require(world.writes == 0 && world.publications == 0, "read-only cleanup performs no writes or publication");
    }

    private static void rejectedChecks() {
        TrackingWorld world = new TrackingWorld(); TransactionCore core = core(); ObjectInstance object = object(world);
        var refused = core.placement(world, object, List.of((candidate, reads) -> "deliberate_policy_rejection"));
        require(!refused.accepted(), "policy rejects placement");
        require(core.execute(world, refused).outcome() == Outcome.REJECTED, "rejected preparation stays rejected");
        Cell support = new Cell(0, 63, 0);
        var prepared = core.placement(world, object, List.of((candidate, reads) -> { reads.require(support); return null; }));
        CellSnapshot changed = new CellSnapshot(Kind.FOREIGN, new BlockData("test:new_support", Map.of(), new byte[]{9}), null, List.of());
        world.cells.put(support, changed);
        require(world.read(support).equals(changed) && world.read(support).equals(changed), "repeated current reads see changed support");
        require(core.execute(world, prepared).outcome() == Outcome.REJECTED, "read-only support precondition is freshly checked");
        require(world.captureCalls == 0 && world.writes == 0 && world.publications == 0, "failed checks create no rollback data");
    }

    private static void preciseWriteSet() {
        TrackingWorld world = new TrackingWorld(); TransactionCore core = core(); ObjectInstance object = object(world);
        Cell support = new Cell(0, 63, 0);
        var prepared = core.placement(world, object, List.of((candidate, reads) -> { reads.require(support); return null; }));
        require(prepared.plan().expected().containsKey(support) && !prepared.plan().writes().containsKey(support), "support is a read-only precondition");
        require(world.captureCalls == 0 && world.writes == 0, "preflight creates no rollback data");
        world.requiredReadsBeforeCapture = Set.copyOf(prepared.plan().expected().keySet()); world.readSinceReset.clear();
        var result = core.execute(world, prepared);
        require(result.outcome() == Outcome.COMMITTED && result.changedCells() == 3, "three actual writes commit");
        require(world.captureCalls == 1 && world.captured.keySet().equals(Set.of(ROOT, SECOND, THIRD)), "only actual write cells captured exactly once");
        require(world.captureCounts.values().stream().allMatch(count -> count == 1), "each write cell staged once");
        require(world.events.indexOf("capture-complete") < world.events.indexOf("write:" + ROOT), "every snapshot is ready before first mutation");
        require(world.captureMapImmutable, "adapter cannot mutate the immutable precondition map");
    }

    private static void entityRevalidation() {
        TrackingWorld world = new TrackingWorld(); TransactionCore core = core(); ObjectInstance object = object(world);
        var prepared = core.placement(world, object, List.of()); world.blocked.add(SECOND);
        require(core.execute(world, prepared).outcome() == Outcome.REJECTED, "new obstruction is revalidated");
        require(world.captureCalls == 0 && world.writes == 0, "obstruction rejection precedes staging");
    }

    private static void refreshWrite() {
        TrackingWorld world = new TrackingWorld(); TransactionCore core = core(); ObjectInstance before = object(world);
        place(core, world, before); world.resetEvidence(); world.refresh.add(SECOND);
        var prepared = core.transition(world, before.owner(), before, List.of());
        require(prepared.accepted() && prepared.plan().writes().keySet().equals(Set.of(SECOND)), "cache refresh selects only logically unchanged helper");
        require(core.execute(world, prepared).outcome() == Outcome.COMMITTED, "refresh commits");
        require(world.captured.keySet().equals(Set.of(SECOND)) && world.writes == 1, "actual refresh gets exactly one rollback snapshot");
    }

    private static void captureFailure() {
        TrackingWorld world = new TrackingWorld(); TransactionCore core = core(); ObjectInstance object = object(world);
        world.failCaptureAt = SECOND;
        var result = core.execute(world, core.placement(world, object, List.of()));
        require(result.outcome() == Outcome.REJECTED && result.reason().contains("rollback_capture_failed") && result.reason().contains("injected_capture_failure"), "capture failure is explicit");
        require(world.writes == 0 && world.restored.isEmpty() && world.publications == 0 && world.cells.isEmpty(), "capture failure never mutates or publishes the world");
    }

    private static void partialFailure() {
        TrackingWorld world = new TrackingWorld(); TransactionCore core = core(); ObjectInstance object = object(world);
        world.metadata.put(ROOT, new byte[]{1, 0, -1}); world.metadata.put(SECOND, new byte[]{2, 3}); world.metadata.put(THIRD, new byte[]{4, 5});
        var prepared = core.placement(world, object, List.of());
        // Metadata not part of the logical read may legitimately change before execute.
        // Rollback must preserve the current value, not a stale preflight copy.
        world.metadata.put(ROOT, new byte[]{9, 8, 7, 6});
        Map<Cell, byte[]> beforeMetadata = copyBytes(world.metadata); Map<Cell, CellSnapshot> beforeCells = new TreeMap<>(world.cells);
        world.failWriteAt = 2;
        var result = core.execute(world, prepared);
        require(result.outcome() == Outcome.ROLLED_BACK, "partial second write rolls back");
        require(sameBytes(beforeMetadata, world.metadata) && beforeCells.equals(world.cells), "both logical state and current adapter metadata are exact after rollback");
        require(world.restored.equals(List.of(SECOND, ROOT)), "only attempted cells restore, in reverse mutation order");
        require(world.captureCounts.keySet().equals(Set.of(ROOT, SECOND, THIRD)), "all intended writes captured before the partial error");
        require(Arrays.equals(world.captured.get(ROOT).metadata, new byte[]{9, 8, 7, 6}), "capture used post-preflight metadata");
    }

    private static void freshReusedAccess() {
        TrackingWorld world = new TrackingWorld(); TransactionCore core = core(); ObjectInstance object = object(world); place(core, world, object);
        var prepared = core.removal(world, object.owner());
        CellSnapshot old = world.read(ROOT);
        CellSnapshot changed = new CellSnapshot(old.kind(), old.data().withNbt(new byte[]{5, 4, 3}), old.resident(), old.guests());
        world.cells.put(ROOT, changed); world.resetEvidence();
        require(world.read(ROOT).equals(changed) && world.read(ROOT).equals(changed), "same adapter returns current payload on repeated reads");
        require(core.execute(world, prepared).outcome() == Outcome.REJECTED && world.captureCalls == 0, "stale root payload rejects before staging");
        world.metadata.put(ROOT, new byte[]{11, 12, 13}); world.resetEvidence(); world.failWriteAt = 2;
        Map<Cell, CellSnapshot> cells = new TreeMap<>(world.cells); Map<Cell, byte[]> metadata = copyBytes(world.metadata);
        require(core.execute(world, core.removal(world, object.owner())).outcome() == Outcome.ROLLED_BACK, "fresh plan on reused adapter restores after fault");
        require(cells.equals(world.cells) && sameBytes(metadata, world.metadata), "reused adapter captures new rollback state instead of prior transaction state");
    }

    private record Saved(CellSnapshot snapshot, byte[] metadata) {}
    private static final class TrackingWorld implements TransactionCore.WorldAccess {
        final Map<Cell, CellSnapshot> cells = new TreeMap<>();
        final Map<Cell, byte[]> metadata = new TreeMap<>();
        final Map<String, Map<Cell, Footprint>> layouts = new HashMap<>();
        final Map<Cell, Saved> captured = new TreeMap<>(); final Map<Cell, Integer> captureCounts = new TreeMap<>();
        final Set<Cell> blocked = new HashSet<>(), refresh = new HashSet<>(), readSinceReset = new HashSet<>();
        Set<Cell> requiredReadsBeforeCapture = Set.of();
        final List<Cell> restored = new ArrayList<>(); final List<String> events = new ArrayList<>();
        Cell failCaptureAt; int failWriteAt = -1, reads, captureCalls, writes, publications; boolean captureMapImmutable;
        void remember(ObjectInstance object) { layouts.put(object.rootData().blockId() + object.rootData().properties(), object.cells()); }
        void resetEvidence() { captured.clear(); captureCounts.clear(); restored.clear(); events.clear(); readSinceReset.clear(); reads = captureCalls = writes = publications = 0; captureMapImmutable = false; }
        @Override public void assertMutationThread() {}
        @Override public String dimensionKey() { return "test:staging"; }
        @Override public boolean isLoaded(Cell cell) { return true; }
        @Override public boolean inBounds(Cell cell) { return true; }
        @Override public CellSnapshot read(Cell cell) { reads++; readSinceReset.add(cell); events.add("read:" + cell); return cells.getOrDefault(cell, AIR); }
        @Override public Optional<ObjectInstance> describeRoot(Owner owner, CellSnapshot root) {
            var geometry = layouts.get(root.data().blockId() + root.data().properties());
            return geometry == null ? Optional.empty() : Optional.of(new ObjectInstance(owner, root.data(), geometry));
        }
        @Override public boolean supportsGuestBindings(CellSnapshot carrier) { return carrier.kind() == Kind.ROOT || carrier.kind() == Kind.HELPER; }
        @Override public boolean intersectsEntities(Cell cell, Footprint proposed) { return blocked.contains(cell); }
        @Override public boolean needsRefresh(Cell cell, CellSnapshot before, CellSnapshot next) { return refresh.contains(cell); }
        @Override public void prepareRollback(Map<Cell, CellSnapshot> expectedWrites) {
            require(writes == 0, "rollback staging must precede every write");
            require(readSinceReset.containsAll(requiredReadsBeforeCapture), "all expected states were freshly revalidated before staging");
            captureCalls++; captured.clear();
            try { expectedWrites.put(new Cell(99, 64, 99), AIR); }
            catch (UnsupportedOperationException expected) { captureMapImmutable = true; }
            require(captureMapImmutable, "write-set preconditions must be immutable");
            for (var entry : expectedWrites.entrySet()) {
                Cell cell = entry.getKey();
                require(entry.getValue().equals(cells.getOrDefault(cell, AIR)), "staging receives validated previous logical state");
                captureCounts.merge(cell, 1, Integer::sum);
                byte[] data = metadata.get(cell); captured.put(cell, new Saved(entry.getValue(), data == null ? null : data.clone()));
                events.add("capture:" + cell);
                if (cell.equals(failCaptureAt)) throw new IllegalStateException("injected_capture_failure");
            }
            events.add("capture-complete");
        }
        @Override public boolean writeSilently(Cell cell, CellSnapshot next) {
            require(events.contains("capture-complete"), "write cannot begin before all snapshots are staged");
            require(captured.containsKey(cell), "no unstaged write"); writes++; events.add("write:" + cell);
            if (next.equals(AIR)) cells.remove(cell); else cells.put(cell, next);
            metadata.put(cell, new byte[]{99, (byte) writes});
            return writes != failWriteAt;
        }
        @Override public boolean restoreSilently(Cell cell, CellSnapshot previous) {
            Saved saved = captured.get(cell); require(saved != null && saved.snapshot.equals(previous), "restore uses staged logical original");
            if (previous.equals(AIR)) cells.remove(cell); else cells.put(cell, previous);
            if (saved.metadata == null) metadata.remove(cell); else metadata.put(cell, saved.metadata.clone());
            restored.add(cell); return true;
        }
        @Override public void publishCommitted(List<Cell> changedCells) { publications++; }
    }
    private static Map<Cell, byte[]> copyBytes(Map<Cell, byte[]> values) { Map<Cell, byte[]> result = new TreeMap<>(); values.forEach((cell, bytes) -> result.put(cell, bytes.clone())); return result; }
    private static boolean sameBytes(Map<Cell, byte[]> expected, Map<Cell, byte[]> actual) { return expected.keySet().equals(actual.keySet()) && expected.entrySet().stream().allMatch(entry -> Arrays.equals(entry.getValue(), actual.get(entry.getKey()))); }
    private static void run(String name, Runnable test) { test.run(); tests++; System.out.println("PASS " + name); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
