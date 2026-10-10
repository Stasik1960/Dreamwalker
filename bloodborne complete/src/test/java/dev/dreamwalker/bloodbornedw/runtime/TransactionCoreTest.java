package dev.dreamwalker.bloodbornedw.runtime;

import dev.dreamwalker.bloodbornedw.runtime.CellSnapshot.BlockData;
import dev.dreamwalker.bloodbornedw.runtime.CellSnapshot.Kind;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Footprint;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Point;
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

/** Dependency-free Java 17 checks; these verify the pure core, not Fabric gameplay. */
public final class TransactionCoreTest {
    private static final BlockData AIR_DATA = new BlockData("minecraft:air", Map.of(), new byte[0]);
    private static final CellSnapshot AIR = new CellSnapshot(Kind.AIR, AIR_DATA, null, List.of());
    private static final BlockData HELPER = new BlockData("test:internal_part", Map.of(), new byte[0]);
    private static final Footprint FULL = footprint(new Box(0, 0, 0, 1, 1, 1));
    private static int tests;

    public static void main(String[] args) {
        run("whole object placement/removal", TransactionCoreTest::wholeObject);
        run("foreign state and BE preservation", TransactionCoreTest::foreignObstruction);
        run("shared helper targeting and independent removal", TransactionCoreTest::sharedHelpers);
        run("shared root insertion retains other object NBT", TransactionCoreTest::sharedRoot);
        run("partial write failure restores every snapshot", TransactionCoreTest::rollback);
        run("stale plan rejects without a write", TransactionCoreTest::stalePlan);
        run("unloaded owner is deferred; same ID reuse is stale", TransactionCoreTest::deferredAndReusedOwner);
        run("support reads become commit preconditions", TransactionCoreTest::supportChecks);
        run("visual-only transition ignores unchanged collision", TransactionCoreTest::visualOnly);
        run("entity movement between preflight and commit", TransactionCoreTest::entityMoves);
        run("chunk boundary rejects unloaded cells without loading", TransactionCoreTest::chunkBoundary);
        run("rotation hook keeps BE payload and four-turn geometry", TransactionCoreTest::rotations);
        run("notifications see complete commit and can roll back", TransactionCoreTest::notifications);
        run("rollback failure is an explicit failure", TransactionCoreTest::rollbackFailure);
        run("bounded multi-owner capacity", TransactionCoreTest::capacity);
        run("dimension-scoped plan", TransactionCoreTest::dimension);
        run("obsolete membership prunes links without destroying roots", TransactionCoreTest::obsoleteMembership);
        run("snapshots copy opaque bytes and state maps", TransactionCoreTest::immutableSnapshots);
        run("unchanged helpers refresh cached data and roll back separately", TransactionCoreTest::cachedRefresh);
        run("trusted functional payload transition preserves explicit adapter update", TransactionCoreTest::functionalPayload);
        run("alias migration retains UUID/payload and atomically rewrites helpers", TransactionCoreTest::aliasMigration);
        System.out.println("PASS " + tests + " transaction core checks; Java " + System.getProperty("java.version"));
    }

    private static void wholeObject() {
        MemoryWorld world = new MemoryWorld(); TransactionCore core = core();
        ObjectInstance object = instance(world, "whole", new Cell(15, 64, 0), Map.of(Cell.ORIGIN, FULL, new Cell(1, 0, 0), FULL, new Cell(1, 1, 0), FULL));
        require(core.execute(world, core.placement(world, object, List.of())).outcome() == Outcome.COMMITTED, "place whole");
        Cell helper = object.owner().root().add(new Cell(1, 1, 0));
        TargetResolver.Targets target = TargetResolver.raycastCell(world, helper, new Point(16.5, 65.5, -1), new Point(0, 0, 1), 5);
        require(object.owner().equals(target.nearest().owner()), "helper targets whole owner");
        require(core.execute(world, core.removal(world, target.nearest().owner())).outcome() == Outcome.COMMITTED, "remove whole");
        for (Cell offset : object.cells().keySet()) require(world.read(object.owner().root().add(offset)).equals(AIR), "no orphan");
    }

