package dev.dreamwalker.maplighting;

import com.mojang.logging.LogUtils;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.HashSet;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.GameRules;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkProvider;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.light.LightSourceView;
import net.minecraft.world.chunk.light.LightingProvider;
import org.slf4j.Logger;

public final class LightingMaintenance implements DedicatedServerModInitializer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int TILE_SIZE = 7;
    private final ArrayDeque<Work> work = new ArrayDeque<>();
    private final Map<String, Set<ChunkPos>> saved = new HashMap<>();
    private int count;
    private boolean completed;
    private long start;

    @Override
    public void onInitializeServer() {
        if (!Files.isRegularFile(Path.of("lighting-disposable.marker"))) {
            throw new IllegalStateException("Lighting maintenance requires a disposable map copy.");
        }
        ServerLifecycleEvents.SERVER_STARTED.register(this::prepare);
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
    }

    private void prepare(MinecraftServer server) {
        try {
            try (var writer = Files.newBufferedWriter(Path.of("registry.tsv"))) {
                for (var block : Registries.BLOCK) {
                    for (var state : block.getStateManager().getStates()) {
                        writer.write(stateKey(block, state));
                        writer.write('\t');
                        writer.write(Integer.toString(state.getLuminance()));
                        writer.newLine();
                    }
                }
            }

            Map<String, ServerWorld> worlds = new HashMap<>();
            for (var world : server.getWorlds()) {
                String key = world.getRegistryKey().getValue().toString();
                worlds.put(key, world);
                world.getGameRules().get(GameRules.RANDOM_TICK_SPEED).set(0, server);
                world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
                world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
                world.setTimeOfDay(18000);
            }
            loadPlan(worlds);
            LOGGER.info("DW_LIGHTING start {} targets in {} tiles, set time 18000", count, work.size());
            start = System.nanoTime();
        } catch (Exception failure) {
            fail(server, failure);
        }
    }

    private void loadPlan(Map<String, ServerWorld> worlds) throws IOException {
        try (var reader = Files.newBufferedReader(Path.of("lighting-plan.tsv"))) {
            for (String line; (line = reader.readLine()) != null;) {
                String[] cols = line.split("\t");
                String dimension = cols[1];
                var pos = new ChunkPos(Integer.parseInt(cols[2]), Integer.parseInt(cols[3]));
                if (cols[0].equals("existing")) {
                    saved.computeIfAbsent(dimension, ignored -> new HashSet<>())
                        .add(pos);
                }
            }
        }
        Map<Tile, List<ChunkPos>> tiles = new LinkedHashMap<>();
        try (var reader = Files.newBufferedReader(Path.of("lighting-plan.tsv"))) {
            for (String line; (line = reader.readLine()) != null;) {
                String[] cols = line.split("\t");
                if (!cols[0].equals("target")) continue;
                String dimension = cols[1];
                var pos = new ChunkPos(Integer.parseInt(cols[2]), Integer.parseInt(cols[3]));
                if (!saved.get(dimension).contains(pos)) {
                    throw new IOException("Target chunk is missing " + dimension + ' ' + pos);
                }
                var tile = new Tile(dimension, Math.floorDiv(pos.x, TILE_SIZE), Math.floorDiv(pos.z, TILE_SIZE));
                tiles.computeIfAbsent(tile, ignored -> new ArrayList<>()).add(pos);
                count++;
            }
        }
        for (var entry : tiles.entrySet()) {
            var tile = entry.getKey();
            var world = worlds.get(tile.dimension);
            if (world == null) throw new IOException("Dimension is missing " + tile.dimension);
            work.add(new Work(tile, world, entry.getValue()));
        }
    }

    private void tick(MinecraftServer server) {
        if (completed) return;
        try {
            var next = work.poll();
            if (next != null) {
                bake(next);
                return;
            }
            Files.writeString(Path.of("lighting.done"), count + " chunks relit\n");
            LOGGER.info("DW_LIGHTING done: {} chunks in {} s", count, (System.nanoTime() - start) / 1e9);
            completed = true;
            server.stop(false);
        } catch (Exception failure) {
            fail(server, failure);
        }
    }

    private void bake(Work item) throws IOException {
        var world = item.world;
        var tile = item.tile;
        Map<Long, Chunk> window = new HashMap<>();
        var available = saved.get(tile.dimension);
        // Block and indirect sky light travel at most 14 lateral blocks.
        // One fully decoded chunk of padding covers every path into the tile.
        for (int x = tile.x * TILE_SIZE - 1; x <= (tile.x + 1) * TILE_SIZE; x++) {
            for (int z = tile.z * TILE_SIZE - 1; z <= (tile.z + 1) * TILE_SIZE; z++) {
                var pos = new ChunkPos(x, z);
                if (available.contains(pos)) {
                    var chunk = world.getChunkManager().getChunk(x, z, ChunkStatus.FULL, true);
                    if (chunk == null) throw new IOException("Could not load chunk " + pos);
                    chunk.getChunkSkyLight().refreshSurfaceY(chunk);
                    window.put(pos.toLong(), chunk);
                }
            }
        }

        var lighting = new LightingProvider(new ChunkProvider() {
            @Override public LightSourceView getChunk(int x, int z) { return window.get(ChunkPos.toLong(x, z)); }
            @Override public BlockView getWorld() { return world; }
        }, true, world.getDimension().hasSkyLight());
        for (var chunk : window.values()) {
            var pos = chunk.getPos();
            var sections = chunk.getSectionArray();
            for (int i = 0; i < sections.length; i++) {
                if (!sections[i].isEmpty()) {
                    lighting.setSectionStatus(ChunkSectionPos.from(pos, world.sectionIndexToCoord(i)), false);
                }
            }
            lighting.setColumnEnabled(pos, true);
        }
        while (lighting.hasUpdates()) lighting.doLightUpdates();
        for (var chunk : window.values()) lighting.propagateLight(chunk.getPos());
        while (lighting.hasUpdates()) lighting.doLightUpdates();

        var dest = Path.of("relit", tile.dimension.replace(':', '_'));
        Files.createDirectories(dest);
        for (var pos : item.targets) {
            try (var out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(
                    dest.resolve(pos.x + "." + pos.z + ".bin"))))) {
                out.writeInt(lighting.getBottomY());
                out.writeInt(lighting.getTopY());
                for (int y = lighting.getBottomY(); y < lighting.getTopY(); y++) {
                    var section = ChunkSectionPos.from(pos, y);
                    for (var type : LightType.values()) {
                        var nibble = lighting.get(type).getLightSection(section);
                        out.writeBoolean(nibble != null);
                        if (nibble != null) out.write(nibble.asByteArray());
                    }
                }
            }
        }
        LOGGER.info("DW_LIGHTING {} {} {}: {} tiles left, {} loaded", tile.dimension, tile.x, tile.z,
            work.size(), window.size());
    }

    private void fail(MinecraftServer server, Exception failure) {
        LOGGER.error("DW_LIGHTING failed", failure);
        completed = true;
        server.stop(false);
    }

    private static String stateKey(Block block, BlockState state) {
        var key = Registries.BLOCK.getId(block).toString();
        if (state.getEntries().isEmpty()) return key;
        var props = new TreeMap<String, String>();
        for (var prop : state.getProperties()) {
            props.put(prop.getName(), valueName(prop, state));
        }
        var out = new StringBuilder(key).append('[');
        boolean first = true;
        for (var entry : props.entrySet()) {
            if (!first) out.append(',');
            first = false;
            out.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return out.append(']').toString();
    }

    private static <T extends Comparable<T>> String valueName(Property<T> property, BlockState state) {
        return property.name(state.get(property));
    }

    private record Tile(String dimension, int x, int z) {}
    private record Work(Tile tile, ServerWorld world, List<ChunkPos> targets) {}
}
