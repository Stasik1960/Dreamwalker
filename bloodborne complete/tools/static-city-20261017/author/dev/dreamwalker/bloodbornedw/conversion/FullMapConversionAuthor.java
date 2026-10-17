package dev.dreamwalker.bloodbornedw.conversion;

import com.google.gson.*;
import com.mojang.datafixers.util.Either;
import dev.dreamwalker.bloodbornedw.architecture.SourceConversionScope;
import dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.composite.mixin.BbChunkFutureInvoker;
import dev.dreamwalker.bloodbornedw.runtime.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.chunk.FlatChunkGenerator;
import net.minecraft.util.WorldSavePath;

/** Explicit, separately built importer. No conversion entrypoint ships in the production mod. */
public final class FullMapConversionAuthor implements ModInitializer {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path INPUT = Path.of("dw-full-conversion-input.json");
    private static final ChunkTicketType<ChunkPos> TICKET = ChunkTicketType.create("dw_explicit_conversion", Comparator.comparingLong(ChunkPos::toLong));
    private static final String[] PHASES = {"owners.jsonl.gz", "whole-objects.jsonl.gz", "entities.jsonl.gz"};
    private static final int MAX_LINE = 16 * 1024 * 1024;
    private static final String IMPLEMENTED_STYLE = "APPROVED_EXACT_VISIBLE_WITH_ENCLOSED_VANILLA";
    private static final Set<String> IMPLEMENTED_STYLES=Set.of(IMPLEMENTED_STYLE,"APPROVED_EXACT_VISIBLE_AND_HIDDEN_COMPACT");
    private static final Map<MinecraftServer, Job> JOBS = new WeakHashMap<>();
    private static final ChunkStatus[] LOAD_STAGES = {ChunkStatus.EMPTY, ChunkStatus.STRUCTURE_STARTS,
            ChunkStatus.STRUCTURE_REFERENCES, ChunkStatus.BIOMES, ChunkStatus.NOISE, ChunkStatus.SURFACE,
            ChunkStatus.CARVERS, ChunkStatus.FEATURES, ChunkStatus.INITIALIZE_LIGHT, ChunkStatus.LIGHT,
            ChunkStatus.SPAWN, ChunkStatus.FULL};

    /** Implemented by the conversion add-on only; counters never wait on futures or retain holders. */
    public interface ChunkControl { ChunkCounts dw$chunkCounts(); }
    public record ChunkCounts(int loadedWorldChunks, int holders, int readableHolders,
                              int pendingHolders, int chunksToUnload, int unloadQueue) {
        public ChunkCounts {
            require(loadedWorldChunks >= 0 && holders >= 0 && readableHolders >= 0
                    && pendingHolders >= 0 && chunksToUnload >= 0 && unloadQueue >= 0, "Invalid native chunk counters");
        }
        boolean empty() { return loadedWorldChunks == 0 && holders == 0 && readableHolders == 0
                && pendingHolders == 0 && chunksToUnload == 0 && unloadQueue == 0; }
    }

    private record Limits(boolean bounded, boolean pauseBeforeRows, long maxRows, long maxTicks,
                          int checkpointRows, int maxWorkingChunks, int maxLoadedChunks,
                          int maxHolders, int maxDrainTicks, int measurementHoldTicks, long measurementActiveRows) {
        static Limits read() {
            return new Limits(Boolean.getBoolean("dw.conversion.boundedChunks"),
                    Boolean.getBoolean("dw.conversion.pauseBeforeRows"),
                    number("maxRows", 0, 0, Long.MAX_VALUE), number("maxTicks", 0, 0, Long.MAX_VALUE),
                    (int)number("checkpointRows", 128, 1, 1024),
                    (int)number("maxWorkingChunks", 16, 1, 256),
                    (int)number("maxLoadedChunks", 128, 9, 4096),
                    (int)number("maxHolders", 1024, 9, 16384),
                    (int)number("maxDrainTicks", 2400, 20, 72000),
                    (int)number("measurementHoldTicks", 0, 0, 12000),
                    number("measurementActiveRows", 64, 1, Long.MAX_VALUE));
        }
        private static long number(String key, long fallback, long minimum, long maximum) {
            String text = System.getProperty("dw.conversion." + key);
            long value = text == null ? fallback : Long.parseLong(text);
            require(value >= minimum && value <= maximum, "Invalid dw.conversion." + key); return value;
        }
    }
    private static final class RowWaiting extends RuntimeException {
        static final RowWaiting INSTANCE = new RowWaiting();
        private RowWaiting() { super(null, null, false, false); }
    }
    private static final class CapacityDrain extends RuntimeException {
        static final CapacityDrain INSTANCE = new CapacityDrain();
        private CapacityDrain() { super(null, null, false, false); }
    }

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
        final boolean staticGrassMasonry;
        final boolean staticArchitecture;
        final boolean cityStaticArchitecture;
        final String[] phases;
        final Limits limits;
        final Map<ServerWorld, Rules> rules = new LinkedHashMap<>();
        final Map<String,NbtList> originalSourceBlockEntities=new HashMap<>();
        BufferedReader stream;
        JsonObject current;
        ServerWorld ticketWorld;
        final Set<ChunkPos> tickets = new HashSet<>();
        final Map<ServerWorld,Map<ChunkPos,Integer>> heldTickets = new IdentityHashMap<>();
        LoadRequest loading;
        int phase;
        long line, committed, alreadyPresent, checkpointCount, started = System.nanoTime();
        long architectureOwners, architectureCells, existingSourceProtoAdmissions;
        long architectureSkipped, architectureSkippedOwners, architectureSkippedCells;
        final Map<String,Long> architectureSkipReasons=new TreeMap<>();
        boolean unsupportedCurrent;
        int peakLoadedWorldChunks, peakHolders, peakReadableHolders;
        int peakPendingHolders, peakChunksToUnload, peakUnloadQueue;
        long initialProcessed, ticks, drainStartedTick;
        boolean draining, initialBoundary = true, measuredStart, measuredActive, measuredDrained;
        String stopStatus, holdPhase, measurementId;
        int holdRemaining;
        boolean finished;

