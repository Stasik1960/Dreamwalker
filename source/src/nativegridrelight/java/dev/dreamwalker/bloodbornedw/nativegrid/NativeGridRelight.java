package dev.dreamwalker.bloodbornedw.nativegrid;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Standalone, opt-in authoring helper. It is never part of the release JAR.
 * Drives the target server's real LIGHT pipeline in bounded ticket batches.
 * A batch is durable only after the server has flushed its actual chunk saves.
 */
public final class NativeGridRelight implements ModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("native-grid-relight");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ChunkTicketType<ChunkPos> TICKET = ChunkTicketType.create("native_grid_relight", Comparator.comparingLong(ChunkPos::toLong));
    private static Runner runner;

    @Override public void onInitialize() {
        NativeGridDoorFootprintExport.register();
        if (System.getProperty("nativegrid.relight.queue") == null) return;
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try { runner = new Runner(server); }
            catch (Throwable error) { LOG.error("Relight startup failed", error); server.stop(false); }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (runner == null) return;
            try { runner.tick(); }
            catch (Throwable error) { runner.fail(error); }
        });
    }

    private record Work(String dimension, int x, int z) {
        String line() { return dimension + "\t" + x + "\t" + z; }
        ChunkPos pos() { return new ChunkPos(x, z); }
    }

    private static final class Runner {
        final MinecraftServer server;
        final Path queue, output, doneFile, worldPath;
        final String queueHash, runtimeHash, catalogHash;
        final List<Work> work;
        final NativeGridAssemblyRelight assemblies;
        final Set<String> done = new HashSet<>();
        final List<Work> batch = new ArrayList<>();
        final List<CompletableFuture<Chunk>> light = new ArrayList<>();
        final int batchSize = Math.max(1, Math.min(8, Integer.getInteger("nativegrid.relight.batch", 2)));
        ServerWorld world;
        int cursor, state, ticks, startTick, peakLoaded;
        long started = System.nanoTime();
        boolean finished;

        Runner(MinecraftServer server) throws Exception {
            this.server = server;
            queue = Path.of(System.getProperty("nativegrid.relight.queue")).toAbsolutePath().normalize();
            output = Path.of(System.getProperty("nativegrid.relight.receipt", queue.resolveSibling("relight-engine.json").toString())).toAbsolutePath().normalize();
            doneFile = output.resolveSibling(output.getFileName() + ".done.tsv");
            queueHash = hash(queue);
            JsonObject binding = GSON.fromJson(Files.readString(queue.resolveSibling("relight-binding.json")), JsonObject.class);
            require(queueHash.equals(binding.get("queueSha256").getAsString()), "Queue binding hash differs");
            worldPath = server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();
            require(worldPath.equals(Path.of(binding.get("target").getAsString()).toAbsolutePath().normalize()), "Relight server opened another world");
            Path production = FabricLoader.getInstance().getModContainer("bloodborne_dw").orElseThrow().getOrigin().getPaths().get(0);
            require(Files.isRegularFile(production), "Run the relight helper with the actual production JAR");
            runtimeHash = hash(production);
            require(runtimeHash.equals(binding.getAsJsonObject("runtime").get("sha256").getAsString()), "Loaded production JAR differs from migration binding");
            try (InputStream stream = NativeGridRelight.class.getClassLoader().getResourceAsStream("assets/bloodborne_dw/native_grid/catalog.json")) {
                require(stream != null, "Loaded native-grid catalogue is absent");
                catalogHash = hash(stream);
            }
            require(catalogHash.equals(binding.getAsJsonObject("catalog").get("sha256").getAsString()), "Loaded catalogue differs from migration binding");
            LinkedHashSet<Work> unique = new LinkedHashSet<>();
            for (String line : Files.readAllLines(queue)) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] fields = line.split("\t");
                require(fields.length == 3, "Malformed relight queue row");
                unique.add(new Work(fields[0], Integer.parseInt(fields[1]), Integer.parseInt(fields[2])));
            }
            work = List.copyOf(unique);
            if (Files.exists(doneFile)) {
                List<String> lines = Files.readAllLines(doneFile);
                require(!lines.isEmpty() && lines.get(0).equals("# " + queueHash + " " + runtimeHash), "Resume journal belongs to another queue/runtime");
                done.addAll(lines.subList(1, lines.size()));
                require(work.stream().map(Work::line).collect(java.util.stream.Collectors.toSet()).containsAll(done), "Resume journal contains foreign chunks");
            } else {
                Files.createDirectories(doneFile.getParent());
                append(doneFile, "# " + queueHash + " " + runtimeHash + "\n");
            }
            assemblies=binding.has("fullCityFreshLight") && binding.get("fullCityFreshLight").getAsBoolean()
                    ? new NativeGridAssemblyRelight(server,binding,work.stream().map(Work::line).collect(java.util.stream.Collectors.toSet()),!done.isEmpty(),worldPath,runtimeHash,catalogHash,output) : null;
            checkpoint("RUNNING", false, null);
            LOG.info("Native relight: {} queued, {} already saved, batch {}", work.size(), done.size(), batchSize);
        }

        void tick() throws Exception {
            if (finished) return;
            ticks++;
            if(assemblies!=null && !assemblies.tick())return;
            for (ServerWorld value : server.getWorlds()) peakLoaded = Math.max(peakLoaded, value.getChunkManager().getLoadedChunkCount());
            require(state == 0 || ticks - startTick < 20 * 900, "Native light batch timed out");
            if (state == 0) {
                while (cursor < work.size() && done.contains(work.get(cursor).line())) cursor++;
                if (cursor == work.size()) {
                    server.save(false, true, true);
                    checkpoint("PASS", true, null);
                    finished = true;
                    if (Boolean.parseBoolean(System.getProperty("nativegrid.relight.stop", "true"))) server.stop(false);
                    return;
                }
                Work first = work.get(cursor);
                world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, new Identifier(first.dimension)));
                require(world != null, "Relight dimension absent: " + first.dimension);
                batch.clear(); light.clear();
                while (cursor < work.size() && batch.size() < batchSize && work.get(cursor).dimension.equals(first.dimension)) {
                    Work entry = work.get(cursor++);
                    if (done.contains(entry.line())) continue;
                    batch.add(entry);
                    world.getChunkManager().addTicket(TICKET, entry.pos(), 0, entry.pos());
                }
                state = 1; startTick = ticks;
                return;
            }
            if (state == 1 && ticks - startTick >= 2) {
                for (Work entry : batch) {
                    CompletableFuture<Chunk> loaded = world.getChunkManager().getChunkFutureSyncOnMainThread(entry.x, entry.z, ChunkStatus.FULL, false)
                            .thenApply(value -> value.left().orElseThrow(() -> new IllegalStateException("Native chunk load failed: " + entry.line())));
                    light.add(loaded);
                }
                state = 2;
                return;
            }
            if (state == 2 && light.stream().allMatch(CompletableFuture::isDone)) {
                List<Chunk> chunks = light.stream().map(CompletableFuture::join).toList();
                light.clear();
                for (Chunk chunk : chunks) {
                    // Heightmaps are recomputed from actual target states. Never
                    // publish isLightOn=true ourselves: only native light does.
                    Heightmap.populateHeightmaps(chunk, EnumSet.allOf(Heightmap.Type.class));
                    chunk.setLightOn(false);
                    light.add(world.getChunkManager().getLightingProvider().initializeLight(chunk, false)
                            .thenCompose(value -> world.getChunkManager().getLightingProvider().light(value, false)));
                }
                state = 3;
                return;
            }
            if (state == 3 && light.stream().allMatch(CompletableFuture::isDone)) {
                for (CompletableFuture<Chunk> future : light) {
                    Chunk chunk = future.join();
                    require(chunk.isLightOn(), "Native engine did not complete chunk light: " + chunk.getPos());
                    chunk.setNeedsSaving(true);
                }
                world.getChunkManager().save(true);
                server.save(false, true, true);
                // Save first, then fsync progress. A crash before the journal
                // append harmlessly repeats the last bounded native batch.
                StringBuilder text = new StringBuilder();
                for (Work entry : batch) { text.append(entry.line()).append('\n'); done.add(entry.line()); }
                append(doneFile, text.toString());
                for (Work entry : batch) world.getChunkManager().removeTicket(TICKET, entry.pos(), 0, entry.pos());
                checkpoint("RUNNING", true, null);
                state = 4; startTick = ticks;
            }
            // Give ordinary server unload processing time between batches.
            if (state == 4 && ticks - startTick >= 4) { batch.clear(); light.clear(); state = 0; }
        }

        void fail(Throwable error) {
            if (finished) return;
            finished = true;
            LOG.error("Native relight failed; saved cursor is resumable", error);
            if(assemblies!=null)assemblies.release();
            try { checkpoint("FAIL", false, error.toString()); } catch (Exception secondary) { LOG.error("Could not write relight failure receipt", secondary); }
            server.stop(false);
        }

        void checkpoint(String status, boolean saved, String error) throws Exception {
            JsonObject report = new JsonObject();
            report.addProperty("schemaVersion", 1); report.addProperty("status", status); report.addProperty("nativeEngine", true);
            report.addProperty("world", worldPath.toString()); report.addProperty("queueSha256", queueHash);
            report.addProperty("runtimeSha256", runtimeHash); report.addProperty("catalogSha256", catalogHash);
            report.addProperty("queued", work.size()); report.addProperty("processed", done.size()); report.addProperty("pending", work.size()-done.size());
            report.addProperty("saveFlushed", saved); report.addProperty("batchSize", batchSize); report.addProperty("peakLoadedChunksPerDimension", peakLoaded);
            report.addProperty("seconds", (System.nanoTime()-started)/1e9); report.addProperty("error", error);
            if(assemblies!=null && (status.equals("PASS") || saved))report.add("assemblyPhase",assemblies.reference());
            Path temporary = output.resolveSibling(output.getFileName()+".writing");
            Files.createDirectories(output.getParent());
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                ByteBuffer buffer=StandardCharsets.UTF_8.encode(GSON.toJson(report)+"\n"); while(buffer.hasRemaining())channel.write(buffer); channel.force(true);
            }
            Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private static String hash(Path file) throws Exception { try(InputStream stream=Files.newInputStream(file)){return hash(stream);} }
    private static String hash(InputStream stream) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256"); byte[] buffer=new byte[1024*1024];int n;
        while((n=stream.read(buffer))!=-1)digest.update(buffer,0,n);return HexFormat.of().formatHex(digest.digest());
    }
    private static void append(Path file, String text) throws IOException {
        try(FileChannel channel=FileChannel.open(file,StandardOpenOption.CREATE,StandardOpenOption.APPEND,StandardOpenOption.WRITE)) {
            ByteBuffer data=StandardCharsets.UTF_8.encode(text);while(data.hasRemaining())channel.write(data);channel.force(true);
        }
    }
}
