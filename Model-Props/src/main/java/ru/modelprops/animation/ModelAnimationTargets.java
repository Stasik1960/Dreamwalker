package ru.modelprops.animation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Derives the same stable element IDs used by the renderer without loading client classes. */
public final class ModelAnimationTargets {
    private static final Pattern ELEMENT_ID = Pattern.compile("[A-Za-z0-9_.:/-]{1,64}");

    private ModelAnimationTargets() {
    }

    public static Set<String> fromModelJson(String modelJson) {
        JsonObject root = JsonParser.parseString(modelJson).getAsJsonObject();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        result.add("root");
        if (!root.has("elements") || !root.get("elements").isJsonArray()) {
            return Set.copyOf(result);
        }
        JsonArray elements = root.getAsJsonArray("elements");
        for (int index = 0; index < elements.size(); index++) {
            JsonElement value = elements.get(index);
            if (!value.isJsonObject()) {
                continue;
            }
            JsonObject element = value.getAsJsonObject();
            String id = string(element, "modelprops_id").trim();
            if (!claimElementId(id, result)) {
                id = string(element, "name").trim();
                if (!claimElementId(id, result)) {
                    id = "element_" + index;
                    int suffix = 2;
                    while (!result.add(id)) {
                        id = "element_" + index + "_" + suffix++;
                    }
                }
            }
        }
        return Set.copyOf(result);
    }

    private static boolean claimElementId(String id, Set<String> usedIds) {
        return ELEMENT_ID.matcher(id).matches() && usedIds.add(id);
    }

    private static String string(JsonObject object, String key) {
        try {
            return object.has(key) ? object.get(key).getAsString() : "";
        } catch (RuntimeException ignored) {
            return "";
        }
    }
}
