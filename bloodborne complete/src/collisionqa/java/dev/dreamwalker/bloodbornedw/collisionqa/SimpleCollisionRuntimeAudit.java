package dev.dreamwalker.bloodbornedw.collisionqa;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornedw.compat.collision.*;
import dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.block.*;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.network.*;
import net.minecraft.network.packet.Packet;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
import net.minecraft.world.*;
import net.minecraft.world.gen.chunk.FlatChunkGenerator;

/** Deliberately installed QA only. No production entrypoint or source-map access. */
public final class SimpleCollisionRuntimeAudit implements ModInitializer {
    private static Audit audit;
    private static SavedImportedCollisionRuntimeAudit savedAudit;
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            Path previous = server.getSavePath(WorldSavePath.ROOT).resolve("gallery-manifest.json");
            if (Files.isRegularFile(previous)) {
                try { savedAudit = new SavedImportedCollisionRuntimeAudit(server.getOverworld(), previous); }
                catch (IOException failure) { throw new UncheckedIOException(failure); }
                return;
            }
            if (!server.getSaveProperties().getLevelName().equals("isolated-smoke-world") ||
                    !(server.getOverworld().getChunkManager().getChunkGenerator() instanceof FlatChunkGenerator))
                throw new IllegalStateException("Collision audit requires a fresh isolated flat fixture");
            audit = new Audit(server.getOverworld());
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (savedAudit != null && !savedAudit.finished) {
                try { savedAudit.tick(); } catch (Throwable failure) { savedAudit.fail(failure); }
                return;
            }
            if (audit == null || audit.finished) return;
            try { audit.tick(); } catch (Throwable failure) { audit.fail(failure); }
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static JsonObject selector(int variant, String profile, boolean open, int rotation) {
        JsonObject s = new JsonObject(); s.addProperty("variant", Integer.toString(variant));
        s.addProperty("profile", profile); s.addProperty("open", Boolean.toString(open));
        s.addProperty("rotation", Integer.toString(rotation)); return s;
    }

