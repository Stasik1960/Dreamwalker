package dev.dreamwalker.serverqa;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornedw.architecture.NoCollidePlacement;
import dev.dreamwalker.bloodbornedw.architecture.PlacementPhysics;
import dev.dreamwalker.bloodbornedw.architecture.RpCollisionShapes;
import dev.dreamwalker.bloodbornedw.architecture.SourceConversionScope;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** Test-only gameplay exercise using the actual Fabric server and owner ledger. */
public final class StudioLayoutQa implements ModInitializer {
    private final JsonObject report = new JsonObject();
    private final JsonArray passed = new JsonArray();
    private final JsonArray failed = new JsonArray();
    private Path output;
    private boolean ran;

    @Override public void onInitialize() {
        String file = System.getProperty("dw.studio.qa");
        if (file == null) return;
        if (!Files.isRegularFile(Path.of("disposable-studio-world.marker")))
            throw new IllegalStateException("Studio QA requires a disposable world");
        output = Path.of(file);
        report.add("passed", passed);
        report.add("failed", failed);
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> new Thread(
                () -> System.exit(report.has("error") ? 1 : 0), "DW-Studio-QA-exit").start());
    }

    private void tick(MinecraftServer server) {
        if (ran) return;
        ran = true;
        try {
            run(server.getOverworld());
            if (!failed.isEmpty()) throw new AssertionError(failed.size() + " QA scenarios failed");
            server.save(false, true, true);
            report.addProperty("status", "PASS");
        } catch (Throwable failure) {
            failure.printStackTrace();
            report.addProperty("status", "FAIL");
            report.addProperty("error", failure.toString());
        }
        try {
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        } finally {
            server.stop(false);
        }
    }

    private void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        passed.add(name);
    }

    private void section(String name, Runnable scenario) {
        try {
            scenario.run();
        } catch (AssertionError | RuntimeException failure) {
            failed.add(name + ": " + failure);
            failure.printStackTrace();
        }
    }

    private void run(ServerWorld world) {
        for (int cx = -2; cx <= 3; cx++) for (int cz = -2; cz <= 2; cz++) world.getChunk(cx, cz);
        var legacy = new Identifier("bloodborne_dw", "source_spruce_trapdoor_bottom");
        check(!Registries.BLOCK.containsId(legacy) && !Registries.ITEM.containsId(legacy), "90952 block and item retired");
        var actor = new ServerPlayerEntity(world.getServer(), world, new GameProfile(UUID.randomUUID(), "StudioQA"));
        actor.networkHandler = new ServerPlayNetworkHandler(world.getServer(), new ClientConnection(NetworkSide.SERVERBOUND), actor);
        section("RP revision", () -> checkRevisionProps(world, actor));
        section("ordinary shutter", () -> checkOrdinaryShutter(world, actor));
        section("composite and RP seats", () -> checkCompositeAndSeat(world, actor));
        section("No Collide placement", () -> checkForcedPlacement(world, actor));
        section("occupied shutter", () -> checkShutterOnOccupiedCell(world, actor));
    }

    private void checkOrdinaryShutter(ServerWorld world, ServerPlayerEntity actor) {
        actor.setPosition(8, 74, 7);
        actor.setYaw(0);

        var window = CompositeArchitecture.kindBlock("source_shutter_window");
        BlockPos root = new BlockPos(8, 74, 8);
        var closed = window.getDefaultState();
        var closedLayout = StudioFootprints.dynamic(CompositeData.data(closed, new net.minecraft.nbt.NbtCompound()));
        check(closedLayout != null && !closedLayout.players().isEmpty() && !closedLayout.outline().isEmpty(), "closed shutter authored shapes");
        // A shutter extends below its root. Use a wall behind it for ordinary
        // placement, leaving the authored sweep and lower frame clear.
        BlockPos backing = root.north();
        world.setBlockState(backing, Blocks.STONE.getDefaultState());
        var shutterItem = CompositeArchitecture.kindItem("source_shutter_window");
        var shutterStack = new ItemStack(shutterItem);
        actor.setStackInHand(Hand.MAIN_HAND, shutterStack);
        var wallHit = new BlockHitResult(new Vec3d(8.5, 74.5, 8), Direction.SOUTH, backing, false);
        var ordinaryContext = new ItemPlacementContext(actor, Hand.MAIN_HAND, shutterStack, wallHit);
        var ordinary = shutterItem.place(ordinaryContext);
        var ordinaryResult = CompositeRuntime.lastResult();
        report.addProperty("ordinaryShutterPlacement", ordinary + " at " + ordinaryContext.getBlockPos().toShortString()
                + ", item " + shutterItem.getClass().getSimpleName() + ", " + (ordinaryResult == null ? "no transaction" : ordinaryResult.reason()));
        check(ordinary.isAccepted(), "ordinary 93241 installed by its item");
        actor.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        var owner = ((CompositeBlockEntity) world.getBlockEntity(root)).resident();
        var playerCell = closedLayout.players().entrySet().iterator().next();
        var offset = playerCell.getKey();
        var playerPos = root.add(offset.x(), offset.y(), offset.z());
        var seen = world.getBlockState(playerPos).getCollisionShape(world, playerPos, ShapeContext.of(actor));
        check(!seen.isEmpty(), "player shape exists in its edited cell");
        var outlineCell = closedLayout.outline().keySet().iterator().next();
        var outlinePos = root.add(outlineCell.x(), outlineCell.y(), outlineCell.z());
        check(!world.getBlockState(outlinePos).getOutlineShape(world, outlinePos, ShapeContext.of(actor)).isEmpty(), "authored outline raycast shape");
        var opened = closed.with(CompositeRootBlock.OPEN, true);
        var openLayout = StudioFootprints.dynamic(CompositeData.data(opened, new net.minecraft.nbt.NbtCompound()));
        check(!openLayout.outline().equals(closedLayout.outline()), "open and closed poses are independent");
        clickShutter(world, actor, owner, root, true);
        clickShutter(world, actor, owner, root, false);
        check(CompositeRuntime.remove(world, owner, null, false).outcome() == TransactionCore.Outcome.COMMITTED, "remove the complete shutter");

    }

    private void checkCompositeAndSeat(ServerWorld world, ServerPlayerEntity actor) {
        var wood = CompositeArchitecture.kindBlock("prototype_wood_window");
        BlockState woodState = wood.getDefaultState();
        var model = StudioLayouts.model(wood.spec);
        check(model.anchor().equals(new Vec3d(0, 0, -1)), "wood window anchor from exact Studio project");
        var rawPart = wood.spec.pose(woodState).parts().get(0);
        var art = ImportedStairLegacyArt.selectedParts(wood, woodState, new net.minecraft.nbt.NbtCompound()).get(0);
        var authored = StudioLayouts.pose(model, woodState);
        var expected = authored.offset().subtract(model.anchor());
        check(Math.abs(art.offset().z() - rawPart.offset().z() - expected.z * 16) < 1e-8, "art shift relative to installed anchor");

        var bench = CompositeArchitecture.kindBlock("source_red_tulip_1");
        var seatState = bench.getDefaultState();
        if (StudioLayouts.pose(StudioLayouts.model(bench.spec), seatState) == null
                || StudioLayouts.pose(StudioLayouts.model(bench.spec), seatState).seats().isEmpty())
            seatState = seatState.with(CompositeRootBlock.VARIANT, 1);
        BlockPos seatRoot = new BlockPos(24, 74, 8);
        var seatOwner = place(world, seatRoot, seatState);
        var targets = BenchSeating.targets(world, seatRoot);
        check(!targets.isEmpty() && !targets.get(0).seats().isEmpty(), "authored block seats are targetable");
        var target = targets.get(0);
        var point = target.seats().get(0);
        actor.setPosition(point.contact().add(0, 0, -1));
        check(BenchSeating.sit(world, target, actor, point.contact()).isAccepted()
                && actor.getVehicle() instanceof BenchSeatEntity, "player mounts a reviewed seat");
        actor.stopRiding();
        check(!actor.hasVehicle(), "seat dismount");
        check(CompositeRuntime.remove(world, seatOwner, null, false).outcome() == TransactionCore.Outcome.COMMITTED, "remove seated object and its contributions");

        var rpType = Registries.ENTITY_TYPE.get(new Identifier("bloodborne_rp", "boat"));
        var boat = (RpObjectEntity) rpType.create(world);
        boat.refreshPositionAndAngles(40, 74, 8, 90, 0);
        check(world.spawnEntity(boat), "spawn test RP boat");
        var layout = StudioRpGeometry.layout(boat);
        check(layout != null && !layout.players().isEmpty() && layout.seats().size() == 3, "RP player collision and seats");
        check(boat.interactionBoxes().equals(layout.outline()), "RP selection uses authored outline");
        Box box = layout.players().get(0);
        check(!RpCollisionShapes.append(world, actor, box.expand(.01), List.of()).isEmpty(), "RP collision reaches player movement");
        check(!layout.blocks().isEmpty() && StudioPlacement.rpBlocks(world, layout.blocks().get(0), null), "RP reservations block construction");
        boat.discard();


        var nativeRecipe = StaticArchitectureCatalog.recipes().stream().filter(recipe ->
                recipe.key().startsWith("studio_") && StudioStaticShapes.of(recipe).outline() != null).findFirst().orElseThrow();
        check(StudioStaticShapes.of(nativeRecipe).players() != null
                && nativeRecipe.outlineShape() == StudioStaticShapes.of(nativeRecipe).outline(), "finite native recipe uses Studio shapes");
        check(!StaticArchitectureCatalog.recipes().isEmpty(), "native removal closures validated on server startup");

    }

    private void checkRevisionProps(ServerWorld world, ServerPlayerEntity actor) {
        String reference = System.getProperty("dw.studio.qa.rpRevision");
        if (reference == null) return;
        JsonArray models;
        try {
            models = JsonParser.parseString(Files.readString(Path.of(reference)))
                    .getAsJsonObject().getAsJsonArray("models");
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Missing RP revision test input", failure);
        }
        for (var entry : models) {
            var model = entry.getAsJsonObject();
            String id = model.get("registryId").getAsString();
            var type = Registries.ENTITY_TYPE.get(new Identifier(id));
            var prop = (RpObjectEntity) type.create(world);
            prop.refreshPositionAndAngles(40.5, 82, 8.5, 90, 0);
            check(world.spawnEntity(prop), "spawn " + id);
            StudioRpGeometry.Layout visibleDogs = null;
            for (var value : model.getAsJsonArray("states")) {
                var state = value.getAsJsonObject();
                var selector = state.getAsJsonObject("selector");
                String key = state.get("key").getAsString();
                if (selector.has("DogsVisible"))
                    prop.setDogsVisible(selector.get("DogsVisible").getAsBoolean());
                if (selector.has("Open")) {
                    var tag = prop.writeNbt(new NbtCompound());
                    tag.putBoolean("Open", selector.get("Open").getAsBoolean());
                    prop.readNbt(tag);
                }
                var layout = StudioRpGeometry.layout(prop);
                check(layout != null, "resolve " + id + " " + key);
                double scale = prop.asset().scale() * prop.objectScale();
                List<Box> players = new ArrayList<>(), blocks = new ArrayList<>(), outline = new ArrayList<>();
                for (var collider : state.getAsJsonArray("colliders")) {
                    var box = collider.getAsJsonObject();
                    String mode = box.get("mode").getAsString();
                    var center = prop.getPos().add(vector(box.getAsJsonArray("center")).multiply(scale / 16));
                    var half = vector(box.getAsJsonArray("size")).multiply(scale / 32);
                    var shape = new Box(center.subtract(half), center.add(half));
                    if (mode.equals("both") || mode.equals("players")) players.add(shape);
                    if (mode.equals("both") || mode.equals("blocks")) blocks.add(shape);
                    if (mode.equals("outline")) outline.add(shape);
                }
                var seats = state.getAsJsonArray("seats");
                check(sameBoxes(layout.players(), players) && sameBoxes(layout.blocks(), blocks)
                        && sameBoxes(layout.outline(), outline) && layout.seats().size() == seats.size()
                        && prop.interactionBoxes().equals(layout.outline()), "all collider modes and seats for " + id + " " + key);
                if (selector.has("DogsVisible")) {
                    if (prop.dogsVisible()) visibleDogs = layout;
                    else check(visibleDogs != null && sameBoxes(layout.players(), visibleDogs.players())
                            && sameBoxes(layout.blocks(), visibleDogs.blocks())
                            && sameBoxes(layout.outline(), visibleDogs.outline()), "shared dog cage colliders for " + id);
                }
                var offset = state.getAsJsonArray("modelOffset");
                var expected = vector(offset).multiply(scale / 16);
                check(StudioRpGeometry.renderOffset(prop, 90, 0).squaredDistanceTo(expected) < 1e-12,
                        "art offset for " + id + " " + key);
                if (!layout.blocks().isEmpty()) {
                    check(StudioPlacement.rpBlocks(world, layout.blocks().get(0), null),
                            "placement reservation for " + id + " " + key);
                }
                for (int i = 0; i < seats.size(); i++) {
                    var point = layout.seats().get(i).contact();
                    var surface = seats.get(i).getAsJsonObject().getAsJsonArray("surfaceCenter");
                    check(point.squaredDistanceTo(prop.getPos().add(vector(surface).multiply(scale / 16))) < 1e-12,
                            "seat surface for " + id + " " + key);
                }
            }
            if (id.endsWith(":chest")) {
                var tag = prop.writeNbt(new NbtCompound());
                tag.putBoolean("Open", true);
                prop.readNbt(tag);
                check(StudioRpGeometry.layout(prop) == null, "unlisted open chest retains stock geometry");
            }
            if (id.endsWith(":dog_cage")) {
                var point = StudioRpGeometry.layout(prop).seats().get(0).contact();
                actor.setPosition(point.x, point.y - actor.getStandingEyeHeight(), point.z + 2);
                actor.setYaw(180);
                actor.setPitch(0);
                check(prop.interact(actor, Hand.MAIN_HAND).isAccepted()
                        && actor.getVehicle() instanceof BenchSeatEntity, "right-click mounts a new RP seat");
                actor.stopRiding();
            }
            prop.discard();
        }
    }

    private static Vec3d vector(JsonArray array) {
        return new Vec3d(array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble());
    }

    private static boolean sameBoxes(List<Box> actual, List<Box> expected) {
        if (actual.size() != expected.size()) return false;
        for (int i = 0; i < actual.size(); i++) {
            Box a = actual.get(i), b = expected.get(i);
            if (new Vec3d(a.minX, a.minY, a.minZ).squaredDistanceTo(b.minX, b.minY, b.minZ) > 1e-16
                    || new Vec3d(a.maxX, a.maxY, a.maxZ).squaredDistanceTo(b.maxX, b.maxY, b.maxZ) > 1e-16) return false;
        }
        return true;
    }

    private void checkShutterOnOccupiedCell(ServerWorld world, ServerPlayerEntity actor) {
        BlockPos target = new BlockPos(206, 43, -913);
        for (int cx = 11; cx <= 13; cx++) for (int cz = -59; cz <= -57; cz++) world.getChunk(cx, cz);
        var occupied = staticCell(256, 53);
        world.setBlockState(target, occupied);
        world.setBlockState(target.up(), staticCell(256, 52));
        world.setBlockState(target.down(), staticCell(0, 20));
        world.setBlockState(target.west(), staticCell(256, 56));
        world.setBlockState(target.east(), staticCell(257, 8));
        BlockPos wall = target.north();
        world.setBlockState(wall, Blocks.STONE.getDefaultState());
        var hit = new BlockHitResult(new Vec3d(206.5, 43.5, -913), Direction.SOUTH, wall, false);
        var item = CompositeArchitecture.kindItem("source_shutter_window");
        var stack = new ItemStack(item, 2);
        actor.setStackInHand(Hand.MAIN_HAND, stack);
        actor.setPosition(206.5, 43, -910);
        actor.setYaw(0);
        check(NoCollidePlacement.arm(actor)
                && item.place(new ItemPlacementContext(actor, Hand.MAIN_HAND, stack, hit)).isAccepted(),
                "No Collide 93241 installs over the photographed occupied static cell");
        NoCollidePlacement.invalidate(actor);
        actor.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        var entity = world.getBlockEntity(target.south());
        check(entity instanceof CompositeBlockEntity && world.getBlockState(target).equals(occupied),
                "shutter takes the nearest free cell and leaves native stone intact");
        var owner = ((CompositeBlockEntity) entity).resident();
        clickShutter(world, actor, owner, target, true);
        clickShutter(world, actor, owner, target, false);
        check(world.getBlockState(target).equals(occupied), "both shutter poses preserve the native stone");
    }

    private static BlockState staticCell(int bank, int part) {
        return Registries.BLOCK.get(new Identifier("bloodborne_dw", "static_architecture_" + bank))
                .getDefaultState().with(StaticArchitectureBlock.PART, part);
    }

    private void clickShutter(ServerWorld world, ServerPlayerEntity actor, Owner owner, BlockPos position, boolean open) {
        var root = CompositeData.pos(owner.root());
        actor.setPosition(position.getX() + .5, position.getY() + 1.25 - actor.getStandingEyeHeight(), position.getZ() + 3.5);
        actor.setYaw(180);
        actor.setPitch(0);
        var hit = (BlockHitResult) actor.raycast(5, 1, false);
        check(hit.getType() == HitResult.Type.BLOCK && owner.equals(CompositeRuntime.target(world, hit.getBlockPos(), actor)),
                "right-click ray selects 93241 at " + position.toShortString());
        var result = UseBlockCallback.EVENT.invoker().interact(actor, world, Hand.MAIN_HAND, hit);
        if (result == ActionResult.PASS)
            result = world.getBlockState(hit.getBlockPos()).onUse(world, actor, Hand.MAIN_HAND, hit);
        var state = world.getBlockState(root);
        var transition = CompositeRuntime.lastResult();
        check(result.isAccepted() && state.get(CompositeRootBlock.OPEN) == open,
                "right-click " + (open ? "opens" : "closes") + " 93241 at " + position.toShortString()
                        + " (" + (transition == null ? "no transaction" : transition.reason()) + ")");
        var payload = ((CompositeBlockEntity) world.getBlockEntity(root)).payload();
        var art = ImportedStairLegacyArt.selectedParts((CompositeRootBlock) state.getBlock(), state, payload);
        check(art.stream().anyMatch(part -> part.model().getPath().endsWith(open ? "/shutter_1" : "/shutter_0")),
                "93241 selects the matching visual pose");
    }

    private void checkForcedPlacement(ServerWorld world, ServerPlayerEntity actor) {
        BlockPos clicked = new BlockPos(8, 86, 20);
        BlockPos requested = clicked.east();
        world.setBlockState(clicked, Blocks.STONE.getDefaultState());
        var item = CompositeArchitecture.kindItem("prototype_wood_window");
        var stack = new ItemStack(item, 8);
        stack.getOrCreateSubNbt("BlockStateTag").putString("open", "true");
        actor.setStackInHand(Hand.MAIN_HAND, stack);
        actor.setPosition(8, 86, 19);
        check(!item.place(placement(actor, stack, clicked)).isAccepted()
                && "missing_adequate_support".equals(CompositeRuntime.lastResult().reason()), "normal 90007 requires support");
        check(NoCollidePlacement.arm(actor), "arm the actual held 90007 stack");
        check(item.place(placement(actor, stack, clicked)).isAccepted(), "90007 installs unsupported through its item");
        var first = (CompositeBlockEntity) world.getBlockEntity(requested);
        var firstState = world.getBlockState(requested);
        check(first != null && firstState.get(CompositeRootBlock.OPEN)
                && first.payload().getBoolean(ForcedCompositePlacement.INDEPENDENT_SUPPORT), "open pose and support policy stored");
        NoCollidePlacement.invalidate(actor);
        CompositeRuntime.scheduleSupport(world, requested);
        CompositeRuntime.drain(world);
        check(world.getBlockEntity(requested) == first, "neighbor validation retains the floating panel");

        BlockPos otherFace = clicked.add(0, 0, 12);
        BlockPos occupied = otherFace.east();
        world.setBlockState(otherFace, Blocks.STONE.getDefaultState());
        world.setBlockState(occupied, Blocks.STONE.getDefaultState());
        actor.setPosition(8, 86, 31);
        check(NoCollidePlacement.arm(actor) && item.place(placement(actor, stack, otherFace)).isAccepted(),
                "90007 installs over an occupied cell");
        BlockPos storage = occupied.up();
        var second = (CompositeBlockEntity) world.getBlockEntity(storage);
        check(world.getBlockState(occupied).isOf(Blocks.STONE) && second != null,
                "occupied block preserved with a nearby storage cell");
        var before = CompositeSourceShift.world(firstState, first.payload());
        var after = CompositeSourceShift.world(world.getBlockState(storage), second.payload());
        check(Math.abs(before.x() - after.x()) < 1e-8 && Math.abs(before.y() - after.y() - 1) < 1e-8
                && Math.abs(before.z() - after.z()) < 1e-8, "alternate storage preserves the requested world position");

        var pig = EntityType.PIG.create(world);
        pig.refreshPositionAndAngles(9.5, 86, 30.5, 0, 0);
        check(world.spawnEntity(pig), "spawn an obstruction");
        var obstruction = List.of(pig.getBoundingBox());
        check(PlacementPhysics.entityConflict(world, obstruction, null, false) != null, "ordinary placement sees the entity");
        boolean entered = NoCollidePlacement.enter(actor);
        try {
            check(entered && PlacementPhysics.entityConflict(world, obstruction, null, false) == null,
                    "forced placement permits entity overlap");
        } finally {
            NoCollidePlacement.exit(actor, entered, false);
            NoCollidePlacement.invalidate(actor);
            pig.discard();
        }
        check(!NoCollidePlacement.ignoringBlocks(), "placement scope unwinds");
        check(CompositeRuntime.remove(world, second.resident(), null, false).outcome() == TransactionCore.Outcome.COMMITTED
                && world.getBlockState(occupied).isOf(Blocks.STONE), "removal restores the occupied cell");
    }

    private static ItemPlacementContext placement(ServerPlayerEntity actor, ItemStack stack, BlockPos clicked) {
        var hit = new BlockHitResult(new Vec3d(clicked.getX() + 1, clicked.getY() + .5, clicked.getZ() + .5),
                Direction.EAST, clicked, false);
        return new ItemPlacementContext(actor, Hand.MAIN_HAND, stack, hit);
    }

    private dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner place(ServerWorld world, BlockPos pos, BlockState state) {
        var id = UUID.randomUUID();
        var result = SourceConversionScope.initialInstances(Set.of(id), () -> CompositeRuntime.place(world, pos, state, id, null));
        check(result.outcome() == TransactionCore.Outcome.COMMITTED, "place " + state.getBlock() + " at " + pos + " " + result.reason());
        return ((CompositeBlockEntity) world.getBlockEntity(pos)).resident();
    }
}