        Job(MinecraftServer server, JsonObject marker) throws Exception {
            this.server = server; this.marker = marker;
            require(marker.get("schemaVersion").getAsInt() == 1, "Unknown conversion marker schema");
            require(marker.get("explicitConversionAuthorized").getAsBoolean(), "Conversion requires the owner's explicit command");
            require(marker.get("offlinePreparationStatus").getAsString().equals("PASS_DERIVED_COPY_PREPARED"), "Offline source clearing was not verified");
            fixture = marker.get("mode").getAsString().equals("FIXTURE_NATIVE_TEST");
            staticGrassMasonry = Boolean.getBoolean("dw.conversion.staticGrassMasonry");
            require(!staticGrassMasonry || fixture, "Static grass pilot is restricted to an explicit small fixture");
            staticArchitecture = Boolean.getBoolean("dw.conversion.staticArchitecture");
            cityStaticArchitecture = marker.get("mode").getAsString().equals("CITY_STATIC_ARCHITECTURE_AFTER_COMPLETE");
            require(!cityStaticArchitecture || staticArchitecture && !fixture && !staticGrassMasonry, "City static mode requires architecture-only operation");
            require(!staticArchitecture || fixture || cityStaticArchitecture, "Static architecture requires explicit fixture or completed city operation");
            require(!staticArchitecture || !staticGrassMasonry, "Architecture pilot must preserve the other family conversion mode");
            phases = staticArchitecture ? new String[]{PHASES[0], PHASES[1], PHASES[2], "static-architecture-components.jsonl.gz"} : PHASES;
            limits = Limits.read();
            require(!staticArchitecture || limits.bounded, "Static architecture requires native bounded chunk loading");
            require(fixture || cityStaticArchitecture || marker.get("mode").getAsString().equals("FULL_ORIGINAL_AFTER_USER_COMMAND"), "Unknown conversion mode");
            require(fixture || limits.bounded, "Full conversion requires -Ddw.conversion.boundedChunks=true");
            require(!limits.bounded || ConversionChunkControl.active(server) && ConversionChunkControl.spawnPreloadBypassed(server),
                    "Bounded startup was not confirmed before loading the conversion job");
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
            if (fixture) {
                require(manifest.has("fixtureOnly") && manifest.get("fixtureOnly").getAsBoolean(), "Fixture mode requires an explicitly bounded fixture plan");
            } else {
                require(!manifest.has("fixtureOnly") || !manifest.get("fixtureOnly").getAsBoolean(), "A fixture cannot stand in for the original world");
                String execution = manifest.has("runtimeExecution") ? manifest.get("runtimeExecution").getAsString() : "";
                require(!execution.startsWith("BLOCKED") && !execution.startsWith("PENDING"), "Full source plan execution is blocked");
                require(marker.has("sourcePlanPath") && marker.has("sourceStylePolicyPath"), "Full conversion requires exact source/policy bindings");
                Path sourcePlan = Path.of(marker.get("sourcePlanPath").getAsString()).toRealPath();
                Path policyPath = Path.of(marker.get("sourceStylePolicyPath").getAsString()).toRealPath();
                String sourceSHA = sha(sourcePlan), policySHA = sha(policyPath);
                JsonObject policy = parseFile(policyPath);
                String style=policy.get("status").getAsString();
                require(IMPLEMENTED_STYLES.contains(style), "Source-style preservation is not implemented");
                require(manifest.get("sourceStylePolicy").getAsString().equals(style)
                        && marker.get("sourceStylePolicy").getAsString().equals(style), "Source-style policy mismatch");
                require(manifest.get("sourceStylePolicySha256").getAsString().equals(policySHA)
                        && marker.get("sourceStylePolicySha256").getAsString().equals(policySHA), "Stale source-style policy binding");
                require(manifest.get("sourcePlanSha256").getAsString().equals(sourceSHA)
                        && marker.get("sourcePlanSha256").getAsString().equals(sourceSHA)
                        && prepared.get("sourcePlanSha256").getAsString().equals(sourceSHA), "Derived copy/source SQLite plan mismatch");
                require(prepared.get("runtimeManifestSha256").getAsString().equals(planSHA), "Derived copy was staged against another runtime bundle");
            }
            if(cityStaticArchitecture) CityStaticEligibility.validateCompletedSource(target,plan,manifest,marker,productionSHA);
            if (staticArchitecture) require(manifest.has("staticArchitectureCatalogSha256")
                    && manifest.get("staticArchitectureCatalogSha256").getAsString().equals(StaticArchitectureCatalog.resourceSha256()),
                    "Static architecture operation belongs to another finite shared catalog");
            for (String name : phases) {
                require(manifest.getAsJsonObject("runtimeFiles").has(name), "Missing bounded runtime phase " + name);
                require(sha(plan.resolve(name)).equals(manifest.getAsJsonObject("runtimeFiles").get(name).getAsString()), "Changed runtime phase " + name);
            }
            if(manifest.has("originalSourceBlockEntities")){
                var rows=manifest.getAsJsonArray("originalSourceBlockEntities");require(rows.size()<=1024,"Original source metadata budget");
                for(var raw:rows){var row=raw.getAsJsonObject();if(!row.get("decisionAction").getAsString().equals("OWNER"))continue;
                    var p=row.getAsJsonArray("ownerPosition");require(p.size()==3,"Original metadata coordinates");
                    BlockPos position=new BlockPos(p.get(0).getAsInt(),p.get(1).getAsInt(),p.get(2).getAsInt());
                    byte[] bytes=Base64.getDecoder().decode(row.get("nbtBase64").getAsString());require(bytes.length<=MAX_LINE,"Original typed metadata budget");
                    String key=row.get("dimension").getAsString()+"/"+position.asLong();
                    originalSourceBlockEntities.computeIfAbsent(key,ignored->new NbtList()).add(CompositeData.nbt(bytes));
                }
            }
            checkpoint = target.resolve(cityStaticArchitecture?"dw-static-architecture-checkpoint.json":"dw-conversion-checkpoint.json"); report = target.resolve(cityStaticArchitecture?"dw-static-architecture-result.json":"dw-conversion-result.json"); errors = target.resolve(cityStaticArchitecture?"dw-static-architecture-errors.jsonl":"dw-conversion-errors.jsonl");
            JsonObject previous = null;
            if (Files.isRegularFile(checkpoint, LinkOption.NOFOLLOW_LINKS)) {
                previous = parseFile(checkpoint);
                require(previous.get("operationId").getAsString().equals(operation) && previous.get("productionJarSha256").getAsString().equals(productionSHA)
                        && previous.get("runtimeManifestSha256").getAsString().equals(planSHA), "Checkpoint belongs to another operation");
                phase = previous.get("phase").getAsInt(); line = previous.get("line").getAsLong();
                committed = previous.get("committed").getAsLong(); alreadyPresent = previous.get("alreadyPresent").getAsLong();
                require(staticGrassMasonry == (previous.has("staticGrassMasonry") && previous.get("staticGrassMasonry").getAsBoolean()),
                        "Checkpoint static-family mode changed");
                require(staticArchitecture == (previous.has("staticArchitecture") && previous.get("staticArchitecture").getAsBoolean()),
                        "Checkpoint static architecture mode changed");
                architectureOwners = previous.has("architectureOwners") ? previous.get("architectureOwners").getAsLong() : 0;
                architectureCells = previous.has("architectureCells") ? previous.get("architectureCells").getAsLong() : 0;
                require(cityStaticArchitecture == (previous.has("cityStaticArchitecture") && previous.get("cityStaticArchitecture").getAsBoolean()), "Checkpoint city static mode changed");
                architectureSkipped=previous.has("architectureSkipped")?previous.get("architectureSkipped").getAsLong():0;
                architectureSkippedOwners=previous.has("architectureSkippedOwners")?previous.get("architectureSkippedOwners").getAsLong():0;
                architectureSkippedCells=previous.has("architectureSkippedCells")?previous.get("architectureSkippedCells").getAsLong():0;
                if(previous.has("architectureSkipReasons"))previous.getAsJsonObject("architectureSkipReasons").entrySet().forEach(e->architectureSkipReasons.put(e.getKey(),e.getValue().getAsLong()));
            }
            require(phase >= 0 && phase <= phases.length && line >= 0, "Invalid durable cursor");
            initialProcessed = checkpointCount = committed + alreadyPresent + architectureSkipped;
            Map<GameRules,Rules> sharedOriginals = new IdentityHashMap<>();
            for (ServerWorld world : server.getWorlds()) {
                CompositeLedger.enableSharding(world);
                String dimension = world.getRegistryKey().getValue().toString();
                require(previous == null || previous.has("originalRules") && previous.getAsJsonObject("originalRules").has(dimension), "Missing original game rules on resume: " + dimension);
                if (previous != null) {
                    JsonObject savedRules = previous.getAsJsonObject("originalRules").getAsJsonObject(dimension);
                    require(savedRules != null && List.of("daylight", "spawning", "fire", "weather", "randomTicks").stream().allMatch(savedRules::has), "Incomplete original game rules on resume: " + dimension);
                }
                Rules original = previous != null
                        ? JSON.fromJson(previous.getAsJsonObject("originalRules").get(dimension), Rules.class) : new Rules(world);
                require(original != null, "Missing original game rules on resume");
                Rules shared = sharedOriginals.putIfAbsent(world.getGameRules(), original);
                require(shared == null || shared.equals(original), "Conflicting saved originals for dimensions sharing game rules: " + dimension);
                rules.put(world, original);
            }
            // Vanilla dimensions share GameRules. Capture ALL originals before
            // freezing any world, including every durable resume record.
            if (limits.bounded) chunkCounts(); // Validate instrumentation before changing GameRules.
            for (ServerWorld world : rules.keySet()) Rules.freeze(world);
            if (limits.bounded) {
                openPhase(); beginDrain();
            } else {
                if (previous == null) durable("RUNNING");
                openPhase(); write(report, snapshot("RUNNING", null));
            }
        }

