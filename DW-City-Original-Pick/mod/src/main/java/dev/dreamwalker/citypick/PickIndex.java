package dev.dreamwalker.citypick;

import com.google.gson.JsonParser;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads small parts of the generated index only after a pick request. */
final class PickIndex {
    private static final int CHUNK_MAGIC = 0x44575043;
    private static final int POSE_MAGIC = 0x44575031;
    private static final int ROUTE_MAGIC = 0x44575052;
    private static final int ROUTES_PER_FILE = 64;
    private static final int MAX_CHUNKS = 24;
    private static final int MAX_ROUTE_FILES = 8;
    private static final ChunkRows EMPTY_CHUNK = new ChunkRows(new int[0], new int[0], new int[0]);

    private final Map<ChunkKey, ChunkRows> chunks = new LinkedHashMap<>(16, .75f, true);
    private final Map<Integer, List<Route>> routeFiles = new LinkedHashMap<>(16, .75f, true);
    private PoseBook book;
    private CompatibilityBook compatibility;

    record ChunkKey(String namespace, String dimension, int x, int z) {
        String file() {
            return "chunks/" + namespace + "/" + dimension + "/" + x + "." + z + ".bin";
        }
    }

    record Pose(String registryId, String legacyArt, Map<String, String> properties) {}
    private record PoseBook(String catalog, String version, List<Pose> poses) {}
    private record CompatibilityBook(String catalog, Map<Integer, Pose> poses) {}
    private record ChunkRows(int[] cells, int[] visuals, int[] routes) {}
    private record Candidate(int pose, short[] quads) {}
    private record Route(List<Candidate> candidates) {}

    synchronized List<Pose> find(ChunkKey chunk, int cell, int visual, String catalog, String version, FragmentRay ray)
            throws IOException {
        ChunkRows rows = chunks.get(chunk);
        if (rows == null) {
            rows = readChunk(chunk);
            remember(chunks, chunk, rows, MAX_CHUNKS);
        }
        int index = Arrays.binarySearch(rows.cells, cell);
        if (index < 0 || rows.visuals[index] != visual) return findCompatibility(visual, catalog);
        if (book == null) book = readPoses();
        if (!book.catalog.equals(catalog) || visual < 0 && !book.version.equals(version)) return List.of();
        int routeId = rows.routes[index];
        if (routeId < 0) return List.of(pose(-1 - routeId));
        var route = route(routeId);
        Candidate winner = closest(route, ray);
        if (winner != null) return List.of(pose(winner.pose));
        List<Pose> choices = new ArrayList<>(route.candidates.size());
        for (var candidate : route.candidates) choices.add(pose(candidate.pose));
        return List.copyOf(choices);
    }

    private List<Pose> findCompatibility(int visual, String catalog) throws IOException {
        if (visual < 0) return List.of();
        if (compatibility == null) compatibility = readCompatibility();
        if (!compatibility.catalog.equals(catalog)) return List.of();
        Pose pose = compatibility.poses.get(visual);
        return pose == null ? List.of() : List.of(pose);
    }

    private static CompatibilityBook readCompatibility() throws IOException {
        try (var resource = PickIndex.class.getResourceAsStream("/city_original_pick/compatibility-visuals.json")) {
            if (resource == null) return new CompatibilityBook("", Map.of());
            var document = JsonParser.parseReader(new InputStreamReader(resource, StandardCharsets.UTF_8)).getAsJsonObject();
            require(document.get("schema").getAsInt() == 2, "Invalid compatibility visual index");
            Map<Integer, Pose> poses = new LinkedHashMap<>();
            for (var entry : document.getAsJsonObject("visuals").entrySet()) {
                var source = entry.getValue().getAsJsonObject();
                Map<String, String> properties = new LinkedHashMap<>();
                for (var property : source.getAsJsonObject("properties").entrySet()) {
                    properties.put(property.getKey(), property.getValue().getAsString());
                }
                poses.put(Integer.parseInt(entry.getKey()),
                        new Pose(source.get("registryId").getAsString(),
                                source.has("legacyArt") ? source.get("legacyArt").getAsString() : "", Map.copyOf(properties)));
            }
            return new CompatibilityBook(document.get("catalogSha256").getAsString(), Map.copyOf(poses));
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid compatibility visual index", invalid);
        }
    }

    static int cellKey(int x, int y, int z) {
        return ((y + 2048) << 8) | ((z & 15) << 4) | (x & 15);
    }

