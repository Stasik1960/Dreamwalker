package dev.dreamwalker.bloodbornedw.conversion;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.architecture.SourceConversionScope;
import dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.util.WorldSavePath;

/** Explicit, separately built importer. No conversion entrypoint ships in the production mod. */
public final class FullMapConversionAuthor implements ModInitializer {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path INPUT = Path.of("dw-full-conversion-input.json");
    private static final ChunkTicketType<ChunkPos> TICKET = ChunkTicketType.create("dw_explicit_conversion", Comparator.comparingLong(ChunkPos::toLong));
    private static final String[] PHASES = {"owners.jsonl.gz", "whole-objects.jsonl.gz", "entities.jsonl.gz"};
    private static final int MAX_LINE = 16 * 1024 * 1024;
    private static final Map<MinecraftServer, Job> JOBS = new WeakHashMap<>();

    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (!Files.isRegularFile(INPUT, LinkOption.NOFOLLOW_LINKS)) return;
            try { JOBS.put(server, new Job(server, parseFile(INPUT))); }
            catch (Exception failure) { throw new IllegalStateException("Explicit conversion initialization refused", failure); }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Job job = JOBS.get(server);
            if (job == null || job.finished) return;
            try { job.tick(); } catch (Exception failure) { job.fail(failure); }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            Job job = JOBS.get(server);
            if (job != null && !job.finished) job.fail(new IllegalStateException("Conversion interrupted by server stop; resume from durable checkpoint"));
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(JOBS::remove);
    }

    private static final class Job {
        final MinecraftServer server;
        final JsonObject marker;
        final Path target, plan, checkpoint, report, errors;
        final String operation, productionSHA, planSHA;
        final boolean fixture;
        final Map<ServerWorld, Rules> rules = new LinkedHashMap<>();
        BufferedReader stream;
        JsonObject current;
        ServerWorld ticketWorld;
        final Set<ChunkPos> tickets = new HashSet<>();
        int phase;
        long line, committed, alreadyPresent, checkpointCount, started = System.nanoTime();
        boolean finished;

        Job(MinecraftServer server, JsonObject marker) throws Exception {
            this.server = server; this.marker = marker;
            require(marker.get("schemaVersion").getAsInt() == 1, "Unknown conversion marker schema");
            require(marker.get("explicitConversionAuthorized").getAsBoolean(), "Conversion requires the owner's explicit command");
            require(marker.get("offlinePreparationStatus").getAsString().equals("PASS_DERIVED_COPY_PREPARED"), "Offline source clearing was not verified");
            fixture = marker.get("mode").getAsString().equals("FIXTURE_NATIVE_TEST");
            require(fixture || marker.get("mode").getAsString().equals("FULL_ORIGINAL_AFTER_USER_COMMAND"), "Unknown conversion mode");
            operation = UUID.fromString(marker.get("operationId").getAsString()).toString();
            target = server.getSavePath(WorldSavePath.ROOT).toRealPath();
            require(target.equals(Path.of(marker.get("targetWorldPath").getAsString()).toRealPath()), "Marker belongs to another world");
            require(Files.isRegularFile(target.resolve("dw-derived-copy.json"), LinkOption.NOFOLLOW_LINKS), "Missing immutable-input derived-copy marker");
            JsonObject prepared = parseFile(target.resolve("dw-derived-copy.json"));
            require(prepared.get("operationId").getAsString().equals(operation), "Offline operation identity mismatch");
            require(prepared.get("status").getAsString().equals("PASS_DERIVED_COPY_PREPARED"), "Partial offline copy cannot be imported");
            productionSHA = marker.get("productionJarSha256").getAsString();
            String jar = System.getProperty("dw.conversion.productionJar", "");
            require(!jar.isBlank() && sha(Path.of(jar)).equals(productionSHA), "Pinned combined JAR checksum mismatch");
            JsonObject binding;
            try (InputStream input = FullMapConversionAuthor.class.getResourceAsStream("/dw-conversion-binding.json")) {
                require(input != null, "Missing separately built author binding");
                binding = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            }
            require(binding.get("productionJarSha256").getAsString().equals(productionSHA), "Author add-on belongs to another production JAR");
            plan = Path.of(marker.get("runtimePlanPath").getAsString()).toRealPath();
            planSHA = sha(plan.resolve("manifest.json"));
            require(planSHA.equals(marker.get("runtimeManifestSha256").getAsString()), "Runtime manifest changed");
            JsonObject manifest = parseFile(plan.resolve("manifest.json"));
            require(manifest.get("status").getAsString().equals("PASS_COMPLETE_SOURCE_PLAN"), "Unresolved authored source content remains");
            require(manifest.get("knownVisibleGaps").getAsLong() == 0, "Visible source gaps remain");
            require(manifest.getAsJsonObject("sourceHashes").equals(prepared.getAsJsonObject("sourceHashes")), "Original input hashes do not match prepared copy");
            for (String name : PHASES) {
                require(manifest.getAsJsonObject("runtimeFiles").has(name), "Missing bounded runtime phase " + name);
                require(sha(plan.resolve(name)).equals(manifest.getAsJsonObject("runtimeFiles").get(name).getAsString()), "Changed runtime phase " + name);
            }
            checkpoint = target.resolve("dw-conversion-checkpoint.json"); report = target.resolve("dw-conversion-result.json"); errors = target.resolve("dw-conversion-errors.jsonl");
            JsonObject previous = null;
            if (Files.isRegularFile(checkpoint, LinkOption.NOFOLLOW_LINKS)) {
                previous = parseFile(checkpoint);
                require(previous.get("operationId").getAsString().equals(operation) && previous.get("productionJarSha256").getAsString().equals(productionSHA)
                        && previous.get("runtimeManifestSha256").getAsString().equals(planSHA), "Checkpoint belongs to another operation");
                phase = previous.get("phase").getAsInt(); line = previous.get("line").getAsLong();
                committed = previous.get("committed").getAsLong(); alreadyPresent = previous.get("alreadyPresent").getAsLong();
            }
            require(phase >= 0 && phase <= PHASES.length && line >= 0, "Invalid durable cursor");
            for (ServerWorld world : server.getWorlds()) {
                CompositeLedger.enableSharding(world);
                String dimension = world.getRegistryKey().getValue().toString();
                Rules original = previous != null && previous.has("originalRules")
                        ? JSON.fromJson(previous.getAsJsonObject("originalRules").get(dimension), Rules.class) : new Rules(world);
                require(original != null, "Missing original game rules on resume");
                rules.put(world, original); Rules.freeze(world);
            }
            if (previous == null) durable("RUNNING");
            openPhase(); write(report, snapshot("RUNNING", null));
        }

        void openPhase() throws IOException {
            if (stream != null) stream.close();
            stream = null;
            if (phase == PHASES.length) return;
            stream = new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(plan.resolve(PHASES[phase])), 65536), StandardCharsets.UTF_8), 65536);
            for (long i = 0; i < line; i++) require(readLine() != null, "Checkpoint is beyond its input phase");
        }

        String readLine() throws IOException {
            // BufferedReader.readLine would allocate an unbounded malicious line before checking its length.
            StringBuilder text = new StringBuilder(2048);
            for (int value; (value = stream.read()) != -1;) {
                if (value == '\n') return text.toString();
                if (value != '\r') text.append((char)value);
                require(text.length() <= MAX_LINE, "Runtime row exceeds 16 MiB");
            }
            return text.isEmpty() ? null : text.toString();
        }

        void tick() throws Exception {
            require(server.getPlayerManager().getPlayerList().isEmpty(), "Players cannot enter a partially converted world");
            if (Files.exists(target.resolve("dw-conversion-stop.request"), LinkOption.NOFOLLOW_LINKS)) {
                durable("PAUSED_EXPLICIT_STOP"); finish("PAUSED_EXPLICIT_STOP"); return;
            }
            if (phase == PHASES.length) { durable("PASS_RUNTIME_CONVERTED_REOPEN_REQUIRED"); finish("PASS_RUNTIME_CONVERTED_REOPEN_REQUIRED"); return; }
            long deadline = System.nanoTime() + 8_000_000L;
            int batch = 0;
            while (batch++ < 16 && System.nanoTime() < deadline) {
                String raw = readLine();
                if (raw == null) { phase++; line = 0; durable("RUNNING"); openPhase(); break; }
                current = JsonParser.parseString(raw).getAsJsonObject();
                if (fixture) require(committed + alreadyPresent < 10000, "Fixture mode cannot convert a full original map");
                boolean exists = phase == 2 ? entity(current) : owner(current);
                if (exists) alreadyPresent++; else committed++;
                line++; releaseTickets();
                require(!SourceConversionScope.initialInstance(UUID.fromString(current.get("instanceUuid").getAsString())), "Initial overlap privilege leaked");
                current = null;
                if (committed + alreadyPresent - checkpointCount >= 1024) { durable("RUNNING"); break; }
            }
        }

        ServerWorld world(JsonObject row) {
            RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, new Identifier(row.get("dimension").getAsString()));
            ServerWorld world = server.getWorld(key); require(world != null, "Unsupported original dimension " + key.getValue());
            return world;
        }

        BlockPos position(JsonObject row) {
            JsonArray p = row.getAsJsonArray("position"); require(p.size() == 3, "Bad source coordinates");
            return new BlockPos(p.get(0).getAsInt(), p.get(1).getAsInt(), p.get(2).getAsInt());
        }

        void load(ServerWorld world, BlockPos pos) {
            require(world.isInBuildLimit(pos) && world.getWorldBorder().contains(pos), "Source object lies outside target build limits");
            require(ticketWorld == null || ticketWorld == world, "Unexpected cross-dimension object"); ticketWorld = world;
            ChunkPos chunk = new ChunkPos(pos);
            if (tickets.add(chunk)) { require(tickets.size() <= 256, "One source object spans more than 256 chunks"); world.getChunkManager().addTicket(TICKET, chunk, 2, chunk); }
            world.getChunk(chunk.x, chunk.z);
        }

        boolean owner(JsonObject row) {
            ServerWorld world = world(row); BlockPos pos = position(row); load(world, pos);
            Identifier id = new Identifier(row.get("registryId").getAsString());
            require(id.getNamespace().equals("bloodborne_dw") && Registries.BLOCK.containsId(id), "Unregistered/non-BB source owner " + id);
            Map<String,String> properties = new TreeMap<>(); row.getAsJsonObject("properties").entrySet().forEach(e -> properties.put(e.getKey(), e.getValue().getAsString()));
            BlockState state = CompositeData.state(new CellSnapshot.BlockData(id.toString(), properties, new byte[0]));
            require(VerticalMount.isArchitecture(state) && !ImportedCompactBlock.compact(state), "Deferred owner must be an owned architecture state");
            UUID uuid = UUID.fromString(row.get("instanceUuid").getAsString());
            NbtCompound payload = row.has("payloadSnbt") ? parseNbt(row.get("payloadSnbt").getAsString())
                    : row.has("payload") ? parseNbt(row.get("payload").toString()) : new NbtCompound();
            if (world.getBlockEntity(pos) instanceof CompositeBlockEntity saved && saved.resident() != null) {
                require(saved.resident().instanceId().equals(uuid) && saved.resident().registryId().equals(id.toString()), "Source root already occupied by another owner");
                require(world.getBlockState(pos).equals(state), "Previously imported source state changed before resume");
                for (String key : payload.getKeys()) require(Objects.equals(saved.payload().get(key), payload.get(key)), "Previously imported typed payload changed: " + key);
                return true;
            }
            require(world.getBlockState(pos).isAir() || world.getBlockState(pos).isOf(CompositeArchitecture.CELL), "Offline phase did not clear deferred root");
            if (VerticalMount.isNative(state)) payload = VerticalMount.nativePayload(world, pos, state, payload);
            var object = CompositeRuntime.instance(world, pos, state, uuid, payload);
            for (var offset : object.cells().keySet()) load(world, CompositeData.pos(object.owner().root().add(offset)));
            // A late whole object may meet a helper whose normal owner was
            // installed hours earlier and is cold. The transaction validates
            // that owner's exact root; loading only the incoming boxes would
            // otherwise reject a legitimate original overlap.
            Set<dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner> neighbours = new HashSet<>();
            for (var offset : object.cells().keySet()) for (var contribution : CompositeRuntime.contributions(world, CompositeData.pos(object.owner().root().add(offset)))) neighbours.add(contribution.owner());
            for (var neighbour : neighbours) load(world, CompositeData.pos(neighbour.root()));
            final BlockState selected = state; final NbtCompound supplied = payload;
            TransactionCore.Result result = SourceConversionScope.initialInstances(Set.of(uuid), () -> {
                if (selected.getBlock() instanceof CompositeRootBlock) return CompositeRuntime.place(world, pos, selected, uuid, null, supplied);
                FabricCompositeWorld access = new FabricCompositeWorld(world, Map.of(object.owner(), object));
                return CompositeRuntime.execute(access, CompositeRuntime.core().placement(access, object, List.of()));
            });
            require(result.outcome() == TransactionCore.Outcome.COMMITTED, "Initial source transaction rejected: " + result.reason());
            require(world.getBlockEntity(pos) instanceof CompositeBlockEntity saved && saved.resident() != null && saved.resident().instanceId().equals(uuid), "Owner identity was not persisted");
            return false;
        }

        boolean entity(JsonObject row) {
            ServerWorld world = world(row); BlockPos pos = position(row); load(world, pos);
            Identifier id = new Identifier(row.get("registryId").getAsString());
            boolean inertVanilla = id.getNamespace().equals("minecraft") && Set.of("experience_orb", "item_frame", "glow_item_frame", "armor_stand", "painting").contains(id.getPath());
            require((Set.of("bloodborne_dw", "bloodborne_rp").contains(id.getNamespace()) || inertVanilla) && Registries.ENTITY_TYPE.containsId(id), "Unknown/system converted entity " + id);
            UUID uuid = UUID.fromString(row.get("instanceUuid").getAsString());
            Entity existing = world.getEntity(uuid);
            if (existing != null) { require(Registries.ENTITY_TYPE.getId(existing.getType()).equals(id), "Existing entity UUID belongs to another type"); return true; }
            Entity entity = Registries.ENTITY_TYPE.get(id).create(world); require(entity != null, "Entity factory failed " + id);
            byte[] bytes = Base64.getDecoder().decode(row.get("nbtBase64").getAsString()); require(bytes.length <= MAX_LINE, "Entity typed NBT too large");
            NbtCompound nbt = CompositeData.nbt(bytes); entity.readNbt(nbt); entity.setUuid(uuid);
            require(new BlockPos(MathHelper.floor(entity.getX()), MathHelper.floor(entity.getY()), MathHelper.floor(entity.getZ())).equals(pos), "Entity source coordinates changed");
            Box box = entity.getBoundingBox();
            for (int x = MathHelper.floor(box.minX) >> 4; x <= MathHelper.floor(box.maxX - 1e-7) >> 4; x++)
                for (int z = MathHelper.floor(box.minZ) >> 4; z <= MathHelper.floor(box.maxZ - 1e-7) >> 4; z++) load(world, new BlockPos(x * 16, pos.getY(), z * 16));
            require(world.spawnEntity(entity), "Converted source entity spawn failed " + id);
            return false;
        }

        void releaseTickets() {
            if (ticketWorld != null) for (ChunkPos chunk : tickets) ticketWorld.getChunkManager().removeTicket(TICKET, chunk, 2, chunk);
            tickets.clear(); ticketWorld = null;
        }

        JsonObject snapshot(String status, String failure) {
            JsonObject out = new JsonObject(); out.addProperty("schemaVersion", 1); out.addProperty("status", status);
            out.addProperty("operationId", operation); out.addProperty("productionJarSha256", productionSHA); out.addProperty("runtimeManifestSha256", planSHA);
            out.addProperty("phase", phase); out.addProperty("line", line); out.addProperty("committed", committed); out.addProperty("alreadyPresent", alreadyPresent);
            out.addProperty("targetWorldPath", target.toString()); out.addProperty("originalArchivesWritten", false); out.addProperty("galleryCreated", false);
            out.addProperty("initialOverlapPrivilegeSerialized", false); out.addProperty("elapsedSeconds", (System.nanoTime() - started) / 1_000_000_000d);
            Runtime runtime = Runtime.getRuntime(); out.addProperty("heapUsedBytesObserved", runtime.totalMemory() - runtime.freeMemory()); out.addProperty("heapMaxBytes", runtime.maxMemory());
            JsonObject memory = new JsonObject(); for (ServerWorld world : rules.keySet()) memory.add(world.getRegistryKey().getValue().toString(), JSON.toJsonTree(CompositeLedger.get(world).shardMetrics())); out.add("ownerStorage", memory);
            JsonObject originals = new JsonObject(); rules.forEach((world, saved) -> originals.add(world.getRegistryKey().getValue().toString(), JSON.toJsonTree(saved))); out.add("originalRules", originals);
            if (current != null) out.add("currentSourceRow", current.deepCopy()); if (failure != null) out.addProperty("failure", failure);
            return out;
        }

        void durable(String status) throws IOException {
            releaseTickets(); server.save(false, true, true);
            write(checkpoint, snapshot(status, null)); write(report, snapshot(status, null)); checkpointCount = committed + alreadyPresent;
        }

        void finish(String status) throws IOException {
            releaseTickets(); if (stream != null) stream.close(); stream = null;
            rules.forEach((world, original) -> original.restore(world)); server.save(false, true, true);
            finished = true; write(report, snapshot(status, null)); server.stop(false);
        }

        void fail(Exception failure) {
            try {
                releaseTickets(); if (stream != null) stream.close(); stream = null;
                rules.forEach((world, original) -> original.restore(world)); server.save(false, true, true);
                JsonObject out = snapshot("FAIL_STOPPED_REVIEW_REQUIRED", failure.toString());
                Files.writeString(errors, new Gson().toJson(out) + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                write(report, out);
            } catch (Exception reporting) { failure.addSuppressed(reporting); }
            finished = true; dev.dreamwalker.bloodbornedw.DreamwalkerBb.LOG.error("Explicit full conversion stopped at a recorded source row", failure); server.stop(false);
        }
    }

    private record Rules(boolean daylight, boolean spawning, boolean fire, boolean weather, int randomTicks) {
        Rules(ServerWorld world) { this(world.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE), world.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING), world.getGameRules().getBoolean(GameRules.DO_FIRE_TICK), world.getGameRules().getBoolean(GameRules.DO_WEATHER_CYCLE), world.getGameRules().getInt(GameRules.RANDOM_TICK_SPEED)); }
        static void freeze(ServerWorld world) {
            world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, world.getServer()); world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, world.getServer()); world.getGameRules().get(GameRules.DO_FIRE_TICK).set(false, world.getServer()); world.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false, world.getServer()); world.getGameRules().get(GameRules.RANDOM_TICK_SPEED).set(0, world.getServer()); }
        void restore(ServerWorld world) { world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(daylight, world.getServer()); world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(spawning, world.getServer()); world.getGameRules().get(GameRules.DO_FIRE_TICK).set(fire, world.getServer()); world.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(weather, world.getServer()); world.getGameRules().get(GameRules.RANDOM_TICK_SPEED).set(randomTicks, world.getServer()); }
    }

    private static NbtCompound parseNbt(String value) { try { return net.minecraft.nbt.StringNbtReader.parse(value); } catch (Exception e) { throw new IllegalArgumentException("Invalid typed source payload", e); } }
    private static JsonObject parseFile(Path path) throws IOException { require(Files.size(path) <= MAX_LINE, "Oversized conversion metadata"); try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { return JsonParser.parseReader(reader).getAsJsonObject(); } }
    private static void require(boolean okay, String message) { if (!okay) throw new IllegalStateException(message); }
    private static String sha(Path path) throws Exception { MessageDigest digest = MessageDigest.getInstance("SHA-256"); try (InputStream input = Files.newInputStream(path)) { byte[] buffer = new byte[1024 * 1024]; for (int n; (n = input.read(buffer)) != -1;) digest.update(buffer, 0, n); } return HexFormat.of().formatHex(digest.digest()); }
    private static void write(Path path, JsonObject value) throws IOException { Path pending = path.resolveSibling(path.getFileName() + ".pending"); Files.writeString(pending, JSON.toJson(value) + "\n", StandardCharsets.UTF_8); try { Files.move(pending, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); } catch (AtomicMoveNotSupportedException e) { Files.move(pending, path, StandardCopyOption.REPLACE_EXISTING); } }
}