    private static final class Audit {
        final ServerWorld world;
        final List<CompositeRootBlock> added;
        final List<Runnable> cases = new ArrayList<>();
        final JsonObject result = new JsonObject();
        final JsonArray movement = new JsonArray();
        final Map<String, JsonObject> documents = new HashMap<>();
        final Map<String, CompositeSpec> originals = new HashMap<>();
        final JsonObject originalDocuments = originalDocuments();
        int index, geometryCases, variantCount, emptyCases, ticks;
        boolean finished;
        Audit(ServerWorld world) {
            this.world = world;
            added = CompositeArchitecture.blocks().stream().filter(b -> b.spec.id.getPath().startsWith("source_")).sorted(Comparator.comparing(b -> b.spec.id.toString())).toList();
            result.addProperty("schema", "dreamwalker-v122-simple-collision-runtime-v1");
            result.addProperty("status", "RUNNING");
            result.addProperty("method", "Actual Minecraft dedicated server registry, CompositeSpec footprint, ordinary player movement, placement/copy/typed NBT/removal. No real author export and no physical keyboard/mouse input.");
            require(added.size() == 1045, "Expected 1045 surviving NEW registry types, got " + added.size());
            for (String retired : List.of("source_jungle_trapdoor_bottom", "source_jungle_trapdoor_open")) {
                Identifier id = new Identifier("bloodborne_dw", retired);
                require(!Registries.BLOCK.containsId(id) && !Registries.ITEM.containsId(id), "Retired red object remains registered: " + id);
            }
            for (var block : added) for (int variant = 0; variant < block.spec.variants.size(); variant++) {
                final int v = variant; variantCount++;
                cases.add(() -> geometry(block, v));
            }
            for (var block : added) if (!block.spec.id.getPath().startsWith("source_"))
                throw new AssertionError("Protected V12 object entered NEW fixture");
            result.addProperty("newRegistryCount", added.size()); result.addProperty("offeredVariantCount", variantCount);
            write();
        }
        void tick() throws Exception {
            ticks++;
            // Bounded work; exhaustive state geometry is independent of fixture placement.
            for (int n = 0; n < 4 && index < cases.size(); n++) cases.get(index++).run();
            if (index % 64 == 0) write();
            if (index < cases.size()) return;
            contract();
            mixedPhysicalControl();
            representativeFixtures();
            finished = true;
            result.addProperty("status", "PASS"); result.addProperty("exactGeometryStates", geometryCases);
            result.addProperty("physicallyEmptyStates", emptyCases); result.addProperty("ticks", ticks);
            result.addProperty("realAuthorReplacement", "NOT_APPLIED_EXPORT_NOT_SUPPLIED");
            result.addProperty("manualMouseKeyboard", "NOT_RUN"); result.add("movementAndTransactions", movement);
            world.getServer().save(false, true, true); write();
        }
        void geometry(CompositeRootBlock block, int variant) {
            for (boolean open : new boolean[]{false, true}) for (int yaw = 0; yaw < 8; yaw++) {
                BlockState state = block.getDefaultState().with(CompositeRootBlock.VARIANT, variant).with(CompositeRootBlock.OPEN, open).with(CompositeRootBlock.ROTATION, yaw);
                var physical = expected(physicalInput(block, variant, open, yaw), 0, 0);
                VoxelShape expected = shape(physical), actual = VoxelShapes.empty();
                var footprint = block.spec.footprint(state, 0);
                require(footprint.containsKey(Cell.ORIGIN), "Root reservation missing " + block.spec.id);
                VoxelShape rootShape = localShape(footprint.get(Cell.ORIGIN).collision());
                require(!VoxelShapes.matchesAnywhere(rootShape, CompositeRuntime.cellShape(world, new BlockPos(512, 128, 512), true, state, ShapeContext.absent()), BooleanBiFunction.NOT_SAME), "NEW root fallback enlarged the ordinary model-bounded collider " + block.spec.id + " yaw=" + yaw);
                boolean selectable = false;
                for (var entry : footprint.entrySet()) {
                    selectable |= !entry.getValue().selection().isEmpty();
                    for (var box : entry.getValue().collision()) {
                        require(box.minX() >= -1e-8 && box.minY() >= -1e-8 && box.minZ() >= -1e-8 && box.maxX() <= 1 + 1e-8 && box.maxY() <= 1 + 1e-8 && box.maxZ() <= 1 + 1e-8, "Unclipped physical contribution " + block.spec.id);
                        actual = VoxelShapes.union(actual, VoxelShapes.cuboid(box.minX() + entry.getKey().x(), box.minY() + entry.getKey().y(), box.minZ() + entry.getKey().z(), box.maxX() + entry.getKey().x(), box.maxY() + entry.getKey().y(), box.maxZ() + entry.getKey().z()));
                    }
                }
                require(selectable, "Source geometry lost selection " + block.spec.id);
                require(!VoxelShapes.matchesAnywhere(expected, actual, BooleanBiFunction.NOT_SAME), "Runtime collision differs from independent rotate/six-face-clip oracle " + block.spec.id + " variant=" + variant + " open=" + open + " yaw=" + yaw);
                for (var profile : CompositeRootBlock.Profile.values()) {
                    var profiled = state.with(CompositeRootBlock.PROFILE, profile);
                    require(block.spec.footprint(profiled, 0).equals(footprint), "Profile changes frozen collision/selection " + block.spec.id);
                    require(CollisionReplacements.query(block.spec.id.toString(), selector(variant, profile.asString(), open, yaw)).isEmpty(), "Unexpected author replacement installed");
                    geometryCases++; if (actual.isEmpty()) emptyCases++;
                }
            }
        }
        void contract() throws Exception {
            JsonObject root = JsonParser.parseString("{\"schema\":\"dreamwalker.player-collision-project\",\"schemaVersion\":1,\"unitsPerBlock\":16,\"coordinates\":{\"rotationOrder\":\"ZYX\"},\"policy\":{\"scope\":\"players_only\",\"operation\":\"replace_per_exact_state\",\"unlistedStates\":\"preserve\"},\"source\":{\"repository\":\"QA_ONLY_SYNTHETIC\",\"commit\":\"QA_ONLY\",\"release\":\"QA_ONLY\"},\"seatSize\":[9.6,1.6,9.6],\"models\":[]}").getAsJsonObject();
            JsonObject model = new JsonObject(); model.addProperty("registryId", added.get(0).spec.id.toString());
            JsonObject row = new JsonObject(); row.addProperty("key", "qa-empty-base"); row.addProperty("replace", true); row.addProperty("reviewed", true);
            row.add("selector", selector(0, "base", false, 0)); row.add("colliders", new JsonArray()); row.add("seats", new JsonArray());
            JsonArray states = new JsonArray(); states.add(row); model.add("states", states); root.getAsJsonArray("models").add(model);
            CollisionPack pack = CollisionPack.read(new StringReader(root.toString()));
            require(pack.replacement(added.get(0).spec.id.toString(), selector(0, "base", false, 0)).isPresent(), "Exact reviewed empty replacement not preserved");
            require(pack.replacement(added.get(0).spec.id.toString(), selector(0, "alt", false, 0)).isEmpty(), "BASE replacement leaked to ALT");
            var numeric = selector(0, "base", false, 0); numeric.addProperty("variant", 0);
            require(pack.replacement(added.get(0).spec.id.toString(), numeric).isEmpty(), "Selector string silently matched number");
            var extended = selector(0, "base", false, 0); extended.addProperty("waterlogged", "false");
            require(pack.replacement(added.get(0).spec.id.toString(), extended).isEmpty(), "Selector accepted unrelated extra property");
            require(pack.replacement("bloodborne_dw:missing_registry", selector(0, "base", false, 0)).isEmpty(), "Unknown ID matched");
            result.addProperty("exactReplacementParserSyntheticContract", "PASS_DATA_ONLY_NOT_AUTHOR_IMPORT");
        }
        void mixedPhysicalControl() {
            BlockPos probe = new BlockPos(512, 128, 512); world.getChunk(probe);
            var oldBox = new dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box(.11, .25, .11, .21, .375, .21);
            var newBox = new dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box(.64, .25, .64, .83, .375, .83);
            Owner oldOwner = new Owner(UUID.randomUUID(), "bloodborne_dw:prototype_roof", new Cell(8192, 128, 8192));
            Owner newOwner = new Owner(UUID.randomUUID(), "bloodborne_dw:source_bricks", new Cell(8208, 128, 8192));
            require(!world.isChunkLoaded(CompositeData.pos(oldOwner.root())) && !world.isChunkLoaded(CompositeData.pos(newOwner.root())), "Mixed deferred-root fixture accidentally loads owner roots");
            var old = new CompositeData.Contribution(oldOwner, CompositeData.data(Registries.BLOCK.get(new Identifier(oldOwner.registryId())).getDefaultState(), new NbtCompound()), new dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Footprint(List.of(oldBox), List.of(oldBox)));
            var added = new CompositeData.Contribution(newOwner, CompositeData.data(Registries.BLOCK.get(new Identifier(newOwner.registryId())).getDefaultState(), new NbtCompound()), new dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Footprint(List.of(newBox), List.of(newBox)));
            var ledger = CompositeLedger.get(world); Cell cell = CompositeData.cell(probe); var previous = ledger.at(cell);
            try {
                ledger.put(cell, List.of(old, added));
                VoxelShape expected = VoxelShapes.union(VoxelShapes.cuboid(.0625, .25, .0625, .25, .375, .25), VoxelShapes.cuboid(.64, .25, .64, .83, .375, .83));
                VoxelShape actual = CompositeRuntime.cellShape(world, probe, true, Blocks.AIR.getDefaultState(), ShapeContext.absent());
                require(!VoxelShapes.matchesAnywhere(expected, actual, BooleanBiFunction.NOT_SAME), "Mixed old/new collision lost old quantization or enlarged NEW fractional bounds");
                VoxelShape selection = CompositeRuntime.cellShape(world, probe, false, Blocks.AIR.getDefaultState(), ShapeContext.absent());
                // The synthetic NEW membership is far from its real owner and
                // had no historical outline there; old owner raster stays exact.
                VoxelShape oldSelectionPolicy = VoxelShapes.cuboid(.0625, .25, .0625, .25, .375, .25);
                require(!VoxelShapes.matchesAnywhere(selection, oldSelectionPolicy, BooleanBiFunction.NOT_SAME), "NEW exact physical path changed mixed selection policy");
                BlockPos entityProbe = new BlockPos(0, 128, 0); Cell entityCell = CompositeData.cell(entityProbe);
                var obstacle = new ArmorStandEntity(EntityType.ARMOR_STAND, world); obstacle.setNoGravity(true);
                obstacle.refreshPositionAndAngles(entityProbe.getX() + .379, entityProbe.getY() + .25, entityProbe.getZ() + .735, 0, 0);
                require(obstacle.intersectionChecked && world.spawnEntity(obstacle), "Native fractional entity witness must block ordinary placement");
                try {
                    require(world.getOtherEntities(null, obstacle.getBoundingBox()).contains(obstacle), "Fractional placement witness is not visible in the actual native entity query");
                    var newIncoming = new dev.dreamwalker.bloodbornedw.runtime.ObjectInstance(newOwner, added.rootData(), Map.of(Cell.ORIGIN, added.shape()));
                    var oldIncoming = new dev.dreamwalker.bloodbornedw.runtime.ObjectInstance(oldOwner, old.rootData(), Map.of(Cell.ORIGIN, added.shape()));
                    require(!new FabricCompositeWorld(world, Map.of(newOwner, newIncoming)).intersectsEntities(entityCell, added.shape()), "NEW placement still enlarges fractional entity collision");
                    require(new FabricCompositeWorld(world, Map.of(oldOwner, oldIncoming)).intersectsEntities(entityCell, added.shape()), "Protected OLD placement raster changed at a fractional entity boundary");
                    result.addProperty("fractionalEntityPlacementPolicy", "PASS_NATIVE_ARMOR_STAND_NEW_EXACT_OLD_RASTER");
                } finally { obstacle.discard(); }
                result.addProperty("mixedLegacyAndNewFractionalCollision", "PASS_LEGACY_RASTER_NEW_EXACT_SELECTION_UNCHANGED_NO_ROOT_CHUNK_LOAD");
            } finally { ledger.put(cell, previous); }
        }
        void representativeFixtures() {
            LinkedHashMap<String, CompositeRootBlock> chosen = new LinkedHashMap<>();
            for (String path : List.of("source_bricks", "source_addon_grass_0", "source_potted_fern", "source_potted_flowering_azalea_bush", "source_hold_ladder_01", "source_hold_wood_ladder_01", "source_hold_ladder_02", "source_hold_ladder_03", "source_deepslate_iron_ore", "source_acacia_fence_gate", "source_bell_floor", "source_stone_stairs", "source_stone_stairs_inner", "source_stone_stairs_outer"))
                added.stream().filter(b -> b.spec.id.getPath().equals(path)).findFirst().ifPresent(b -> chosen.put(path, b));
            added.stream().filter(b -> b.spec.openable).findFirst().ifPresent(b -> chosen.put("openable", b));
            added.stream().filter(b -> b.spec.variants.get(0).closed().collision().isEmpty()).findFirst().ifPresent(b -> chosen.put("passable", b));
            for (var entry : chosen.entrySet()) for (int yaw : new int[]{0, 1, 2, 7}) {
                fixture(entry.getKey(), entry.getValue(), 0, yaw, false);
                if (entry.getValue().spec.openable) fixture(entry.getKey() + "_open", entry.getValue(), 0, yaw, true);
            }
            for (var block : chosen.values()) if (block.spec.importedClimbingBounds != null)
                fixture("mounted_shifted_ladder", block, 0, 1, false, true);
        }
        void fixture(String role, CompositeRootBlock block, int variant, int yaw, boolean opened) {
            fixture(role, block, variant, yaw, opened, false);
        }
        void fixture(String role, CompositeRootBlock block, int variant, int yaw, boolean opened, boolean shifted) {
            BlockPos root = new BlockPos(512, 128, 512);
            BlockState state = block.getDefaultState().with(CompositeRootBlock.VARIANT, variant).with(CompositeRootBlock.ROTATION, yaw).with(CompositeRootBlock.OPEN, opened);
            var footprint = block.spec.footprint(state, 0);
            Set<Long> chunks = new HashSet<>();
            for (var cell : footprint.keySet()) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                BlockPos position = root.add(cell.x() + dx, cell.y(), cell.z() + dz);
                long key = new ChunkPos(position).toLong(); if (chunks.add(key)) { world.setChunkForced(position.getX() >> 4, position.getZ() >> 4, true); world.getChunk(position); }
            }
            int minX = footprint.keySet().stream().mapToInt(Cell::x).min().orElse(0) - 4, maxX = footprint.keySet().stream().mapToInt(Cell::x).max().orElse(0) + 4;
            int minZ = footprint.keySet().stream().mapToInt(Cell::z).min().orElse(0) - 4, maxZ = footprint.keySet().stream().mapToInt(Cell::z).max().orElse(0) + 4;
            for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
                BlockPos floor = root.add(x, -1, z); world.getChunk(floor);
                if (!world.getBlockState(floor).isOf(Blocks.STONE)) world.setBlockState(floor, Blocks.STONE.getDefaultState(), Block.FORCE_STATE | Block.SKIP_DROPS);
            }
            var player = player(world); player.refreshPositionAndAngles(0, 128, 0, 0, 0);
            NbtCompound payload = new NbtCompound(); payload.putLong("QA_long", Long.MIN_VALUE); payload.putString("QA_text", "новые коллизии");
            if (shifted) {
                NbtList shift = new NbtList(); shift.add(NbtDouble.of(.125)); shift.add(NbtDouble.of(.125)); shift.add(NbtDouble.of(.125));
                payload.put("SourceShift", shift); payload.putDouble(VerticalMount.KEY, .25);
                var moved = CompositeRuntime.instance(world, root, state, UUID.randomUUID(), payload);
                for (var cell : moved.cells().keySet()) world.getChunk(root.add(cell.x(), cell.y(), cell.z()));
            }
            var placement = CompositeRuntime.place(world, root, state, UUID.randomUUID(), player, payload);
            require(placement.outcome() == TransactionCore.Outcome.COMMITTED, "Representative placement rejected " + block.spec.id + " yaw=" + yaw + ": " + placement);
            var entity = (CompositeBlockEntity) world.getBlockEntity(root); Owner owner = entity.resident();
            require(owner != null && entity.payload().equals(payload), "Placement lost typed payload");
            var placedGeometry = CompositeRuntime.instance(world, root, state, owner.instanceId(), entity.payload());
            CompositeSpec oldSpec = original(block);
            var oldFootprint = CompositeSourceShift.footprint(oldSpec, state, entity.payload());
            boolean oldPhysical = oldSpec.footprint(state, 0).values().stream().anyMatch(f -> !f.collision().isEmpty());
            Set<Cell> aimingCells = new TreeSet<>(placedGeometry.cells().keySet()); aimingCells.addAll(oldFootprint.keySet());
            for (Cell offset : aimingCells) {
                var oldPart = oldFootprint.get(offset);
                var aimingBoxes = oldPart == null ? List.<dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box>of() : oldPhysical ? oldPart.collision() : oldPart.selection();
                BlockPos cell = root.add(offset.x(), offset.y(), offset.z());
                BlockState carrier = world.getBlockState(cell);
                VoxelShape oldShape = CompositeRuntime.shape(aimingBoxes), nativeShape = CompositeRuntime.cellShape(world, cell, false, carrier, ShapeContext.of(player));
                require(!VoxelShapes.matchesAnywhere(oldShape, nativeShape, BooleanBiFunction.NOT_SAME), "NEW native aiming changed from frozen previous release " + block.spec.id + " yaw=" + yaw + " cell=" + offset);
                java.util.concurrent.atomic.AtomicReference<Throwable> failed = new java.util.concurrent.atomic.AtomicReference<>();
                Thread worker = new Thread(() -> { try {
                    require(!VoxelShapes.matchesAnywhere(oldShape, CompositeRuntime.cellShape(world, cell, false, carrier, ShapeContext.absent()), BooleanBiFunction.NOT_SAME), "Worker snapshot lost original NEW aiming");
                } catch (Throwable t) { failed.set(t); } }, "collision-qa-shape-worker");
                worker.start(); try { worker.join(); } catch (InterruptedException e) { throw new RuntimeException(e); }
                require(failed.get() == null, "Worker original aiming mismatch " + failed.get());
            }
            for (var entry : placedGeometry.cells().entrySet()) {
                BlockPos cell = root.add(entry.getKey().x(), entry.getKey().y(), entry.getKey().z());
                VoxelShape actual = CompositeRuntime.cellShape(world, cell, true, world.getBlockState(cell), ShapeContext.of(player));
                require(!VoxelShapes.matchesAnywhere(localShape(entry.getValue().collision()), actual, BooleanBiFunction.NOT_SAME), "Placed NEW root/helper physical shape enlarged raw ordinary cuboid " + block.spec.id + " yaw=" + yaw + " cell=" + entry.getKey());
            }
            var loaded = new CompositeBlockEntity(root, state); loaded.readNbt(entity.createNbt());
            require(loaded.resident().equals(owner) && loaded.payload().equals(entity.payload()) && loaded.contributions().equals(entity.contributions()), "Typed NBT roundtrip failed");
            ItemStack copy = CompositeRuntime.pick(world, owner);
            require(copy.isOf(block.asItem()) && copy.getSubNbt("BlockStateTag").getString("variant").equals(Integer.toString(variant)) && copy.getSubNbt("BlockEntityTag") == null && copy.getSubNbt("CompositePayload") == null, "Pick lost art or copied ownership");
            var shift = CompositeSourceShift.world(state, payload);
            List<Box> expected = expected(physicalInput(block, variant, opened, yaw), 0, payload.getDouble(VerticalMount.KEY), shift.x(), shift.y(), shift.z(), true).stream().map(b -> b.offset(root)).toList();
            JsonObject row = new JsonObject(); row.addProperty("role", role); row.addProperty("registryId", block.spec.id.toString()); row.addProperty("rotation", yaw); row.addProperty("open", opened);
            row.addProperty("originalNativeAndWorkerAiming", "PASS_FROZEN_PREVIOUS_RELEASE_GEOMETRY");
            if (yaw == 0 && block.spec.id.getPath().equals("source_potted_fern")) {
                Vec3d eye = new Vec3d(root.getX() + .5, root.getY() + 2, root.getZ() + .5), end = eye.add(0, -3, 0);
                var hit = CompositeRuntime.cellShape(world, root, false, state, ShapeContext.of(player)).raycast(eye, end, root);
                require(hit != null && Math.abs(hit.getPos().y - root.getY() - .25) < 1e-9, "Fern original hollow-pot centre aiming was simplified with physics");
                player.refreshPositionAndAngles(root.getX() + .5, root.getY() + 2 - player.getStandingEyeHeight(), root.getZ() + .5, 0, 90);
                require(owner.equals(CompositeRuntime.target(world, root, player)), "Original fern ray lost its owner/item picking");
                row.addProperty("fernOriginalCentreRayY", hit.getPos().y - root.getY());
                row.addProperty("fernPhysicalTopY", placedGeometry.cells().get(Cell.ORIGIN).collision().stream().mapToDouble(b -> b.maxY()).max().orElseThrow());
            }
            if (expected.isEmpty()) {
                player.refreshPositionAndAngles(root.getX() - 1, root.getY(), root.getZ() + .5, 0, 0);
                double start = player.getX(); player.move(MovementType.SELF, new Vec3d(2, 0, 0));
                require(Math.abs(player.getX() - start - 2) < 1e-6, "Passable decoration blocked ordinary player movement");
                row.addProperty("passThrough", "PASS_ACTUAL_PLAYER_MOVE");
            } else {
                var left = expected.stream().min(Comparator.comparingDouble(b -> b.minX)).orElseThrow();
                player.refreshPositionAndAngles(left.minX - 1, left.minY + Math.min(.05, (left.maxY - left.minY) / 2), (left.minZ + left.maxZ) / 2, 0, 0);
                // Isolate the side collider from ordinary automatic step-up;
                // step-up is separately exercised on the actual stairs below.
                float ordinaryStep = player.getStepHeight(); player.setStepHeight(0);
                player.setOnGround(false);
                double start = player.getX(); double proposed = Math.max(2, left.maxX - left.minX + 2);
                double allowed = VoxelShapes.calculateMaxOffset(Direction.Axis.X, player.getBoundingBox(), List.of(shape(expected)), proposed);
                player.move(MovementType.SELF, new Vec3d(proposed, 0, 0));
                require(Math.abs(player.getX() - start - allowed) < 1e-6 && allowed < proposed, "Actual movement failed simplified collider " + block.spec.id + " yaw=" + yaw + " expected=" + allowed + " actual=" + (player.getX() - start) + " feetY=" + player.getY() + " onGround=" + player.isOnGround() + " rootCollision=" + world.getBlockState(root).getCollisionShape(world, root, ShapeContext.of(player)).getBoundingBoxes());
                player.setStepHeight(ordinaryStep);
                row.addProperty("sideCollision", "PASS_ACTUAL_PLAYER_MOVE"); row.addProperty("allowedX", allowed);
                if (yaw == 0 && Set.of("source_potted_fern", "source_potted_flowering_azalea_bush").contains(block.spec.id.getPath())) {
                    player.refreshPositionAndAngles(left.minX - 1, left.minY, (left.minZ + left.maxZ) / 2, 0, 0); player.setOnGround(true);
                    double stepX = player.getX(), stepY = player.getY(), ontoPot = 1 + (left.maxX - left.minX) / 2; player.move(MovementType.SELF, new Vec3d(ontoPot, 0, 0));
                    require(Math.abs(player.getX() - stepX - ontoPot) < 1e-6 && player.getY() > stepY + .1, "Ordinary player cannot step onto the low physical pot: requested=" + ontoPot + " actualX=" + (player.getX() - stepX) + " actualY=" + (player.getY() - stepY));
                    row.addProperty("lowPotOrdinaryStep", "PASS_ACTUAL_PLAYER_MOVE_WITH_DEFAULT_STEP_HEIGHT");
                    row.addProperty("lowPotStepDeltaX", player.getX() - stepX); row.addProperty("lowPotStepDeltaY", player.getY() - stepY);
                }
                var top = expected.stream().max(Comparator.comparingDouble(b -> b.maxY)).orElseThrow();
                player.refreshPositionAndAngles((top.minX + top.maxX) / 2, top.maxY + .25, (top.minZ + top.maxZ) / 2, 0, 0);
                double startY = player.getY(); double allowedY = VoxelShapes.calculateMaxOffset(Direction.Axis.Y, player.getBoundingBox(), List.of(shape(expected)), -.5);
                player.move(MovementType.SELF, new Vec3d(0, -.5, 0));
                require(Math.abs(player.getY() - startY - allowedY) < 1e-6 && allowedY > -.5, "Platform did not support actual player " + block.spec.id);
                row.addProperty("topSupport", "PASS_ACTUAL_PLAYER_MOVE");
            }
            if (block.spec.importedClimbingBounds != null) {
                var zone = expected(List.of(block.spec.importedClimbingBounds), yaw, payload.getDouble(VerticalMount.KEY), shift.x(), shift.y(), shift.z(), false).get(0).offset(root);
                player.refreshPositionAndAngles((zone.minX + zone.maxX) / 2, zone.minY + .05, (zone.minZ + zone.maxZ) / 2, 0, 0);
                require(player.isClimbing(), "Imported ladder does not expose actual LivingEntity climbing yaw=" + yaw);
                double beforeY = player.getY(); player.setVelocity(Vec3d.ZERO); player.setJumping(true);
                for (int n = 0; n < 3; n++) player.travel(Vec3d.ZERO);
                require(player.getY() > beforeY + .05, "Imported ladder does not support actual ascent");
                row.addProperty("actualAscentDeltaY", player.getY() - beforeY);
                player.setJumping(false); row.addProperty("climbingAndAscent", "PASS_ACTUAL_LIVING_ENTITY_TRAVEL");
                row.addProperty("heightAndSourceShift", shifted ? "PASS" : "ZERO_OFFSET");
            }
            if (Set.of("source_deepslate_iron_ore", "source_acacia_fence_gate").contains(block.spec.id.getPath())) {
                double a = yaw * Math.PI / 4, s = Math.sin(a), c = Math.cos(a);
                player.refreshPositionAndAngles(root.getX() + .5 + 1.5 * s, root.getY() + .05, root.getZ() + .5 - 1.5 * c, 0, 0);
                // Vanilla resolves one move's axes separately. Tick-sized
                // increments follow the actual straight walking lane; a single
                // three-block diagonal request would sweep an artificial L.
                Vec3d before = player.getPos();
                for (int n = 0; n < 60; n++) player.move(MovementType.SELF, new Vec3d(-.05 * s, 0, .05 * c));
                row.addProperty("passageDeltaX", player.getX() - before.x); row.addProperty("passageDeltaZ", player.getZ() - before.z);
                require(Math.abs(player.getX() - before.x + 3 * s) < 1e-6 && Math.abs(player.getZ() - before.z - 3 * c) < 1e-6, "Simple arch blocks its functional central passage " + block.spec.id + " yaw=" + yaw + " delta=" + player.getPos().subtract(before));
                row.addProperty("centralPassage", "PASS_ACTUAL_PLAYER_MOVE");
            }
            if (yaw == 0 && block.spec.id.getPath().equals("source_potted_flowering_azalea_bush")) {
                player.refreshPositionAndAngles(root.getX() - 1, root.getY() + .5, root.getZ() + .5, 0, 0);
                double beforeX = player.getX(); player.move(MovementType.SELF, new Vec3d(2, 0, 0));
                require(Math.abs(player.getX() - beforeX - 2) < 1e-6, "Decorative foliage above physical pot blocks player");
                row.addProperty("foliagePassage", "PASS_ACTUAL_PLAYER_MOVE");
            }
            if (yaw == 0 && Set.of("source_hold_ladder_01", "source_hold_wood_ladder_01").contains(block.spec.id.getPath())) {
                player.refreshPositionAndAngles(root.getX() + .5, root.getY(), root.getZ() - 1.3, 0, 0);
                for (int step = 0; step < 25; step++) { player.setOnGround(true); player.move(MovementType.SELF, new Vec3d(0, -.01, .12)); }
                double landing = expected.stream().mapToDouble(b -> b.maxY).max().orElseThrow();
                require(player.getZ() > root.getZ() + 1 && player.getY() >= landing - .05, "Simple treads do not permit ordinary ascent to landing " + block.spec.id);
                row.addProperty("actualStairPlayerY", player.getY()); row.addProperty("stairLandingY", landing);
                row.addProperty("stairAscentToLanding", "PASS_ACTUAL_PLAYER_MOVE");
            }
            if (Set.of("source_stone_stairs", "source_stone_stairs_inner", "source_stone_stairs_outer").contains(block.spec.id.getPath())) {
                // Canonical vanilla straight/outer ascend east; the inner
                // corner's low tread is approached diagonally from the NW.
                boolean inner = block.spec.id.getPath().endsWith("_inner");
                double dx = inner ? Math.sqrt(.5) : 1, dz = inner ? Math.sqrt(.5) : 0;
                double ox = -1, oz = inner ? -1 : .25;
                double a = yaw * Math.PI / 4, s = Math.sin(a), c = Math.cos(a);
                player.refreshPositionAndAngles(root.getX() + .5 + c * (ox - .5) - s * (oz - .5), root.getY(), root.getZ() + .5 + s * (ox - .5) + c * (oz - .5), 0, 0);
                Vec3d step = new Vec3d(.08 * (c * dx - s * dz), -.01, .08 * (s * dx + c * dz));
                double landing = expected.stream().mapToDouble(b -> b.maxY).max().orElseThrow();
                for (int n = 0; n < 32 && player.getY() < landing - .05; n++) { player.setOnGround(true); player.move(MovementType.SELF, step); }
                require(player.getY() >= landing - .05, "NEW native stair cannot ascend to upper tread " + block.spec.id + " yaw=" + yaw + " actualY=" + player.getY() + " landingY=" + landing + " pos=" + player.getPos());
                player.move(MovementType.SELF, new Vec3d(0, -.2, 0)); require(player.getY() >= landing - .05, "Native stair upper tread provides no landing support");
                row.addProperty("nativeStairAscent", "PASS_ACTUAL_PLAYER_MOVE_WITH_DEFAULT_STEP_HEIGHT"); row.addProperty("actualStairPlayerY", player.getY()); row.addProperty("stairLandingY", landing);
            }
            require(CompositeRuntime.remove(world, owner, player, false).outcome() == TransactionCore.Outcome.COMMITTED, "Representative removal failed"); CompositeRuntime.drain(world);
            for (var cell : footprint.keySet()) require(CompositeRuntime.contributions(world, root.add(cell.x(), cell.y(), cell.z())).stream().noneMatch(c -> c.owner().equals(owner)), "Orphan collision helper survived removal");
            row.addProperty("typedNbtPickWholeRemoval", "PASS"); row.addProperty("status", "PASS"); movement.add(row); player.discard();
        }
        List<CompositeSpec.Bounds> physicalInput(CompositeRootBlock block, int variant, boolean open, int yaw) {
            JsonObject document = documents.computeIfAbsent(block.spec.id.getPath(), path -> {
                try (InputStream input = CompositeSpec.class.getResourceAsStream("/bloodborne_dw/composite/" + path + ".json")) {
                    return JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(input), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                } catch (IOException failure) { throw new UncheckedIOException(failure); }
            });
            JsonObject poses = document.getAsJsonArray("variants").get(variant).getAsJsonObject().getAsJsonObject("poses");
            JsonObject pose = poses.getAsJsonObject(open && poses.has("open") ? "open" : "closed");
            if (!pose.has("simpleCollisionByRotation")) return block.spec.pose(block.getDefaultState().with(CompositeRootBlock.VARIANT, variant).with(CompositeRootBlock.OPEN, open)).collision();
            List<CompositeSpec.Bounds> boxes = new ArrayList<>();
            for (JsonElement element : pose.getAsJsonArray("simpleCollisionByRotation").get(yaw).getAsJsonArray()) {
                JsonObject b = element.getAsJsonObject(); JsonArray f = b.getAsJsonArray("from"), t = b.getAsJsonArray("to");
                boxes.add(new CompositeSpec.Bounds(new CompositeSpec.Vec(f.get(0).getAsDouble(), f.get(1).getAsDouble(), f.get(2).getAsDouble()), new CompositeSpec.Vec(t.get(0).getAsDouble(), t.get(1).getAsDouble(), t.get(2).getAsDouble())));
            }
            return boxes;
        }
        static JsonObject originalDocuments() {
            try (InputStream input = SimpleCollisionRuntimeAudit.class.getResourceAsStream("/qa-original-source-descriptors.json.gz")) {
                return JsonParser.parseReader(new InputStreamReader(new GZIPInputStream(Objects.requireNonNull(input)), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (IOException e) { throw new UncheckedIOException(e); }
        }
        CompositeSpec original(CompositeRootBlock block) {
            return originals.computeIfAbsent(block.spec.id.toString(), id -> {
                try {
                    var constructor = CompositeSpec.class.getDeclaredConstructor(JsonObject.class); constructor.setAccessible(true);
                    return constructor.newInstance(originalDocuments.getAsJsonObject(id));
                } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
            });
        }
        void fail(Throwable error) {
            finished = true; result.addProperty("status", "FAIL"); result.addProperty("failedCase", index); result.addProperty("failure", error.toString());
            StringWriter trace = new StringWriter(); error.printStackTrace(new PrintWriter(trace)); result.addProperty("stackTrace", trace.toString()); write();
        }
        void write() {
            result.addProperty("completedVariants", index); result.addProperty("exactGeometryStates", geometryCases);
            result.add("movementAndTransactions", movement);
            try { Files.writeString(Path.of("collision-runtime-audit.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n"); }
            catch (IOException failure) { throw new UncheckedIOException(failure); }
        }
    }
    private static ServerPlayerEntity player(ServerWorld world) {
        var actor = new ServerPlayerEntity(world.getServer(), world, new GameProfile(UUID.randomUUID(), "SimpleCollisionQA"));
        var connection = new ClientConnection(NetworkSide.SERVERBOUND) {
            @Override public void send(Packet<?> packet) {}
            @Override public void send(Packet<?> packet, PacketCallbacks callbacks) {}
        };
        actor.networkHandler = new ServerPlayNetworkHandler(world.getServer(), connection, actor);
        actor.changeGameMode(GameMode.SURVIVAL); actor.getAbilities().allowModifyWorld = true; return actor;
    }
    /** Independent oracle: transform each ordinary box once, then clip only
     * the six small OUTSIDE distances of the entire pose to installation cell. */
    private static List<Box> expected(List<CompositeSpec.Bounds> boxes, int yaw, double mountY) {
        return expected(boxes, yaw, mountY, 0, 0, 0, true);
    }
    private static List<Box> expected(List<CompositeSpec.Bounds> boxes, int yaw, double mountY, double sx, double sy, double sz, boolean crop) {
        List<Box> rotated = new ArrayList<>(); double angle = yaw * Math.PI / 4, c = Math.cos(angle), s = Math.sin(angle);
        if (yaw % 2 == 0) { c = Math.rint(c); s = Math.rint(s); }
        for (var b : boxes) {
            double x0 = Double.POSITIVE_INFINITY, z0 = x0, x1 = Double.NEGATIVE_INFINITY, z1 = x1;
            for (double x : new double[]{b.from().x() / 16, b.to().x() / 16}) for (double z : new double[]{b.from().z() / 16, b.to().z() / 16}) {
                double tx = .5 + c * (x - .5) - s * (z - .5), tz = .5 + s * (x - .5) + c * (z - .5);
                x0 = Math.min(x0, tx); x1 = Math.max(x1, tx); z0 = Math.min(z0, tz); z1 = Math.max(z1, tz);
            }
            rotated.add(new Box(x0 + sx, b.from().y() / 16 + mountY + sy, z0 + sz, x1 + sx, b.to().y() / 16 + mountY + sy, z1 + sz));
        }
        if (!crop) return List.copyOf(rotated);
        if (rotated.isEmpty()) return List.of();
        double[] lo = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY}, hi = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (var b : rotated) { lo[0] = Math.min(lo[0], b.minX); lo[1] = Math.min(lo[1], b.minY); lo[2] = Math.min(lo[2], b.minZ); hi[0] = Math.max(hi[0], b.maxX); hi[1] = Math.max(hi[1], b.maxY); hi[2] = Math.max(hi[2], b.maxZ); }
        boolean[] trimLo = new boolean[3], trimHi = new boolean[3];
        for (int a = 0; a < 3; a++) { trimLo[a] = lo[a] < 0 && lo[a] > -.5; trimHi[a] = hi[a] > 1 && hi[a] < 1.5; }
        List<Box> clipped = new ArrayList<>();
        for (var b : rotated) {
            double x0 = trimLo[0] ? Math.max(0, b.minX) : b.minX, y0 = trimLo[1] ? Math.max(0, b.minY) : b.minY, z0 = trimLo[2] ? Math.max(0, b.minZ) : b.minZ;
            double x1 = trimHi[0] ? Math.min(1, b.maxX) : b.maxX, y1 = trimHi[1] ? Math.min(1, b.maxY) : b.maxY, z1 = trimHi[2] ? Math.min(1, b.maxZ) : b.maxZ;
            if (x1 - x0 > 1e-9 && y1 - y0 > 1e-9 && z1 - z0 > 1e-9) clipped.add(new Box(x0, y0, z0, x1, y1, z1));
        }
        return List.copyOf(clipped);
    }
    private static VoxelShape shape(List<Box> boxes) { VoxelShape shape = VoxelShapes.empty(); for (var box : boxes) shape = VoxelShapes.union(shape, VoxelShapes.cuboid(box)); return shape; }
    private static VoxelShape localShape(List<dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box> boxes) { VoxelShape s = VoxelShapes.empty(); for (var b : boxes) s = VoxelShapes.union(s, VoxelShapes.cuboid(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())); return s; }
}
