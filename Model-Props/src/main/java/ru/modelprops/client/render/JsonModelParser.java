package ru.modelprops.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.render.model.json.ModelTransformationMode;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class JsonModelParser {
    private static final int MAX_ELEMENTS = 1024;
    private static final Set<String> DIRECTIONS = Set.of("down", "up", "north", "south", "west", "east");
    private static final Pattern ELEMENT_ID = Pattern.compile("[A-Za-z0-9_.:/-]{1,64}");

    private JsonModelParser() {
    }

    public static RenderModel parse(String id, String displayName, String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        Map<String, String> aliases = readAliases(root);
        List<RenderModel.Element> elements = new ArrayList<>();
        Set<String> elementIds = new LinkedHashSet<>();
        elementIds.add("root");
        if (root.has("elements") && root.get("elements").isJsonArray()) {
            JsonArray array = root.getAsJsonArray("elements");
            if (array.size() > MAX_ELEMENTS) {
                throw new IllegalArgumentException("Model has more than " + MAX_ELEMENTS + " elements");
            }
            for (int index = 0; index < array.size(); index++) {
                JsonElement element = array.get(index);
                if (element.isJsonObject()) {
                    elements.add(readElement(element.getAsJsonObject(), aliases, index, elementIds));
                }
            }
        }

        String flatTexture = null;
        if (elements.isEmpty()) {
            flatTexture = resolveTexture("#layer0", aliases);
            if (flatTexture.startsWith("#")) {
                flatTexture = resolveTexture("#0", aliases);
            }
            if (flatTexture.startsWith("#")) {
                flatTexture = "minecraft:item/barrier";
            }
        }
        return new RenderModel(id, displayName, List.copyOf(elements), flatTexture, readDisplay(root));
    }

    private static RenderModel.Element readElement(JsonObject object, Map<String, String> aliases,
                                                   int index, Set<String> usedIds) {
        String id = string(object, "modelprops_id", "").trim();
        if (!claimElementId(id, usedIds)) {
            id = string(object, "name", "").trim();
            if (!claimElementId(id, usedIds)) {
                id = "element_" + index;
                int suffix = 2;
                while (!usedIds.add(id)) {
                    id = "element_" + index + "_" + suffix++;
                }
            }
        }
        float[] from = vector(object.get("from"), new float[]{0.0F, 0.0F, 0.0F});
        float[] to = vector(object.get("to"), new float[]{16.0F, 16.0F, 16.0F});
        RenderModel.ElementRotation rotation = RenderModel.ElementRotation.NONE;
        if (object.has("rotation") && object.get("rotation").isJsonObject()) {
            JsonObject value = object.getAsJsonObject("rotation");
            String axis = string(value, "axis", "y").toLowerCase(Locale.ROOT);
            if (!axis.equals("x") && !axis.equals("y") && !axis.equals("z")) {
                axis = "y";
            }
            rotation = new RenderModel.ElementRotation(
                    vector(value.get("origin"), new float[]{8.0F, 8.0F, 8.0F}),
                    axis,
                    number(value, "angle", 0.0F)
            );
        }

        Map<String, RenderModel.Face> faces = new LinkedHashMap<>();
        if (object.has("faces") && object.get("faces").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : object.getAsJsonObject("faces").entrySet()) {
                String direction = entry.getKey().toLowerCase(Locale.ROOT);
                if (!DIRECTIONS.contains(direction) || !entry.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject face = entry.getValue().getAsJsonObject();
                float[] uv = vector4(face.get("uv"), defaultUv(direction, from, to));
                String texture = resolveTexture(string(face, "texture", "#0"), aliases);
                int faceRotation = integer(face, "rotation", 0);
                if (faceRotation % 90 != 0) {
                    faceRotation = 0;
                }
                faceRotation = Math.floorMod(faceRotation, 360);
                faces.put(direction, new RenderModel.Face(uv, texture, faceRotation));
            }
        }
        return new RenderModel.Element(id, from, to, rotation, Map.copyOf(faces));
    }

    private static boolean claimElementId(String id, Set<String> usedIds) {
        return ELEMENT_ID.matcher(id).matches() && usedIds.add(id);
    }

    private static Map<String, String> readAliases(JsonObject root) {
        Map<String, String> aliases = new LinkedHashMap<>();
        if (root.has("textures") && root.get("textures").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("textures").entrySet()) {
                if (entry.getValue().isJsonPrimitive()) {
                    aliases.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
        }
        return aliases;
    }

    private static String resolveTexture(String value, Map<String, String> aliases) {
        String current = value;
        Set<String> visited = new LinkedHashSet<>();
        while (current.startsWith("#")) {
            String key = current.substring(1);
            if (!visited.add(key)) {
                return current;
            }
            String next = aliases.get(key);
            if (next == null) {
                return current;
            }
            current = next;
        }
        return current;
    }

    private static Map<ModelTransformationMode, RenderModel.DisplayTransform> readDisplay(JsonObject root) {
        EnumMap<ModelTransformationMode, RenderModel.DisplayTransform> result = new EnumMap<>(ModelTransformationMode.class);
        if (!root.has("display") || !root.get("display").isJsonObject()) {
            return Map.of();
        }
        JsonObject display = root.getAsJsonObject("display");
        addDisplay(result, display, "firstperson_righthand", ModelTransformationMode.FIRST_PERSON_RIGHT_HAND);
        addDisplay(result, display, "firstperson_lefthand", ModelTransformationMode.FIRST_PERSON_LEFT_HAND);
        addDisplay(result, display, "thirdperson_righthand", ModelTransformationMode.THIRD_PERSON_RIGHT_HAND);
        addDisplay(result, display, "thirdperson_lefthand", ModelTransformationMode.THIRD_PERSON_LEFT_HAND);
        addDisplay(result, display, "gui", ModelTransformationMode.GUI);
        addDisplay(result, display, "ground", ModelTransformationMode.GROUND);
        addDisplay(result, display, "fixed", ModelTransformationMode.FIXED);
        addDisplay(result, display, "head", ModelTransformationMode.HEAD);
        return Map.copyOf(result);
    }

    private static void addDisplay(EnumMap<ModelTransformationMode, RenderModel.DisplayTransform> target,
                                   JsonObject display, String key, ModelTransformationMode mode) {
        if (!display.has(key) || !display.get(key).isJsonObject()) {
            return;
        }
        JsonObject value = display.getAsJsonObject(key);
        target.put(mode, new RenderModel.DisplayTransform(
                vector(value.get("translation"), new float[]{0.0F, 0.0F, 0.0F}),
                vector(value.get("rotation"), new float[]{0.0F, 0.0F, 0.0F}),
                vector(value.get("scale"), new float[]{1.0F, 1.0F, 1.0F})
        ));
    }

    private static float[] defaultUv(String direction, float[] from, float[] to) {
        return switch (direction) {
            case "down", "up" -> new float[]{from[0], from[2], to[0], to[2]};
            case "north", "south" -> new float[]{from[0], 16.0F - to[1], to[0], 16.0F - from[1]};
            case "west", "east" -> new float[]{from[2], 16.0F - to[1], to[2], 16.0F - from[1]};
            default -> new float[]{0.0F, 0.0F, 16.0F, 16.0F};
        };
    }

    private static float[] vector(JsonElement element, float[] fallback) {
        if (element == null || !element.isJsonArray() || element.getAsJsonArray().size() != 3) {
            return fallback.clone();
        }
        float[] result = new float[3];
        try {
            for (int i = 0; i < 3; i++) {
                result[i] = finiteOrFallback(element.getAsJsonArray().get(i).getAsFloat(), fallback[i]);
            }
            return result;
        } catch (RuntimeException ignored) {
            return fallback.clone();
        }
    }

    private static float[] vector4(JsonElement element, float[] fallback) {
        if (element == null || !element.isJsonArray() || element.getAsJsonArray().size() != 4) {
            return fallback.clone();
        }
        float[] result = new float[4];
        try {
            for (int i = 0; i < 4; i++) {
                result[i] = finiteOrFallback(element.getAsJsonArray().get(i).getAsFloat(), fallback[i]);
            }
            return result;
        } catch (RuntimeException ignored) {
            return fallback.clone();
        }
    }

    private static String string(JsonObject object, String key, String fallback) {
        try {
            return object.has(key) ? object.get(key).getAsString() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static float number(JsonObject object, String key, float fallback) {
        try {
            return object.has(key) ? finiteOrFallback(object.get(key).getAsFloat(), fallback) : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static float finiteOrFallback(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }

    private static int integer(JsonObject object, String key, int fallback) {
        try {
            return object.has(key) ? object.get(key).getAsInt() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
