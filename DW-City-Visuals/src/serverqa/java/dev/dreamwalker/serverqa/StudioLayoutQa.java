package dev.dreamwalker.serverqa;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornedw.architecture.RpCollisionShapes;
import dev.dreamwalker.bloodbornedw.architecture.SourceConversionScope;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Test-only gameplay exercise using the actual Fabric server and owner ledger. */
public final class StudioLayoutQa implements ModInitializer {
    private final JsonObject report = new JsonObject();
    private final JsonArray passed = new JsonArray();
    private Path output;
    private boolean ran;

    @Override public void onInitialize() {
        String file = System.getProperty("dw.studio.qa");
        if (file == null) return;
        if (!Files.isRegularFile(Path.of("disposable-studio-world.marker")))
            throw new IllegalStateException("Studio QA requires a disposable world");
        output = Path.of(file);
        report.add("passed", passed);
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> new Thread(
                () -> System.exit(report.has("error") ? 1 : 0), "DW-Studio-QA-exit").start());
    }

    private void tick(MinecraftServer server) {
        if (ran) return;
        ran = true;
        try {
            run(server.getOverworld());
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

    private void run(ServerWorld world) {
        for (int cx = -2; cx <= 3; cx++) for (int cz = -2; cz <= 2; cz++) world.getChunk(cx, cz);
        var legacy = new Identifier("bloodborne_dw", "source_spruce_trapdoor_bottom");
        check(!Registries.BLOCK.containsId(legacy) && !Registries.ITEM.containsId(legacy), "90952 block and item retired");
        var actor = new ServerPlayerEntity(world.getServer(), world, new GameProfile(UUID.randomUUID(), "StudioQA"));
        actor.networkHandler = new ServerPlayNetworkHandler(world.getServer(), new ClientConnection(NetworkSide.SERVERBOUND), actor);
        actor.setPosition(8, 74, 7);

        var window = CompositeArchitecture.kindBlock("source_shutter_window");
        BlockPos root = new BlockPos(8, 74, 8);
        var closed = window.getDefaultState();
        var closedLayout = StudioFootprints.dynamic(CompositeData.data(closed, new net.minecraft.nbt.NbtCompound()));
        check(closedLayout != null && !closedLayout.players().isEmpty() && !closedLayout.outline().isEmpty(), "closed shutter authored shapes");
        var owner = place(world, root, closed);
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
        check(CompositeRuntime.transition(world, owner, opened, null).outcome() == TransactionCore.Outcome.COMMITTED
                && world.getBlockState(root).get(CompositeRootBlock.OPEN), "open placed shutter");
        check(CompositeRuntime.transition(world, owner, closed, null).outcome() == TransactionCore.Outcome.COMMITTED, "close placed shutter");
        check(CompositeRuntime.remove(world, owner, null, false).outcome() == TransactionCore.Outcome.COMMITTED, "remove the complete shutter");

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

    private dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner place(ServerWorld world, BlockPos pos, BlockState state) {
        var id = UUID.randomUUID();
        var result = SourceConversionScope.initialInstances(Set.of(id), () -> CompositeRuntime.place(world, pos, state, id, null));
        check(result.outcome() == TransactionCore.Outcome.COMMITTED, "place " + state.getBlock() + " at " + pos + " " + result.reason());
        return ((CompositeBlockEntity) world.getBlockEntity(pos)).resident();
    }
}