        void openPhase() throws IOException {
            if (stream != null) stream.close();
            stream = null;
            if (phase == phases.length) return;
            stream = new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(plan.resolve(phases[phase])), 65536), StandardCharsets.UTF_8), 65536);
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
            if (!limits.bounded) { legacyTick(); return; }
            ticks++;
            chunkCounts(); // Primitive-only observations, including transient active work.
            require(server.getPlayerManager().getPlayerList().isEmpty(), "Players cannot enter a partially converted world");
            if (Files.exists(target.resolve("dw-conversion-stop.request"), LinkOption.NOFOLLOW_LINKS)) stopStatus = "PAUSED_EXPLICIT_STOP";
            else if (stopStatus == null && (limits.pauseBeforeRows || limits.maxRows > 0 && processedThisRun() >= limits.maxRows
                    || limits.maxTicks > 0 && ticks >= limits.maxTicks)) stopStatus = "PAUSED_OPERATIONAL_LIMIT";
            if (holdRemaining > 0) {
                if (draining) drainStartedTick++; // Measurement holds do not consume the native drain deadline.
                Path continuation = Path.of("dw-conversion-measurement-continue.json");
                boolean released = Files.isRegularFile(continuation, LinkOption.NOFOLLOW_LINKS)
                        && measurementId.equals(parseFile(continuation).get("measurementId").getAsString());
                if (released) { Files.delete(continuation); holdRemaining = 0; }
                else holdRemaining--;
                if (holdRemaining == 0) measurement("END", holdPhase);
                return;
            }
            if (draining) { drain(); return; }
            if (stopStatus != null || phase == phases.length) {
                if (stopStatus == null) stopStatus = "PASS_RUNTIME_CONVERTED_REOPEN_REQUIRED";
                beginDrain(); return;
            }
            try { if (loading != null && !loading.advance()) return; }
            catch (CapacityDrain wait) { beginDrain(); return; }
            long deadline = System.nanoTime() + 8_000_000L;
            for (int batch = 0; batch < 16 && System.nanoTime() < deadline; batch++) {
                if (current == null) {
                    if (overBudget() || heldChunkCount() >= limits.maxWorkingChunks) { beginDrain(); return; }
                    String raw = readLine();
                    if (raw == null) { phase++; line = 0; openPhase(); beginDrain(); return; }
                    current = JsonParser.parseString(raw).getAsJsonObject();
                }
                if (fixture) require(committed + alreadyPresent + architectureSkipped < 10000, "Fixture mode cannot convert a full original map");
                boolean exists;
                try { exists = phase == 3 ? architecture(current) : phase == 2 ? entity(current) : owner(current); }
                catch (RowWaiting waiting) { return; }
                catch (CapacityDrain wait) { beginDrain(); return; }
                requireInitialPrivilegeReleased(current);
                if(unsupportedCurrent){unsupportedCurrent=false;architectureSkipped++;}else if (exists) alreadyPresent++; else committed++;
                line++; current = null; tickets.clear(); ticketWorld = null;
                if (!measuredActive && processedThisRun() >= limits.measurementActiveRows && limits.measurementHoldTicks > 0) {
                    measuredActive = true; hold("ACTIVE"); return;
                }
                if (limits.maxRows > 0 && processedThisRun() >= limits.maxRows) { stopStatus = "PAUSED_OPERATIONAL_LIMIT"; beginDrain(); return; }
                if (committed + alreadyPresent + architectureSkipped - checkpointCount >= limits.checkpointRows) { beginDrain(); return; }
            }
        }

        void legacyTick() throws Exception {
            require(server.getPlayerManager().getPlayerList().isEmpty(), "Players cannot enter a partially converted world");
            if (Files.exists(target.resolve("dw-conversion-stop.request"), LinkOption.NOFOLLOW_LINKS)) {
                durable("PAUSED_EXPLICIT_STOP"); finish("PAUSED_EXPLICIT_STOP"); return;
            }
            if (phase == phases.length) { durable("PASS_RUNTIME_CONVERTED_REOPEN_REQUIRED"); finish("PASS_RUNTIME_CONVERTED_REOPEN_REQUIRED"); return; }
            long deadline = System.nanoTime() + 8_000_000L;
            int batch = 0;
            while (batch++ < 16 && System.nanoTime() < deadline) {
                String raw = readLine();
                if (raw == null) { phase++; line = 0; durable("RUNNING"); openPhase(); break; }
                current = JsonParser.parseString(raw).getAsJsonObject();
                if (fixture) require(committed + alreadyPresent + architectureSkipped < 10000, "Fixture mode cannot convert a full original map");
                boolean exists = phase == 3 ? architecture(current) : phase == 2 ? entity(current) : owner(current);
                if(unsupportedCurrent){unsupportedCurrent=false;architectureSkipped++;}else if (exists) alreadyPresent++; else committed++;
                line++; releaseTickets();
                requireInitialPrivilegeReleased(current);
                current = null;
                if (committed + alreadyPresent + architectureSkipped - checkpointCount >= 1024) { durable("RUNNING"); break; }
            }
        }

        void requireInitialPrivilegeReleased(JsonObject row) {
            if (phase == 3) {
                for (JsonElement owner : row.getAsJsonArray("owners"))
                    require(!SourceConversionScope.initialInstance(UUID.fromString(owner.getAsJsonObject().get("instanceUuid").getAsString())), "Initial overlap privilege leaked");
            } else {
                require(!SourceConversionScope.initialInstance(UUID.fromString(row.get("instanceUuid").getAsString())), "Initial overlap privilege leaked");
            }
        }

        long processedThisRun() { return committed + alreadyPresent + architectureSkipped - initialProcessed; }

        Map<ServerWorld,ChunkCounts> chunkCounts() {
            Map<ServerWorld,ChunkCounts> result = new LinkedHashMap<>();
            for (ServerWorld world : rules.keySet()) {
                require((Object)world.getChunkManager() instanceof ChunkControl, "Missing bounded conversion chunk diagnostics");
                result.put(world, ((ChunkControl)(Object)world.getChunkManager()).dw$chunkCounts());
            }
            ChunkCounts observed = totalChunks(result);
            peakLoadedWorldChunks = Math.max(peakLoadedWorldChunks, observed.loadedWorldChunks);
            peakHolders = Math.max(peakHolders, observed.holders);
            peakReadableHolders = Math.max(peakReadableHolders, observed.readableHolders);
            peakPendingHolders = Math.max(peakPendingHolders, observed.pendingHolders);
            peakChunksToUnload = Math.max(peakChunksToUnload, observed.chunksToUnload);
            peakUnloadQueue = Math.max(peakUnloadQueue, observed.unloadQueue);
            return result;
        }

        ChunkCounts totalChunks(Map<ServerWorld,ChunkCounts> dimensions) {
            int loaded = 0, holders = 0, readable = 0, pending = 0, unloading = 0, queue = 0;
            for (ChunkCounts c : dimensions.values()) {
                loaded = Math.addExact(loaded, c.loadedWorldChunks); holders = Math.addExact(holders, c.holders);
                readable = Math.addExact(readable, c.readableHolders); pending = Math.addExact(pending, c.pendingHolders);
                unloading = Math.addExact(unloading, c.chunksToUnload); queue = Math.addExact(queue, c.unloadQueue);
            }
            return new ChunkCounts(loaded, holders, readable, pending, unloading, queue);
        }

        int heldChunkCount() { return heldTickets.values().stream().mapToInt(Map::size).sum(); }

        boolean overBudget() {
            ChunkCounts c = totalChunks(chunkCounts());
            return c.loadedWorldChunks >= limits.maxLoadedChunks || Math.max(c.holders, c.readableHolders) >= limits.maxHolders;
        }

        void ensureRowBudget() {
            if (!limits.bounded || !overBudget()) return;
            if (committed + alreadyPresent + architectureSkipped > checkpointCount) throw CapacityDrain.INSTANCE;
            throw new IllegalStateException("One source row exceeds bounded chunk working set before its transaction; "
                    + "cursor remains at the previous durable row; required object chunks=" + tickets.size());
        }

        void beginDrain() throws IOException {
            releaseTickets(); server.save(false, true, true);
            draining = true; drainStartedTick = ticks;
            write(report, snapshot("RUNNING_DRAINING_CHUNKS", null));
        }

        void drain() throws IOException {
            require(ticks - drainStartedTick <= limits.maxDrainTicks, "Native chunk drain exceeded its bounded wait; no clean checkpoint promoted");
            if (!totalChunks(chunkCounts()).empty()) return;
            // Native unloading may flush more dirty shards after the first save.
            // Flush again only after ALL holder/future/unload references are gone.
            server.save(false, true, true);
            require(totalChunks(chunkCounts()).empty(), "Native chunk drain changed during final flush");
            if (initialBoundary && !measuredStart) {
                measuredStart = true;
                if (limits.measurementHoldTicks > 0) { hold("START_EMPTY"); return; }
            }
            if (stopStatus != null && !measuredDrained && limits.measurementHoldTicks > 0) {
                measuredDrained = true; hold("DRAINED"); return;
            }
            if (stopStatus != null) {
                if (stream != null) stream.close(); stream = null;
                rules.forEach((world, original) -> original.restore(world)); server.save(false, true, true);
                require(totalChunks(chunkCounts()).empty(), "Clean pause requires zero retained native chunk references");
                write(checkpoint, snapshot(stopStatus, null)); write(report, snapshot(stopStatus, null));
                checkpointCount = committed + alreadyPresent + architectureSkipped; finished = true; server.stop(false); return;
            }
            write(checkpoint, snapshot("RUNNING", null)); write(report, snapshot("RUNNING", null));
            checkpointCount = committed + alreadyPresent + architectureSkipped; draining = false; initialBoundary = false;
        }

        void hold(String phase) {
            holdPhase = phase; holdRemaining = limits.measurementHoldTicks; measurementId = UUID.randomUUID().toString();
            measurement("START", phase);
        }

        void measurement(String event, String phase) {
            JsonObject value = new JsonObject(); value.addProperty("event", event); value.addProperty("phase", phase);
            value.addProperty("operationId", operation); value.addProperty("measurementId", measurementId);
            value.addProperty("continueFile", "dw-conversion-measurement-continue.json"); value.addProperty("holdTicks", limits.measurementHoldTicks);
            value.addProperty("processedThisRun", processedThisRun()); value.add("chunkLifecycle", chunkSnapshot());
            Runtime runtime = Runtime.getRuntime(); value.addProperty("heapUsedBytesObserved", runtime.totalMemory() - runtime.freeMemory());
            System.out.println("DW_CONVERSION_MEASUREMENT " + new Gson().toJson(value));
        }

        JsonObject chunkSnapshot() {
            var counts = chunkCounts(); JsonObject dimensions = new JsonObject();
            counts.forEach((world, count) -> dimensions.add(world.getRegistryKey().getValue().toString(), JSON.toJsonTree(count)));
            JsonObject value = new JsonObject(); value.add("dimensions", dimensions); value.add("totals", JSON.toJsonTree(totalChunks(counts)));
            value.addProperty("heldDirectTicketChunks", heldChunkCount()); value.addProperty("draining", draining);
            value.addProperty("loadInFlight", loading != null); value.addProperty("tick", ticks);
            if (loading != null) {
                value.addProperty("loadTarget", loading.target.toString()); value.addProperty("loadStage", loading.stageName());
            }
            return value;
        }

        void holdTicket(ServerWorld world, ChunkPos chunk, ChunkStatus status) {
            int distance = -ChunkStatus.getDistanceFromFull(status); // Native level =33-radius, with no clamp.
            Map<ChunkPos,Integer> held = heldTickets.computeIfAbsent(world, ignored -> new HashMap<>());
            Integer previous = held.get(chunk);
            if (previous != null && previous >= distance) return;
            if (previous != null) world.getChunkManager().removeTicket(TICKET, chunk, previous, chunk);
            world.getChunkManager().addTicket(TICKET, chunk, distance, chunk); held.put(chunk, distance);
        }

        /** One native future at a time; no get/join, no manual chunk or holder eviction. */
        final class LoadRequest {
            final ServerWorld world;
            final ChunkPos target;
            final List<ChunkPos> halo = new ArrayList<>();
            int haloIndex, stage;
            boolean center, centerProbe = true;
            CompletableFuture<Either<Chunk,ChunkHolder.Unloaded>> future;
            LoadRequest(ServerWorld world, ChunkPos target) {
                this.world = world; this.target = target;
                for (int x = target.x - 1; x <= target.x + 1; x++)
                    for (int z = target.z - 1; z <= target.z + 1; z++) halo.add(new ChunkPos(x, z));
            }
            String stageName() { return centerProbe ? "CENTER_PROBE:EMPTY" : (center ? "CENTER:" : "HALO:" + haloIndex + ":") + LOAD_STAGES[stage]; }
            boolean advance() {
                long deadline = System.nanoTime() + 8_000_000L;
                while (System.nanoTime() < deadline) {
                    ChunkPos next = centerProbe || center ? target : halo.get(haloIndex);
                    ChunkStatus status = centerProbe ? ChunkStatus.EMPTY : LOAD_STAGES[stage];
                    if (future == null) {
                        ensureRowBudget(); holdTicket(world, next, status);
                        future = ((BbChunkFutureInvoker)(Object)world.getChunkManager())
                                .bloodborneDw$profileChunkFuture(next.x, next.z, status, true);
                    }
                    ensureRowBudget();
                    if (!future.isDone()) return false;
                    Either<Chunk,ChunkHolder.Unloaded> completed = future.getNow(null);
                    require(completed != null && completed.left().isPresent(), "Native bounded load did not provide " + next + " at " + status);
                    Chunk actual = completed.left().orElseThrow();
                    if (centerProbe) {
                        // Inspect the center first. Saved FULL is only a route choice:
                        // still await the native FULL future and load() accessibility check.
                        centerProbe = false; future = null;
                        if (actual.getStatus().isAtLeast(ChunkStatus.FULL)) {
                            center = true; stage = LOAD_STAGES.length - 1;
                        }
                        // Every non-FULL center follows the unchanged .10 halo stages.
                        // The probe itself does not increment admission counters.
                        continue;
                    }
                    // EMPTY is requested first: absent/pre-FEATURES chunks fail
                    // before this loader can request any generation stage.
                    // Existing FEATURES in the source flat world has already
                    // completed terrain and features. The remaining native
                    // lighting, empty flat population and FULL stages preserve
                    // its prepared block states; every light halo is checked.
                    boolean full = actual.getStatus().isAtLeast(ChunkStatus.FULL);
                    require(full || actual.getStatus().isAtLeast(ChunkStatus.FEATURES)
                                    && world.getChunkManager().getChunkGenerator() instanceof FlatChunkGenerator,
                            "Bounded conversion refuses a missing/pre-FEATURES or unverified-generator source halo chunk: "
                                    + next + " at " + actual.getStatus());
                    if (!full && stage == 0) existingSourceProtoAdmissions++;
                    future = null;
                    if (!center && status == ChunkStatus.INITIALIZE_LIGHT) {
                        if (++haloIndex == halo.size()) { center = true; stage = 9; } else stage = 0;
                    } else if (center && status == ChunkStatus.FULL) {
                        ensureRowBudget(); loading = null; return true;
                    } else stage++;
                }
                return false;
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
            if (limits.bounded) {
                if (tickets.add(chunk)) require(tickets.size() <= 256, "One source object spans more than 256 chunks");
                if (world.getChunkManager().getWorldChunk(chunk.x, chunk.z) != null) {
                    holdTicket(world, chunk, ChunkStatus.FULL); return;
                }
                if (loading == null) loading = new LoadRequest(world, chunk);
                require(loading.world == world && loading.target.equals(chunk), "Unexpected overlapping native chunk request");
                if (!loading.advance()) throw RowWaiting.INSTANCE;
                require(world.getChunkManager().getWorldChunk(chunk.x, chunk.z) != null, "Completed native FULL chunk is not accessible");
                return;
            }
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
            NbtCompound payload = row.has("payloadSnbt") ? parseNbt(row.get("payloadSnbt").getAsString())
                    : row.has("payload") ? parseNbt(row.get("payload").toString()) : new NbtCompound();
            NbtList originalEntities=originalSourceBlockEntities.get(row.get("dimension").getAsString()+"/"+pos.asLong());
            if(originalEntities!=null)payload.put("OriginalSourceBlockEntities",originalEntities.copy());
            require(row.has("sourceNativeModelOffsetState") == row.has("sourceNativeModelOffsetPosition"), "Incomplete native model offset annotation");
            if (row.has("sourceNativeModelOffsetState")) {
                JsonArray original = row.getAsJsonArray("sourceNativeModelOffsetPosition");
                require(original.size() == 3, "Bad original native model offset position");
                BlockPos originalPosition = new BlockPos(original.get(0).getAsBigDecimal().intValueExact(),
                        original.get(1).getAsBigDecimal().intValueExact(), original.get(2).getAsBigDecimal().intValueExact());
                require(originalPosition.equals(pos), "Native model offset original source root differs from imported root");
                payload = SourceNativeModelOffsetV127.apply(state, payload, row.get("sourceNativeModelOffsetState").getAsString(), originalPosition);
            }
            if (staticGrassMasonry && StaticGrassMasonryBlock.eligible(state, payload))
                return staticGrassMasonry(world, pos);
            UUID uuid = UUID.fromString(row.get("instanceUuid").getAsString());
            if (world.getBlockEntity(pos) instanceof CompositeBlockEntity saved && saved.resident() != null) {
                // placeInternal canonicalizes a source alias on its FIRST
                // installation. Replay compares the exact same canonical art
                // and typed payload, not merely membership of an alias group.
                BlockState expectedState = state;
                NbtCompound expectedPayload = payload.copy();
                if (expectedState.getBlock() instanceof CompositeRootBlock block) {
                    if (block.spec.id.getPath().equals("prototype_tree"))
                        expectedState = expectedState.with(CompositeRootBlock.VARIANT, 0);
                    if (block.spec.id.getPath().startsWith("source_")) {
                        if (ImportedTypeAliases.needsMigration(expectedState, expectedPayload)) {
                            expectedPayload = ImportedTypeAliases.transformPayload(expectedState, expectedPayload);
                            expectedState = ImportedTypeAliases.target(expectedState);
                        }
                        expectedState = ImportedStairShapes.normalize(expectedState);
                        expectedPayload = ImportedTypeAliases.markPayload(expectedPayload);
                    }
                    if (!(expectedState.getBlock() instanceof ThinWindowRootBlock)) expectedPayload.remove("MountY");
                }
                String expectedRegistry = Registries.BLOCK.getId(expectedState.getBlock()).toString();
                require(saved.resident().instanceId().equals(uuid) && saved.resident().root().equals(CompositeData.cell(pos))
                        && saved.resident().registryId().equals(expectedRegistry), "Source root already occupied by another owner");
                require(world.getBlockState(pos).equals(expectedState), "Previously imported source state changed before resume");
                NbtCompound actualPayload = saved.payload();
                Set<String> checkedKeys = new HashSet<>(expectedPayload.getKeys());
                checkedKeys.addAll(List.of("SourceShift", "MountY", VerticalMount.KEY, ImportedStairLegacyArt.KEY));
                for (String key : checkedKeys) require(Objects.equals(actualPayload.get(key), expectedPayload.get(key)), "Previously imported typed payload changed: " + key);
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
            ensureRowBudget();
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

        /** One reviewed immutable pose. Guest owners still use the existing ledger and
         * transaction lifecycle; this native carrier never acquires its own identity. */
        boolean staticGrassMasonry(ServerWorld world, BlockPos pos) {
            BlockState nextState = StaticGrassMasonryBlock.state();
            if (world.getBlockState(pos).equals(nextState)) {
                require(world.getBlockEntity(pos) == null, "Static grass unexpectedly has a block entity");
                return true;
            }
            require(world.getBlockState(pos).isAir() || world.getBlockState(pos).isOf(CompositeArchitecture.CELL),
                    "Offline phase did not clear static grass root");
            for (var contribution : CompositeRuntime.contributions(world, pos))
                load(world, CompositeData.pos(contribution.owner().root()));
            ensureRowBudget();
            var cell = CompositeData.cell(pos);
            FabricCompositeWorld access = new FabricCompositeWorld(world, Map.of()) {
                @Override public boolean writeSilently(dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell targetCell, CellSnapshot next) {
                    // This narrowly scoped adapter permits the reviewed AIR/HELPER -> native
                    // carrier transition. Full typed rollback and guest writes remain in super.
                    require(targetCell.equals(cell) && next.data().blockId().equals(StaticGrassMasonryBlock.ID.toString()),
                            "Unexpected static pilot write");
                    world.setBlockState(pos, nextState, net.minecraft.block.Block.FORCE_STATE | net.minecraft.block.Block.SKIP_DROPS);
                    return super.writeSilently(targetCell, next);
                }
            };
            CellSnapshot previous = access.read(cell);
            require(previous.resident() == null && (previous.kind() == CellSnapshot.Kind.AIR || previous.kind() == CellSnapshot.Kind.HELPER),
                    "Static pilot cannot replace a resident or foreign block");
            require(CompositeData.nbt(previous.data().blockEntityNbt()).isEmpty(),
                    "Static pilot cannot discard private helper payload");
            Map<dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell, CellSnapshot> expected = new TreeMap<>();
            expected.put(cell, previous);
            for (var guest : previous.guests()) {
                CellSnapshot root = access.read(guest.root());
                require(access.describeRoot(guest, root).filter(object -> object.at(cell) != null).isPresent(),
                        "Static pilot found a stale guest owner");
                expected.put(guest.root(), root);
            }
            CellSnapshot next = new CellSnapshot(CellSnapshot.Kind.FOREIGN, CompositeData.data(nextState, new NbtCompound()), null, previous.guests());
            var plan = new TransactionCore.Plan(access.dimensionKey(), expected, Map.of(cell, next), Map.of());
            var result = CompositeRuntime.execute(access, new TransactionCore.Preparation(plan, null));
            require(result.outcome() == TransactionCore.Outcome.COMMITTED, "Static source transaction rejected: " + result.reason());
            require(world.getBlockState(pos).equals(nextState) && world.getBlockEntity(pos) == null,
                    "Static source was not persisted as a native block");
            return false;
        }

        /** Only accepted, not-yet-static components reach this point. The unchanged
         * migration plan supplies actual writes; no broad component box is loaded.
         * Waiting discards the preparation, so the next attempt reruns eligibility
         * and strict migration reads before full typed rollback capture/any write. */
        void loadStaticWriteNeighbors(ServerWorld world, Collection<dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell> writes) {
            Map<ChunkPos,BlockPos> required = new LinkedHashMap<>();
            for (var cell : writes) {
                BlockPos written = CompositeData.pos(cell);
                for (Direction direction : Direction.values()) {
                    BlockPos adjacent = written.offset(direction);
                    if (!world.isOutOfHeightLimit(adjacent)) required.putIfAbsent(new ChunkPos(adjacent),adjacent);
                }
            }
            for (BlockPos adjacent : required.values()) load(world,adjacent);
        }

        boolean architecture(JsonObject row) throws IOException {
            require(staticArchitecture && phase == 3, "Static architecture phase is not enabled");
            ServerWorld world=world(row);
            for(var raw:row.getAsJsonArray("cells"))load(world,position(raw.getAsJsonObject()));
            for(var raw:row.getAsJsonArray("owners"))load(world,position(raw.getAsJsonObject()));
            ensureRowBudget();
            if(cityStaticArchitecture){
                String unsupported=CityStaticEligibility.inspect(world,row);
                if(unsupported!=null){
                    unsupportedCurrent=true;architectureSkippedOwners+=row.getAsJsonArray("owners").size();architectureSkippedCells+=row.getAsJsonArray("cells").size();
                    architectureSkipReasons.merge(unsupported,1L,Long::sum);
                    JsonObject receipt=new JsonObject();receipt.addProperty("operationId",operation);receipt.addProperty("phase",phase);receipt.addProperty("line",line);receipt.addProperty("componentId",row.get("componentId").getAsString());receipt.addProperty("reason",unsupported);receipt.addProperty("writes",0);receipt.addProperty("rollbackSnapshots",0);
                    Files.writeString(target.resolve("dw-static-architecture-skips.jsonl"),new Gson().toJson(receipt)+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
                    return false;
                }
            }
            var prepared=StaticArchitectureMigration.prepare(world,row);
            if(!prepared.alreadyPresent()){
                loadStaticWriteNeighbors(world,prepared.plan().writes().keySet());
                ensureRowBudget();
                var result=CompositeRuntime.execute(prepared.access(),new TransactionCore.Preparation(prepared.plan(),null));
                require(result.outcome()==TransactionCore.Outcome.COMMITTED,"Static architecture transaction rejected: "+result.reason());
            }
            architectureOwners+=prepared.owners();architectureCells+=prepared.cells();
            return prepared.alreadyPresent();
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
            ensureRowBudget();
            require(world.spawnEntity(entity), "Converted source entity spawn failed " + id);
            return false;
        }

        void releaseTickets() {
            if (limits.bounded) {
                heldTickets.forEach((world, held) -> held.forEach((chunk, distance) -> world.getChunkManager().removeTicket(TICKET, chunk, distance, chunk)));
                heldTickets.clear(); loading = null;
            } else if (ticketWorld != null) for (ChunkPos chunk : tickets) ticketWorld.getChunkManager().removeTicket(TICKET, chunk, 2, chunk);
            tickets.clear(); ticketWorld = null;
        }

        JsonObject snapshot(String status, String failure) {
            JsonObject out = new JsonObject(); out.addProperty("schemaVersion", 1); out.addProperty("status", status);
            out.addProperty("operationId", operation); out.addProperty("productionJarSha256", productionSHA); out.addProperty("runtimeManifestSha256", planSHA);
            out.addProperty("phase", phase); out.addProperty("line", line); out.addProperty("committed", committed); out.addProperty("alreadyPresent", alreadyPresent);
            out.addProperty("targetWorldPath", target.toString()); out.addProperty("originalArchivesWritten", false); out.addProperty("galleryCreated", false);
            out.addProperty("initialOverlapPrivilegeSerialized", false); out.addProperty("elapsedSeconds", (System.nanoTime() - started) / 1_000_000_000d);
            Runtime runtime = Runtime.getRuntime(); out.addProperty("heapUsedBytesObserved", runtime.totalMemory() - runtime.freeMemory()); out.addProperty("heapMaxBytes", runtime.maxMemory());
            out.addProperty("processedThisRun", processedThisRun()); out.addProperty("boundedChunkLoading", limits.bounded);
            out.addProperty("staticGrassMasonry", staticGrassMasonry);
            out.addProperty("staticArchitecture", staticArchitecture);
            out.addProperty("architectureOwners", architectureOwners);out.addProperty("architectureCells", architectureCells);
            out.addProperty("cityStaticArchitecture",cityStaticArchitecture);out.addProperty("architectureSkipped",architectureSkipped);out.addProperty("architectureSkippedOwners",architectureSkippedOwners);out.addProperty("architectureSkippedCells",architectureSkippedCells);out.add("architectureSkipReasons",JSON.toJsonTree(architectureSkipReasons));
            out.addProperty("existingSourceProtoAdmissions", existingSourceProtoAdmissions);
            out.addProperty("sourceProtoMinimumStatus", "minecraft:features");
            out.addProperty("phaseCount", phases.length);
            if (limits.bounded) { out.add("chunkLifecycle", chunkSnapshot()); out.add("boundedChunkLimits", JSON.toJsonTree(limits)); }
            if (limits.bounded) {
                out.add("observedNativeChunkHighWater", JSON.toJsonTree(new ChunkCounts(peakLoadedWorldChunks,
                        peakHolders, peakReadableHolders, peakPendingHolders, peakChunksToUnload, peakUnloadQueue)));
                out.addProperty("nativeChunkHighWaterDefinition", "Per-job maxima of the six real native totals, sampled at existing diagnostics/budget checks and once per bounded server tick; intervals between observations may be missed. Six primitive counters only; no chunk, holder or future references retained.");
            }
            JsonObject memory = new JsonObject(); for (ServerWorld world : rules.keySet()) memory.add(world.getRegistryKey().getValue().toString(), JSON.toJsonTree(CompositeLedger.get(world).shardMetrics())); out.add("ownerStorage", memory);
            JsonObject originals = new JsonObject(); rules.forEach((world, saved) -> originals.add(world.getRegistryKey().getValue().toString(), JSON.toJsonTree(saved))); out.add("originalRules", originals);
            if (current != null) out.add("currentSourceRow", current.deepCopy()); if (failure != null) out.addProperty("failure", failure);
            return out;
        }

        void durable(String status) throws IOException {
            releaseTickets(); server.save(false, true, true);
            write(checkpoint, snapshot(status, null)); write(report, snapshot(status, null)); checkpointCount = committed + alreadyPresent + architectureSkipped;
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
    private static void write(Path path, JsonObject value) throws IOException {
        Path pending = path.resolveSibling(path.getFileName() + ".pending");
        Files.writeString(pending, JSON.toJson(value) + "\n", StandardCharsets.UTF_8);
        for (int attempt = 0; ; attempt++) {
            try {
                Files.move(pending, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                return;
            } catch (AccessDeniedException denied) {
                // Antivirus/readers can briefly deny replacement on Windows.
                // Retain the old complete file and the closed pending file;
                // never fall back to truncating/copying the durable target.
                if (attempt >= 7) throw denied;
                try { Thread.sleep(10); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt(); denied.addSuppressed(interrupted); throw denied;
                }
            }
        }
    }
}
