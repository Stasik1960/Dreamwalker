package ru.modelprops.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import ru.modelprops.ModelProps;
import ru.modelprops.net.ModelPropsAssetProtocol;
import ru.modelprops.server.upload.OggVorbisValidator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class ServerModelCatalog {
    public static final ServerModelCatalog INSTANCE = new ServerModelCatalog();
    public static final int MAX_MODELS = 512;
    public static final int MAX_MODEL_BYTES = 256 * 1024;
    public static final int MAX_TEXTURE_BYTES = 4 * 1024 * 1024;
    public static final int MAX_TEXTURES = 8_192;
    public static final long MAX_CATALOG_BYTES = 64L * 1024L * 1024L;

    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9/._-]+");
    private static final byte[] EXAMPLE_TEXTURE = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    private final Path root = FabricLoader.getInstance().getConfigDir().resolve("modelprops");
    private final Path modelsRoot = root.resolve("models");
    private final Path texturesRoot = root.resolve("textures");
    private final Path animationsRoot = root.resolve("animations");
    private final Path soundsRoot = root.resolve("sounds");
    private final ServerTransformStore transforms = new ServerTransformStore(root);

    private Map<String, ServerModelEntry> entries = Map.of();
    private int generation;

    private ServerModelCatalog() {
    }

    public synchronized void initialize() {
        try {
            Files.createDirectories(modelsRoot);
            Files.createDirectories(texturesRoot);
            Files.createDirectories(animationsRoot);
            Files.createDirectories(soundsRoot);
            createExampleIfEmpty();
        } catch (IOException exception) {
            ModelProps.LOGGER.error("Could not prepare the Model Props config directory", exception);
        }
        reload();
    }

    public synchronized ReloadResult reload() {
        transforms.load();
        LinkedHashMap<String, ServerModelEntry> loaded = new LinkedHashMap<>();
        Set<String> loadedTextures = new HashSet<>();
        ArrayList<String> errors = new ArrayList<>();
        long totalBytes = 0L;
        boolean scanSucceeded = true;
        try {
            Files.createDirectories(modelsRoot);
            Files.createDirectories(texturesRoot);
            Files.createDirectories(animationsRoot);
            Files.createDirectories(soundsRoot);
            List<Path> files;
            try (Stream<Path> stream = Files.walk(modelsRoot)) {
                files = stream.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                        .sorted(Comparator.comparing(Path::toString))
                        .limit(MAX_MODELS + 1L)
                        .toList();
            }
            if (files.size() > MAX_MODELS) {
                errors.add("Too many models (maximum " + MAX_MODELS + ")");
                files = files.subList(0, MAX_MODELS);
            }
            for (Path file : files) {
                try {
                    ServerModelEntry entry = readModel(file);
                    long entryBytes = entry.id().getBytes(StandardCharsets.UTF_8).length
                            + entry.displayName().getBytes(StandardCharsets.UTF_8).length
                            + entry.json().getBytes(StandardCharsets.UTF_8).length;
                    for (byte[] texture : entry.textures().values()) {
                        entryBytes += texture.length;
                    }
                    entryBytes += entry.animationJson().getBytes(StandardCharsets.UTF_8).length;
                    for (byte[] sound : entry.sounds().values()) {
                        entryBytes += sound.length;
                    }
                    if (totalBytes + entryBytes > MAX_CATALOG_BYTES) {
                        errors.add("Catalog is larger than 64 MiB; skipped " + entry.id());
                        continue;
                    }
                    if (loaded.containsKey(entry.id())) {
                        errors.add("Duplicate model id: " + entry.id());
                        continue;
                    }
                    long newTextures = entry.textures().keySet().stream()
                            .filter(reference -> !loadedTextures.contains(reference))
                            .count();
                    if (loadedTextures.size() + newTextures > MAX_TEXTURES) {
                        errors.add("Catalog has more than " + MAX_TEXTURES + " textures; skipped " + entry.id());
                        continue;
                    }
                    loaded.put(entry.id(), entry);
                    loadedTextures.addAll(entry.textures().keySet());
                    totalBytes += entryBytes;
                } catch (Exception exception) {
                    String relative = modelsRoot.relativize(file).toString();
                    errors.add(relative + ": " + exception.getMessage());
                    ModelProps.LOGGER.warn("Could not load model {}", file, exception);
                }
            }
        } catch (IOException exception) {
            errors.add("Could not scan model directory: " + exception.getMessage());
            ModelProps.LOGGER.error("Could not scan {}", modelsRoot, exception);
            scanSucceeded = false;
        }

        if (!scanSucceeded) {
            ModelProps.LOGGER.warn("Keeping the previous Model Props catalog after a scan failure");
            return new ReloadResult(entries.size(), List.copyOf(errors));
        }
        entries = Collections.unmodifiableMap(loaded);
        generation++;
        ModelProps.LOGGER.info("Loaded {} Model Props model(s), generation {}", entries.size(), generation);
        return new ReloadResult(entries.size(), List.copyOf(errors));
    }

    private ServerModelEntry readModel(Path file) throws IOException {
        Path realRoot = root.toRealPath();
        Path realModelsRoot = modelsRoot.toRealPath();
        if (!realModelsRoot.startsWith(realRoot)) {
            throw new IOException("models directory symlink points outside config/modelprops");
        }
        Path realFile = file.toRealPath();
        if (!realFile.startsWith(realModelsRoot)) {
            throw new IOException("Model symlink points outside config/modelprops/models");
        }
        long size = Files.size(file);
        if (size <= 0L || size > MAX_MODEL_BYTES) {
            throw new IOException("JSON size must be between 1 byte and " + MAX_MODEL_BYTES + " bytes");
        }
        String json = Files.readString(file, StandardCharsets.UTF_8);
        JsonObject object = JsonParser.parseString(json).getAsJsonObject();
        String id = idFromPath(modelsRoot.relativize(file));
        if (id.length() > 128) {
            throw new IOException("Model id is longer than 128 characters");
        }
        String displayName = object.has("display_name") && object.get("display_name").isJsonPrimitive()
                ? object.get("display_name").getAsString() : titleFromId(id);
        if (displayName.isBlank() || displayName.length() > 128) {
            throw new IOException("display_name must contain 1-128 characters");
        }

        Map<String, byte[]> textures = new LinkedHashMap<>();
        if (object.has("textures") && object.get("textures").isJsonObject()) {
            JsonObject aliases = object.getAsJsonObject("textures");
            Set<String> terminals = new LinkedHashSet<>();
            for (Map.Entry<String, JsonElement> entry : aliases.entrySet()) {
                if (entry.getValue().isJsonPrimitive()) {
                    String terminal = resolveAlias(entry.getValue().getAsString(), aliases);
                    if (terminal.length() > 256) {
                        throw new IOException("Texture reference is longer than 256 characters");
                    }
                    if (!terminal.startsWith("#")) {
                        terminals.add(terminal);
                    }
                }
            }
            for (String reference : terminals) {
                Path textureFile = findTexture(reference);
                if (textureFile != null) {
                    long textureSize = Files.size(textureFile);
                    if (textureSize <= 0 || textureSize > MAX_TEXTURE_BYTES) {
                        throw new IOException("Texture " + reference + " is larger than 4 MiB");
                    }
                    textures.put(reference, Files.readAllBytes(textureFile));
                }
            }
        }
        String animationJson = readAnimation(id);
        Map<String, byte[]> sounds = readSounds(id);
        return new ServerModelEntry(id, displayName, json, textures, animationJson, sounds);
    }

    private String readAnimation(String id) throws IOException {
        Path candidate = sidecarPath(animationsRoot, id, ".json");
        if (!Files.exists(candidate, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return "";
        }
        Path file = checkedRegularFile(animationsRoot, candidate, "Animation");
        long size = Files.size(file);
        if (size < 1L || size > ModelPropsAssetProtocol.MAX_ANIMATION_BYTES) {
            throw new IOException("Animation JSON size must be between 1 byte and 512 KiB");
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    private Map<String, byte[]> readSounds(String id) throws IOException {
        Path candidate = soundSidecarPath(soundsRoot, id);
        if (!Files.exists(candidate, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return Map.of();
        }
        if (Files.isSymbolicLink(candidate)
                || !Files.isDirectory(candidate, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Sound sidecar is not a regular directory");
        }
        Path realConfigRoot = root.toRealPath();
        Path realSoundsRoot = soundsRoot.toRealPath();
        Path realDirectory = candidate.toRealPath();
        if (!realSoundsRoot.startsWith(realConfigRoot) || !realDirectory.startsWith(realSoundsRoot)) {
            throw new IOException("Sound directory resolves outside config/modelprops/sounds");
        }

        List<Path> files;
        try (Stream<Path> stream = Files.list(realDirectory)) {
            files = stream.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ogg"))
                    .sorted(Comparator.comparing(Path::toString))
                    .limit(ModelPropsAssetProtocol.MAX_SOUNDS_PER_MODEL + 1L)
                    .toList();
        }
        if (files.size() > ModelPropsAssetProtocol.MAX_SOUNDS_PER_MODEL) {
            throw new IOException("Model has more than 32 named sounds");
        }
        LinkedHashMap<String, byte[]> result = new LinkedHashMap<>();
        Set<String> foldedKeys = new HashSet<>();
        long total = 0L;
        for (Path listed : files) {
            Path file = checkedRegularFile(soundsRoot, listed, "Sound");
            String filename = file.getFileName().toString();
            String soundKey = filename.substring(0, filename.length() - 4);
            if (!ModelPropsAssetProtocol.validSoundKey(soundKey)) {
                throw new IOException("Invalid named sound key '" + soundKey + "'");
            }
            if (!foldedKeys.add(soundKey.toLowerCase(Locale.ROOT))) {
                throw new IOException("Named sound keys must be unique ignoring case");
            }
            long size = Files.size(file);
            if (size < 1L || size > ModelPropsAssetProtocol.MAX_SOUND_BYTES
                    || total + size > ModelPropsAssetProtocol.MAX_SOUND_TOTAL_BYTES) {
                throw new IOException("Named sounds exceed the 2 MiB/file or 8 MiB/model limit");
            }
            byte[] bytes = Files.readAllBytes(file);
            OggVorbisValidator.validate(bytes);
            result.put(soundKey, bytes);
            total += bytes.length;
        }
        return Collections.unmodifiableMap(result);
    }

    private Path checkedRegularFile(Path expectedRoot, Path candidate, String label) throws IOException {
        if (Files.isSymbolicLink(candidate)
                || !Files.isRegularFile(candidate, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(label + " sidecar is not a regular file");
        }
        Path realConfigRoot = root.toRealPath();
        Path realExpectedRoot = expectedRoot.toRealPath();
        Path realFile = candidate.toRealPath();
        if (!realExpectedRoot.startsWith(realConfigRoot) || !realFile.startsWith(realExpectedRoot)) {
            throw new IOException(label + " sidecar resolves outside config/modelprops");
        }
        return realFile;
    }

    private static Path sidecarPath(Path base, String id, String suffix) throws IOException {
        int colon = id.indexOf(':');
        if (colon < 1 || colon == id.length() - 1) {
            throw new IOException("Invalid model id");
        }
        Path normalizedBase = base.toAbsolutePath().normalize();
        Path result = normalizedBase.resolve(id.substring(0, colon))
                .resolve(id.substring(colon + 1) + suffix).normalize();
        if (!result.startsWith(normalizedBase)) {
            throw new IOException("Sidecar path leaves config/modelprops");
        }
        return result;
    }

    private static Path soundSidecarPath(Path base, String id) throws IOException {
        int colon = id.indexOf(':');
        if (colon < 1 || colon == id.length() - 1) {
            throw new IOException("Invalid model id");
        }
        Path normalizedBase = base.toAbsolutePath().normalize();
        Path result = normalizedBase.resolve(id.substring(0, colon))
                .resolve(ModelPropsAssetProtocol.encodeSoundPath(id.substring(colon + 1))).normalize();
        if (!result.startsWith(normalizedBase)) {
            throw new IOException("Sound sidecar path leaves config/modelprops");
        }
        return result;
    }

    private Path findTexture(String reference) {
        String raw = reference;
        int colon = raw.indexOf(':');
        String namespace = colon >= 0 ? raw.substring(0, colon).toLowerCase(Locale.ROOT) : "";
        String path = colon >= 0 ? raw.substring(colon + 1) : raw;
        path = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (path.startsWith("textures/")) {
            path = path.substring("textures/".length());
        }
        if (path.endsWith(".png")) {
            path = path.substring(0, path.length() - 4);
        }
        if (!namespace.isEmpty() && !NAMESPACE.matcher(namespace).matches()) {
            return null;
        }
        if (path.isBlank() || path.contains("..") || !PATH.matcher(path).matches()) {
            return null;
        }

        List<Path> candidates = new ArrayList<>();
        if (!namespace.isEmpty()) {
            candidates.add(texturesRoot.resolve(namespace).resolve(path + ".png"));
        }
        candidates.add(texturesRoot.resolve(path + ".png"));
        Path normalizedRoot = texturesRoot.toAbsolutePath().normalize();
        for (Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (normalized.startsWith(normalizedRoot) && Files.isRegularFile(normalized)) {
                try {
                    Path realConfigRoot = root.toRealPath();
                    Path realRoot = texturesRoot.toRealPath();
                    if (!realRoot.startsWith(realConfigRoot)) {
                        ModelProps.LOGGER.warn("Texture directory symlink points outside config/modelprops");
                        return null;
                    }
                    Path realFile = normalized.toRealPath();
                    if (realFile.startsWith(realRoot)) {
                        return realFile;
                    }
                } catch (IOException exception) {
                    ModelProps.LOGGER.warn("Could not resolve texture path {}", normalized, exception);
                }
            }
        }
        return null;
    }

    private static String resolveAlias(String value, JsonObject aliases) {
        String current = value;
        Set<String> visited = new LinkedHashSet<>();
        while (current.startsWith("#")) {
            String key = current.substring(1);
            if (!visited.add(key) || !aliases.has(key) || !aliases.get(key).isJsonPrimitive()) {
                return current;
            }
            current = aliases.get(key).getAsString();
        }
        return current;
    }

    private static String idFromPath(Path relative) throws IOException {
        String path = relative.toString().replace('\\', '/');
        path = path.substring(0, path.length() - ".json".length()).toLowerCase(Locale.ROOT);
        int slash = path.indexOf('/');
        String namespace = slash >= 0 ? path.substring(0, slash) : ModelProps.MOD_ID;
        String value = slash >= 0 ? path.substring(slash + 1) : path;
        if (!NAMESPACE.matcher(namespace).matches() || value.isBlank() || value.contains("..") || !PATH.matcher(value).matches()) {
            throw new IOException("Invalid model filename; use lowercase letters, digits, _, -, . and folders");
        }
        return namespace + ":" + value;
    }

    private static String titleFromId(String id) {
        String path = id.substring(id.indexOf(':') + 1);
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        String[] words = name.replace('-', '_').split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.isEmpty() ? id : result.toString();
    }

    private void createExampleIfEmpty() throws IOException {
        boolean hasJson;
        try (Stream<Path> stream = Files.walk(modelsRoot)) {
            hasJson = stream.anyMatch(path -> Files.isRegularFile(path)
                    && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"));
        }
        if (hasJson) {
            return;
        }
        Path model = modelsRoot.resolve("modelprops/example_cube.json");
        Path texture = texturesRoot.resolve("modelprops/example/cube.png");
        Files.createDirectories(model.getParent());
        Files.createDirectories(texture.getParent());
        String example = """
                {
                  "display_name": "Example Cube",
                  "textures": { "0": "modelprops:example/cube" },
                  "elements": [
                    {
                      "from": [2, 2, 2],
                      "to": [14, 14, 14],
                      "faces": {
                        "down":  { "uv": [0, 0, 16, 16], "texture": "#0" },
                        "up":    { "uv": [0, 0, 16, 16], "texture": "#0" },
                        "north": { "uv": [0, 0, 16, 16], "texture": "#0" },
                        "south": { "uv": [0, 0, 16, 16], "texture": "#0" },
                        "west":  { "uv": [0, 0, 16, 16], "texture": "#0" },
                        "east":  { "uv": [0, 0, 16, 16], "texture": "#0" }
                      }
                    }
                  ]
                }
                """;
        Files.writeString(model, example, StandardCharsets.UTF_8);
        Files.write(texture, EXAMPLE_TEXTURE);
    }

    public synchronized Map<String, ServerModelEntry> entries() {
        return entries;
    }

    public synchronized ServerModelEntry get(String id) {
        return entries.get(id);
    }

    public synchronized boolean contains(String id) {
        return entries.containsKey(id);
    }

    public synchronized int generation() {
        return generation;
    }

    public ServerTransformStore transforms() {
        return transforms;
    }

    public Path root() {
        return root;
    }

    public record ReloadResult(int loaded, List<String> errors) {
    }
}