    private Pose pose(int id) throws IOException {
        if (id < 0 || id >= book.poses.size()) throw new IOException("Invalid source pose ID " + id);
        return book.poses.get(id);
    }

    private Route route(int id) throws IOException {
        int file = id / ROUTES_PER_FILE;
        List<Route> routes = routeFiles.get(file);
        if (routes == null) {
            routes = readRoutes(file);
            remember(routeFiles, file, routes, MAX_ROUTE_FILES);
        }
        int offset = id % ROUTES_PER_FILE;
        if (offset >= routes.size()) throw new IOException("Missing source route " + id);
        return routes.get(offset);
    }

    private static Candidate closest(Route route, FragmentRay ray) {
        Candidate best = null;
        double distance = Double.POSITIVE_INFINITY;
        boolean tied = false;
        for (var candidate : route.candidates) {
            // An unresolved visible source could be in front of the known surfaces.
            if (candidate.quads.length == 0) return null;
            double hit = ray.distance(candidate.quads);
            if (!Double.isFinite(hit)) continue;
            if (hit < distance - FragmentRay.EPSILON) {
                best = candidate;
                distance = hit;
                tied = false;
            } else if (Math.abs(distance - hit) <= FragmentRay.EPSILON) {
                tied = true;
            }
        }
        return tied ? null : best;
    }

    private static ChunkRows readChunk(ChunkKey chunk) throws IOException {
        try (var in = open(chunk.file())) {
            if (in == null) return EMPTY_CHUNK;
            require(in.readInt() == CHUNK_MAGIC, "Invalid city chunk index");
            int size = in.readInt();
            require(size >= 0 && size <= 98_304, "Invalid indexed city cell count");
            int[] cells = new int[size], visuals = new int[size], routes = new int[size];
            for (int i = 0; i < size; i++) {
                cells[i] = in.readInt();
                visuals[i] = in.readInt();
                routes[i] = in.readInt();
                require(i == 0 || cells[i - 1] < cells[i], "Unsorted city chunk index");
            }
            return new ChunkRows(cells, visuals, routes);
        }
    }

    private static PoseBook readPoses() throws IOException {
        try (var in = open("poses.bin")) {
            require(in != null && in.readInt() == POSE_MAGIC, "Invalid source pose table");
            String catalog = in.readUTF();
            String version = in.readUTF();
            int size = in.readInt();
            require(size >= 0 && size <= 65_536, "Invalid source pose count");
            List<Pose> poses = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                String id = in.readUTF();
                String legacy = in.readUTF();
                int count = in.readUnsignedByte();
                Map<String, String> properties = new LinkedHashMap<>();
                for (int j = 0; j < count; j++) properties.put(in.readUTF(), in.readUTF());
                poses.add(new Pose(id, legacy, Map.copyOf(properties)));
            }
            return new PoseBook(catalog, version, List.copyOf(poses));
        }
    }

    private static List<Route> readRoutes(int file) throws IOException {
        try (var in = open("routes/" + file + ".bin")) {
            require(in != null && in.readInt() == ROUTE_MAGIC, "Invalid source route file");
            int size = in.readUnsignedByte();
            require(size > 0 && size <= ROUTES_PER_FILE, "Invalid source route count");
            List<Route> routes = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                int count = in.readUnsignedByte();
                require(count >= 2 && count <= 64, "Invalid source candidate count");
                List<Candidate> candidates = new ArrayList<>(count);
                for (int j = 0; j < count; j++) {
                    int pose = in.readUnsignedShort();
                    int quads = in.readUnsignedShort();
                    require(quads <= 2048, "Source geometry exceeds a city cell");
                    short[] vertices = new short[quads * 12];
                    for (int k = 0; k < vertices.length; k++) vertices[k] = in.readShort();
                    candidates.add(new Candidate(pose, vertices));
                }
                routes.add(new Route(List.copyOf(candidates)));
            }
            return List.copyOf(routes);
        }
    }

    private static DataInputStream open(String path) {
        var resource = PickIndex.class.getResourceAsStream("/city_original_pick/" + path);
        return resource == null ? null : new DataInputStream(new BufferedInputStream(resource));
    }

    private static void require(boolean condition, String message) throws IOException {
        if (!condition) throw new IOException(message);
    }

    private static <K, V> void remember(Map<K, V> map, K key, V value, int limit) {
        map.put(key, value);
        if (map.size() > limit) map.remove(map.keySet().iterator().next());
    }
}
