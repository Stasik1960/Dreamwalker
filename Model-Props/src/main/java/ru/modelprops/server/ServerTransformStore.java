package ru.modelprops.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ru.modelprops.ModelProps;
import ru.modelprops.model.ModelTransform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ServerTransformStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private final Map<String, ModelTransform> transforms = new LinkedHashMap<>();
    private boolean loadFailed;

    public ServerTransformStore(Path root) {
        this.file = root.resolve("transforms.json");
    }

    public synchronized void load() {
        if (!Files.isRegularFile(file)) {
            transforms.clear();
            loadFailed = false;
            return;
        }
        Map<String, ModelTransform> loaded = new LinkedHashMap<>();
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject models = root.has("models") && root.get("models").isJsonObject()
                    ? root.getAsJsonObject("models") : new JsonObject();
            for (Map.Entry<String, JsonElement> entry : models.entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject value = entry.getValue().getAsJsonObject();
                ModelTransform transform = new ModelTransform(
                        number(value, "scale", 1.0F),
                        number(value, "offset_x", 0.0F),
                        number(value, "offset_y", 0.0F),
                        number(value, "offset_z", 0.0F),
                        number(value, "rotation_x", 0.0F),
                        number(value, "rotation_y", 0.0F),
                        number(value, "rotation_z", 0.0F),
                        Math.max(0, integer(value, "revision", 0))
                );
                if (transform.isValid()) {
                    loaded.put(entry.getKey(), transform);
                } else {
                    ModelProps.LOGGER.warn("Ignoring invalid transform for {}", entry.getKey());
                }
            }
            transforms.clear();
            transforms.putAll(loaded);
            loadFailed = false;
        } catch (Exception exception) {
            loadFailed = true;
            ModelProps.LOGGER.error("Could not read {}. The existing file will not be overwritten.", file, exception);
        }
    }

    public synchronized ModelTransform get(String id) {
        return transforms.getOrDefault(id, ModelTransform.IDENTITY);
    }

    public synchronized Map<String, ModelTransform> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(transforms));
    }

    public synchronized ModelTransform update(String id, ModelTransform value) throws IOException {
        if (loadFailed) {
            throw new IOException("transforms.json is invalid; fix or rename it, then run /modelprops reload");
        }
        int nextRevision = get(id).revision() + 1;
        ModelTransform saved = value.withRevision(nextRevision);
        if (!saved.isValid()) {
            throw new IllegalArgumentException("Transform is outside the supported range");
        }
        ModelTransform previous = transforms.put(id, saved);
        try {
            save();
        } catch (IOException exception) {
            if (previous == null) {
                transforms.remove(id);
            } else {
                transforms.put(id, previous);
            }
            throw exception;
        }
        return saved;
    }

    public synchronized ModelTransform reset(String id) throws IOException {
        return update(id, ModelTransform.IDENTITY);
    }

    private void save() throws IOException {
        Files.createDirectories(file.getParent());
        JsonObject root = new JsonObject();
        root.addProperty("schema", 1);
        JsonObject models = new JsonObject();
        for (Map.Entry<String, ModelTransform> entry : transforms.entrySet()) {
            ModelTransform transform = entry.getValue();
            JsonObject value = new JsonObject();
            value.addProperty("scale", transform.scale());
            value.addProperty("offset_x", transform.offsetX());
            value.addProperty("offset_y", transform.offsetY());
            value.addProperty("offset_z", transform.offsetZ());
            value.addProperty("rotation_x", transform.rotationX());
            value.addProperty("rotation_y", transform.rotationY());
            value.addProperty("rotation_z", transform.rotationZ());
            value.addProperty("revision", transform.revision());
            models.add(entry.getKey(), value);
        }
        root.add("models", models);

        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, GSON.toJson(root), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static float number(JsonObject object, String key, float fallback) {
        try {
            return object.has(key) ? object.get(key).getAsFloat() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int integer(JsonObject object, String key, int fallback) {
        try {
            return object.has(key) ? object.get(key).getAsInt() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