    private static void foreignObstruction() {
        MemoryWorld world = new MemoryWorld(); TransactionCore core = core();
        Cell occupied = new Cell(1, 64, 0); byte[] payload = {0, 1, 2, -1, 98};
        world.cells.put(occupied, new CellSnapshot(Kind.FOREIGN, new BlockData("test:foreign_inventory", Map.of("direction", "west"), payload), null, List.of()));
        Map<Cell, CellSnapshot> before = world.copy();
        ObjectInstance object = instance(world, "blocked", new Cell(0, 64, 0), Map.of(Cell.ORIGIN, FULL, new Cell(1, 0, 0), FULL));
        require(!core.placement(world, object, List.of()).accepted(), "foreign helper conflict");
        require(before.equals(world.copy()) && world.writes == 0, "foreign world untouched");
        require(Arrays.equals(payload, world.read(occupied).data().blockEntityNbt()), "foreign NBT exact");
    }

    private static void sharedHelpers() {
        MemoryWorld world = new MemoryWorld(); TransactionCore core = core(); Cell shared = new Cell(0, 64, 0);
        Footprint west = footprint(new Box(0, 0, 0, .4, 1, 1)), east = footprint(new Box(.6, 0, 0, 1, 1, 1));
        ObjectInstance a = instance(world, "west", new Cell(-1, 64, 0), Map.of(Cell.ORIGIN, Footprint.EMPTY, new Cell(1, 0, 0), west));
        ObjectInstance b = instance(world, "east", new Cell(1, 64, 0), Map.of(Cell.ORIGIN, Footprint.EMPTY, new Cell(-1, 0, 0), east));
        place(core, world, a); place(core, world, b);
        require(world.read(shared).guests().size() == 2, "two independent guest bindings");
        var westTarget = TargetResolver.raycastCell(world, shared, new Point(-2, 64.5, .5), new Point(1, 0, 0), 5);
        var eastTarget = TargetResolver.raycastCell(world, shared, new Point(2, 64.5, .5), new Point(-1, 0, 0), 5);
        require(westTarget.candidates().size() == 2 && westTarget.nearest().owner().equals(a.owner()), "western visible shape selectable");
        require(eastTarget.nearest().owner().equals(b.owner()), "eastern visible shape selectable");
        require(core.execute(world, core.removal(world, a.owner())).outcome() == Outcome.COMMITTED, "remove selected owner");
        require(world.read(shared).guests().equals(List.of(b.owner())) && world.read(b.owner().root()).resident().equals(b.owner()), "other owner intact");
    }

    private static void sharedRoot() {
        MemoryWorld world = new MemoryWorld(); TransactionCore core = core(); Cell carrier = new Cell(0, 64, 0);
        ObjectInstance guest = instance(world, "guest", new Cell(-1, 64, 0), Map.of(Cell.ORIGIN, Footprint.EMPTY, new Cell(1, 0, 0), footprint(new Box(0, 0, 0, .2, 1, 1))));
        ObjectInstance resident = instance(world, "resident", carrier, Map.of(Cell.ORIGIN, footprint(new Box(.5, 0, 0, 1, 1, 1))));
        resident = withPayload(world, resident, new byte[]{11, 12, 0, -8});
        place(core, world, guest); place(core, world, resident);
        CellSnapshot saved = world.read(carrier);
        require(saved.resident().equals(resident.owner()) && saved.guests().equals(List.of(guest.owner())), "root carries guest");
        require(core.execute(world, core.removal(world, guest.owner())).outcome() == Outcome.COMMITTED, "guest removed independently");
        require(world.read(carrier).resident().equals(resident.owner()) && world.read(carrier).data().equals(saved.data()), "resident data/NBT exact");
    }

    private static void rollback() {
        MemoryWorld world = new MemoryWorld(); TransactionCore core = core();
        ObjectInstance object = instance(world, "rollback", new Cell(0, 64, 0), Map.of(Cell.ORIGIN, FULL, new Cell(1, 0, 0), FULL, new Cell(2, 0, 0), FULL));
        world.cells.put(new Cell(1, 64, 0), new CellSnapshot(Kind.AIR, new BlockData("minecraft:cave_air", Map.of(), new byte[]{7, 8}), null, List.of()));
        Map<Cell, CellSnapshot> original = world.copy(); world.failWrite = 2;
        require(core.execute(world, core.placement(world, object, List.of())).outcome() == Outcome.ROLLED_BACK, "partial adapter failure rolls back");
        require(original.equals(world.copy()), "all snapshots restored including BE bytes");
    }

    private static void stalePlan() {
        MemoryWorld world = new MemoryWorld(); TransactionCore core = core();
        ObjectInstance object = instance(world, "staleplan", new Cell(0, 64, 0), Map.of(Cell.ORIGIN, FULL));
        var prepared = core.placement(world, object, List.of());
        CellSnapshot foreign = new CellSnapshot(Kind.FOREIGN, new BlockData("minecraft:stone", Map.of(), new byte[0]), null, List.of());
        world.cells.put(object.owner().root(), foreign);
        require(core.execute(world, prepared).outcome() == Outcome.REJECTED, "reject stale preflight");
        require(world.writes == 0 && world.read(object.owner().root()).equals(foreign), "no stale write");
    }

    private static void deferredAndReusedOwner() {
        MemoryWorld world = new MemoryWorld(); TransactionCore core = core();
        ObjectInstance object = instance(world, "nonce", new Cell(15, 64, 0), Map.of(Cell.ORIGIN, FULL, new Cell(1, 0, 0), FULL));
        place(core, world, object); Cell helper = new Cell(16, 64, 0); CellSnapshot saved = world.read(helper);
        world.unloaded.add(object.owner().root());
        var validation = TransactionCore.validateCarrier(world, helper);
        require(validation.deferred().equals(List.of(object.owner())) && validation.stale().isEmpty(), "unloaded root deferred");
        require(core.execute(world, core.pruneStale(world, helper)).outcome() == Outcome.COMMITTED && world.read(helper).equals(saved), "deferred binding retained");
        require(!core.removal(world, object.owner()).accepted(), "unloaded whole removal rejected");
        world.unloaded.clear();
        Owner replacement = new Owner(UUID.randomUUID(), object.owner().registryId(), object.owner().root());
        world.cells.put(replacement.root(), new CellSnapshot(Kind.ROOT, object.rootData(), replacement, List.of()));
        require(TransactionCore.validateCarrier(world, helper).stale().equals(List.of(object.owner())), "same ID with new nonce does not own old helper");
        require(core.execute(world, core.pruneStale(world, helper)).outcome() == Outcome.COMMITTED, "prune proven stale");
        require(world.read(helper).equals(AIR) && world.read(replacement.root()).resident().equals(replacement), "new root untouched");
    }

    private static void supportChecks() {
        MemoryWorld world = new MemoryWorld(); TransactionCore core = core(); Cell support = new Cell(0, 63, 0);
        ObjectInstance object = instance(world, "supported", new Cell(0, 64, 0), Map.of(Cell.ORIGIN, FULL));
        CellSnapshot stone = new CellSnapshot(Kind.FOREIGN, new BlockData("minecraft:stone", Map.of(), new byte[]{1}), null, List.of());
        world.cells.put(support, stone);
        List<TransactionCore.PlacementCheck> checks = List.of((candidate, reads) -> reads.require(support).kind() == Kind.FOREIGN ? null : "missing_floor");
        var prepared = core.placement(world, object, checks); require(prepared.accepted(), "valid support");
        world.cells.remove(support);
        require(core.execute(world, prepared).outcome() == Outcome.REJECTED && world.writes == 0, "support change invalidates plan");
        world.unloaded.add(support);
        require(!core.placement(world, object, checks).accepted(), "unloaded support rejected");
    }

    private static void visualOnly() {
        MemoryWorld world = new MemoryWorld(); TransactionCore core = core();
        ObjectInstance object = instance(world, "visualbase", new Cell(0, 64, 0), Map.of(Cell.ORIGIN, FULL)); place(core, world, object);
        world.entityBlocked.add(object.owner().root()); int queries = world.entityQueries;
        ObjectInstance alt = new ObjectInstance(object.owner(), object.rootData().withProperties(Map.of("shape", "visualalt", "profile", "alt")), object.cells()); world.remember(alt);
        require(core.execute(world, core.transition(world, object.owner(), alt, List.of())).outcome() == Outcome.COMMITTED, "ALT transition beside player");
        require(world.entityQueries == queries, "unchanged collision skips entity obstruction");
        ObjectInstance altered = new ObjectInstance(object.owner(), alt.rootData().withProperties(Map.of("shape", "closed")), Map.of(Cell.ORIGIN, footprint(new Box(0, 0, 0, .5, 1, 1)))); world.remember(altered);
        require(!core.transition(world, object.owner(), altered, List.of()).accepted(), "physical transition checks entity");
    }

    private static void entityMoves() {
        MemoryWorld world = new MemoryWorld(); TransactionCore core = core();
        ObjectInstance object = instance(world, "movingentity", new Cell(0, 64, 0), Map.of(Cell.ORIGIN, FULL));
        var prepared = core.placement(world, object, List.of()); world.entityBlocked.add(object.owner().root());
        require(core.execute(world, prepared).outcome() == Outcome.REJECTED && world.writes == 0, "moving entity rechecked at commit");
    }

    private static void chunkBoundary() {
        MemoryWorld world = new MemoryWorld(); TransactionCore core = core();
        ObjectInstance object = instance(world, "boundary", new Cell(15, 64, 0), Map.of(Cell.ORIGIN, FULL, new Cell(1, 0, 0), FULL));
        world.unloaded.add(new Cell(16, 64, 0));
        require(!core.placement(world, object, List.of()).accepted() && world.writes == 0, "unloaded destination rejects whole plan");
        require(world.unloadedReads == 0, "never read or load absent chunk");
    }

    private static void rotations() {
        MemoryWorld world = new MemoryWorld(); TransactionCore core = core();
        ObjectInstance object = instance(world, "rot0", new Cell(0, 64, 0), Map.of(Cell.ORIGIN, Footprint.EMPTY, new Cell(1, 0, 0), footprint(new Box(.1, 0, .2, .4, 1, .7))));
        object = withPayload(world, object, new byte[]{9, 10}); place(core, world, object);
        ObjectInstance rotated = object;
        for (int i = 0; i < 4; i++) rotated = rotated.rotate(1, (data, turns) -> data);
        for (Cell offset : object.cells().keySet()) require(close(object.cells().get(offset), rotated.cells().get(offset)), "four rotations return original geometry");
        rotated = object.rotate(1, (data, turns) -> new BlockData(data.blockId(), Map.of("shape", "rot1"), new byte[]{99})); world.remember(rotated);
        require(core.execute(world, core.transition(world, object.owner(), rotated, List.of())).outcome() == Outcome.COMMITTED, "whole rotation");
        require(world.read(new Cell(1, 64, 0)).equals(AIR) && world.read(new Cell(0, 64, 1)).guests().contains(object.owner()), "old cells clear, new helpers bound");
        require(Arrays.equals(new byte[]{9, 10}, world.read(object.owner().root()).data().blockEntityNbt()), "rotation preserves old root NBT");
    }

    private static void notifications() {
        MemoryWorld world = new MemoryWorld(); TransactionCore core = core();
        ObjectInstance object = instance(world, "notify", new Cell(0, 64, 0), Map.of(Cell.ORIGIN, FULL, new Cell(1, 0, 0), FULL));
        world.onPublish = () -> require(world.read(object.owner().root()).resident() != null && world.read(new Cell(1, 64, 0)).guests().contains(object.owner()), "notifications see complete object");
        place(core, world, object);
        world.onPublish = null; world.failPublish = true; Map<Cell, CellSnapshot> original = world.copy();
        require(core.execute(world, core.removal(world, object.owner())).outcome() == Outcome.ROLLED_BACK, "notification adapter failure restores snapshots");
        require(original.equals(world.copy()), "post-failure world exact");
    }

    private static void rollbackFailure() {
        MemoryWorld world = new MemoryWorld(); TransactionCore core = core();
        ObjectInstance object = instance(world, "badrollback", new Cell(0, 64, 0), Map.of(Cell.ORIGIN, FULL, new Cell(1, 0, 0), FULL));
        world.failWrite = 2; world.failRestoration = true;
        require(core.execute(world, core.placement(world, object, List.of())).outcome() == Outcome.ROLLBACK_FAILED, "do not certify incomplete rollback");
    }

    private static void capacity() {
        MemoryWorld world = new MemoryWorld(); TransactionCore core = new TransactionCore(AIR, HELPER, 2); Cell shared = new Cell(0, 64, 0);
        for (int index = 0; index < 2; index++) {
            Cell root = new Cell(-index - 1, 64, 0); ObjectInstance object = instance(world, "capacity" + index, root, Map.of(Cell.ORIGIN, Footprint.EMPTY, shared.subtract(root), Footprint.EMPTY));
            place(core, world, object);
        }
        ObjectInstance excess = instance(world, "capacityexcess", new Cell(1, 64, 0), Map.of(Cell.ORIGIN, Footprint.EMPTY, new Cell(-1, 0, 0), Footprint.EMPTY));
        Map<Cell, CellSnapshot> before = world.copy();
        require(!core.placement(world, excess, List.of()).accepted() && before.equals(world.copy()), "capacity refuses whole placement");
    }

    private static void dimension() {
        MemoryWorld world = new MemoryWorld(); TransactionCore core = core();
        ObjectInstance object = instance(world, "dimension", new Cell(0, 64, 0), Map.of(Cell.ORIGIN, FULL)); var prepared = core.placement(world, object, List.of());
        world.dimension = "test:other";
        require(core.execute(world, prepared).outcome() == Outcome.REJECTED && world.writes == 0, "cross-dimension misuse rejected");
    }

    private static void obsoleteMembership() {
        MemoryWorld world = new MemoryWorld(); TransactionCore core = core();
        ObjectInstance object = instance(world, "oldmembership", new Cell(0, 64, 0), Map.of(Cell.ORIGIN, FULL, new Cell(1, 0, 0), FULL)); place(core, world, object);
        world.layouts.put(key(object.rootData()), Map.of(Cell.ORIGIN, FULL));
        require(TransactionCore.validateCarrier(world, new Cell(1, 64, 0)).stale().equals(List.of(object.owner())), "current state must own helper");
        require(core.execute(world, core.pruneStale(world, new Cell(1, 64, 0))).outcome() == Outcome.COMMITTED, "prune old membership");
        require(world.read(object.owner().root()).resident().equals(object.owner()), "root preserved");
    }

    private static void immutableSnapshots() {
        byte[] bytes = {1, 2}; Map<String, String> properties = new HashMap<>(Map.of("open", "false"));
        BlockData data = new BlockData("test:immutable", properties, bytes); bytes[0] = 9; properties.put("open", "true");
        byte[] returned = data.blockEntityNbt(); returned[0] = 8;
        require(Arrays.equals(data.blockEntityNbt(), new byte[]{1, 2}) && data.properties().get("open").equals("false"), "snapshots immutable");
    }
    private static void cachedRefresh() {
        Cell root=new Cell(0,64,0),helper=root.add(new Cell(1,0,0));
        int[] version={0},cached={0},rollbackVersion={0},restores={0}; boolean[] refresh={false};
        MemoryWorld world=new MemoryWorld(){
            @Override public boolean needsRefresh(Cell cell,CellSnapshot before,CellSnapshot next){return refresh[0]&&cell.equals(helper);}
            @Override public boolean writeSilently(Cell cell,CellSnapshot next){if(cell.equals(helper))cached[0]=version[0];return super.writeSilently(cell,next);}
            @Override public boolean restoreSilently(Cell cell,CellSnapshot previous){restores[0]++;if(cell.equals(helper))cached[0]=rollbackVersion[0];return super.writeSilently(cell,previous);}
        };
        TransactionCore core=core();ObjectInstance before=instance(world,"cached",root,Map.of(Cell.ORIGIN,FULL,new Cell(1,0,0),FULL));place(core,world,before);
        ObjectInstance after=new ObjectInstance(before.owner(),new BlockData(before.rootData().blockId(),Map.of("shape","new_art"),new byte[0]),before.cells());world.remember(after);refresh[0]=true;version[0]=1;
        var plan=core.transition(world,before.owner(),after,List.of());require(plan.accepted()&&plan.plan().writes().containsKey(helper),"unchanged helper must remain in cache refresh plan");require(core.execute(world,plan).outcome()==Outcome.COMMITTED&&cached[0]==1,"helper cache follows new art even same logical carrier");
        ObjectInstance failed=new ObjectInstance(before.owner(),new BlockData(before.rootData().blockId(),Map.of("shape","failed_art"),new byte[0]),before.cells());world.remember(failed);version[0]=2;rollbackVersion[0]=1;world.failWrite=world.writes+2;Map<Cell,CellSnapshot> snapshots=world.copy();
        require(core.execute(world,core.transition(world,before.owner(),failed,List.of())).outcome()==Outcome.ROLLED_BACK,"failed cached transition rolls back");require(cached[0]==1&&restores[0]==2&&world.copy().equals(snapshots),"distinct restore hook recovers cached and logical data");
    }

    private static TransactionCore core() { return new TransactionCore(AIR, HELPER, 16); }
    private static Footprint footprint(Box box) { return new Footprint(List.of(box), List.of(box)); }
    private static ObjectInstance instance(MemoryWorld world, String shape, Cell root, Map<Cell, Footprint> cells) {
        ObjectInstance object = new ObjectInstance(new Owner(UUID.randomUUID(), "test:" + shape.toLowerCase(), root),
                new BlockData("test:" + shape.toLowerCase(), Map.of("shape", shape), new byte[0]), cells);
        world.remember(object); return object;
    }
    private static void functionalPayload(){
        MemoryWorld world=new MemoryWorld();TransactionCore core=core();
        ObjectInstance before=withPayload(world,instance(world,"mounted",new Cell(0,64,0),Map.of(Cell.ORIGIN,FULL)),new byte[]{3,1,4});place(core,world,before);
        ObjectInstance after=new ObjectInstance(before.owner(),before.rootData().withNbt(new byte[]{3,1,4,9}),before.cells());world.remember(after);
        require(core.execute(world,core.transitionPayload(world,before.owner(),after,List.of())).outcome()==Outcome.COMMITTED,"explicit functional payload commits");
        require(Arrays.equals(world.read(before.owner().root()).data().blockEntityNbt(),new byte[]{3,1,4,9}),"trusted adapter payload is retained rather than replaced with stale mount data");
    }
    private static void aliasMigration() {
        MemoryWorld world=new MemoryWorld();TransactionCore core=core();Cell root=new Cell(0,64,0);
        ObjectInstance old=withPayload(world,instance(world,"retired",root,Map.of(Cell.ORIGIN,FULL,new Cell(1,0,0),FULL)),new byte[]{5,4,3});place(core,world,old);
        Owner owner=new Owner(old.owner().instanceId(),"test:canonical",root);
        ObjectInstance next=new ObjectInstance(owner,new BlockData("test:canonical",Map.of("shape","canonical"),new byte[]{5,4,3}),old.cells());world.remember(next);
        require(!core.transition(world,old.owner(),next,List.of()).accepted(),"normal edits cannot change registry identity");
        require(core.execute(world,core.migrateAlias(world,old.owner(),next,List.of())).outcome()==Outcome.COMMITTED,"explicit migration commits");
        require(world.read(root).resident().equals(owner)&&world.read(root.add(new Cell(1,0,0))).guests().equals(List.of(owner)),"all memberships switch together, UUID stable");
        require(Arrays.equals(world.read(root).data().blockEntityNbt(),new byte[]{5,4,3}),"typed payload exact");
        require(!core.migrateAlias(world,owner,new ObjectInstance(new Owner(UUID.randomUUID(),"test:canonical",root),next.rootData(),next.cells()),List.of()).accepted(),"migration cannot replace UUID");
        ObjectInstance reverse=new ObjectInstance(old.owner(),old.rootData(),old.cells());world.remember(reverse);world.failWrite=world.writes+2;var captured=world.copy();
        require(core.execute(world,core.migrateAlias(world,owner,reverse,List.of())).outcome()==Outcome.ROLLED_BACK&&captured.equals(world.copy()),"failed migration restores whole assembly");
    }
    private static ObjectInstance withPayload(MemoryWorld world, ObjectInstance object, byte[] payload) {
        ObjectInstance result = new ObjectInstance(object.owner(), object.rootData().withNbt(payload), object.cells()); world.remember(result); return result;
    }
    private static void place(TransactionCore core, MemoryWorld world, ObjectInstance object) {
        var prepared = core.placement(world, object, List.of());
        require(prepared.accepted(), "placement accepted: " + prepared.reason());
        require(core.execute(world, prepared).outcome() == Outcome.COMMITTED, "placement committed");
    }
    private static boolean close(Footprint a, Footprint b) {
        if (a.collision().size() != b.collision().size() || a.selection().size() != b.selection().size()) return false;
        for (int i = 0; i < a.collision().size(); i++) {
            Box x = a.collision().get(i), y = b.collision().get(i);
            double[] left = {x.minX(), x.minY(), x.minZ(), x.maxX(), x.maxY(), x.maxZ()}, right = {y.minX(), y.minY(), y.minZ(), y.maxX(), y.maxY(), y.maxZ()};
            for (int j = 0; j < left.length; j++) if (Math.abs(left[j] - right[j]) > 1e-12) return false;
        }
        return true;
    }
    private static void run(String name, Runnable test) { test.run(); tests++; System.out.println("PASS " + name); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private record StateKey(String block, Map<String, String> properties) {}
    private static StateKey key(BlockData data) { return new StateKey(data.blockId(), data.properties()); }

    private static class MemoryWorld implements TransactionCore.WorldAccess {
        private final Map<Cell, CellSnapshot> cells = new TreeMap<>();
        private final Map<StateKey, Map<Cell, Footprint>> layouts = new HashMap<>();
        private final Set<Cell> unloaded = new HashSet<>(), entityBlocked = new HashSet<>();
        private String dimension = "test:city";
        private int writes, entityQueries, unloadedReads, failWrite = -1;
        private boolean failRestoration, failed, failPublish;
        private Runnable onPublish;
        void remember(ObjectInstance instance) { layouts.put(key(instance.rootData()), instance.cells()); }
        Map<Cell, CellSnapshot> copy() { Map<Cell, CellSnapshot> copy = new TreeMap<>(cells); copy.entrySet().removeIf(entry -> entry.getValue().equals(AIR)); return copy; }
        @Override public void assertMutationThread() {}
        @Override public String dimensionKey() { return dimension; }
        @Override public boolean isLoaded(Cell cell) { return !unloaded.contains(cell); }
        @Override public boolean inBounds(Cell cell) { return cell.y() >= -64 && cell.y() < 320; }
        @Override public CellSnapshot read(Cell cell) { if (!isLoaded(cell)) { unloadedReads++; throw new AssertionError("read unloaded chunk"); } return cells.getOrDefault(cell, AIR); }
        @Override public Optional<ObjectInstance> describeRoot(Owner owner, CellSnapshot root) {
            Map<Cell, Footprint> geometry = layouts.get(key(root.data()));
            return geometry == null ? Optional.empty() : Optional.of(new ObjectInstance(owner, root.data(), geometry));
        }
        @Override public boolean supportsGuestBindings(CellSnapshot carrier) { return carrier.kind() == Kind.ROOT || carrier.kind() == Kind.HELPER; }
        @Override public boolean intersectsEntities(Cell cell, Footprint proposed) { entityQueries++; return entityBlocked.contains(cell); }
        @Override public boolean writeSilently(Cell cell, CellSnapshot next) {
            writes++; if (failed && failRestoration) return false;
            cells.put(cell, next);
            if (writes == failWrite) { failed = true; return false; }
            return true;
        }
        @Override public void publishCommitted(List<Cell> changedCells) {
            if (failPublish) { failPublish = false; throw new IllegalStateException("injected_notification_failure"); }
            if (onPublish != null) onPublish.run();
        }
    }
}
