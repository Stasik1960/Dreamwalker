package ru.modelprops.animation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Parser and strict validator for the Model Props animation format version 1. */
public final class AnimationParser {
    public static final int FORMAT_VERSION = 1;
    public static final int MAX_JSON_CHARACTERS = 512 * 1024;
    public static final int MAX_CLIPS = 32;
    public static final int MAX_TRACKS = 2_048;
    public static final int MAX_KEYFRAMES = 8_192;
    public static final int MAX_DURATION_MILLIS = 60_000;

    private static final int MAX_KEYFRAMES_PER_TRACK = 512;
    private static final Pattern CLIP_NAME = Pattern.compile("[A-Za-z0-9_.-]{1,64}");
    private static final Pattern TARGET_NAME = Pattern.compile("[A-Za-z0-9_.:/-]{1,64}");

    private AnimationParser() {
    }

    /** Parses an animation without checking model-specific target names. Safe to call on a server. */
    public static AnimationSet parse(String json) {
        return parse(json, null);
    }

    /** Fully parses and validates an animation, rejecting targets absent from the supplied model. */
    public static AnimationSet parse(String json, Set<String> allowedTargets) {
        if (json == null || json.isBlank()) {
            return AnimationSet.EMPTY;
        }
        if (json.length() > MAX_JSON_CHARACTERS) {
            throw new IllegalArgumentException("Animation JSON is larger than 512 KiB");
        }

        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("Animation JSON root must be an object");
            }
            root = parsed.getAsJsonObject();
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("Malformed animation JSON", exception);
        }

        int version = requiredInteger(root, "format_version");
        if (version != FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported animation format_version " + version
                    + " (expected " + FORMAT_VERSION + ")");
        }
        JsonObject clipObjects = requiredObject(root, "clips");
        if (clipObjects.size() > MAX_CLIPS) {
            throw new IllegalArgumentException("Animation has more than " + MAX_CLIPS + " clips");
        }

        Map<String, AnimationClip> clips = new LinkedHashMap<>();
        Counter counter = new Counter();
        for (Map.Entry<String, JsonElement> entry : clipObjects.entrySet()) {
            String name = entry.getKey();
            if (!CLIP_NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("Invalid clip name '" + name + "'");
            }
            if (!entry.getValue().isJsonObject()) {
                throw new IllegalArgumentException("Clip '" + name + "' must be an object");
            }
            clips.put(name, readClip(name, entry.getValue().getAsJsonObject(), allowedTargets, counter));
        }

        Map<String, String> actions = new LinkedHashMap<>();
        for (String action : AnimationSet.SUPPORTED_ACTIONS) {
            if (clips.containsKey(action)) {
                actions.put(action, action);
            }
        }
        if (root.has("actions")) {
            JsonObject actionObjects = requiredObject(root, "actions");
            for (Map.Entry<String, JsonElement> entry : actionObjects.entrySet()) {
                String action = entry.getKey();
                if (!AnimationSet.SUPPORTED_ACTIONS.contains(action)) {
                    throw new IllegalArgumentException("Unsupported action '" + action + "'");
                }
                String clipName = actionClip(entry.getValue(), action);
                if (!clips.containsKey(clipName)) {
                    throw new IllegalArgumentException("Action '" + action
                            + "' references missing clip '" + clipName + "'");
                }
                actions.put(action, clipName);
            }
        }
        return new AnimationSet(clips, actions);
    }

    /** Structural validation helper for upload paths that do not need the parsed result. */
    public static void validate(String json) {
        parse(json);
    }

    private static AnimationClip readClip(String name, JsonObject object, Set<String> allowedTargets,
                                          Counter counter) {
        int duration = requiredInteger(object, "duration_ms");
        if (duration < 1 || duration > MAX_DURATION_MILLIS) {
            throw new IllegalArgumentException("Clip '" + name + "' duration_ms must be between 1 and "
                    + MAX_DURATION_MILLIS);
        }
        boolean loop = optionalBoolean(object, "loop", false);
        AnimationClip.Interpolation clipInterpolation = interpolation(object, "interpolation",
                AnimationClip.Interpolation.LINEAR);
        JsonObject tracksObject = requiredObject(object, "tracks");
        Map<String, List<AnimationClip.Keyframe>> tracks = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : tracksObject.entrySet()) {
            String target = entry.getKey();
            if (!TARGET_NAME.matcher(target).matches()) {
                throw new IllegalArgumentException("Invalid animation target '" + target + "'");
            }
            if (allowedTargets != null && !allowedTargets.contains(target)) {
                throw new IllegalArgumentException("Animation target '" + target + "' is not present in the model");
            }
            counter.tracks++;
            if (counter.tracks > MAX_TRACKS) {
                throw new IllegalArgumentException("Animation has more than " + MAX_TRACKS + " tracks");
            }
            tracks.put(target, readTrack(name, target, entry.getValue(), duration, clipInterpolation, counter));
        }
        return new AnimationClip(name, duration, loop, tracks);
    }

    private static List<AnimationClip.Keyframe> readTrack(String clipName, String target, JsonElement element,
                                                           int duration,
                                                           AnimationClip.Interpolation clipInterpolation,
                                                           Counter counter) {
        JsonArray frames;
        AnimationClip.Interpolation trackInterpolation = clipInterpolation;
        if (element.isJsonArray()) {
            frames = element.getAsJsonArray();
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            frames = requiredArray(object, "keyframes");
            trackInterpolation = interpolation(object, "interpolation", clipInterpolation);
        } else {
            throw new IllegalArgumentException("Track '" + target + "' in clip '" + clipName
                    + "' must be an array or object");
        }
        if (frames.size() < 1 || frames.size() > MAX_KEYFRAMES_PER_TRACK) {
            throw new IllegalArgumentException("Track '" + target + "' in clip '" + clipName
                    + "' must contain 1.." + MAX_KEYFRAMES_PER_TRACK + " keyframes");
        }
        counter.keyframes += frames.size();
        if (counter.keyframes > MAX_KEYFRAMES) {
            throw new IllegalArgumentException("Animation has more than " + MAX_KEYFRAMES + " keyframes");
        }

        List<AnimationClip.Keyframe> result = new ArrayList<>(frames.size());
        for (JsonElement frameElement : frames) {
            if (!frameElement.isJsonObject()) {
                throw new IllegalArgumentException("Every keyframe must be an object");
            }
            JsonObject frame = frameElement.getAsJsonObject();
            int time = requiredInteger(frame, "time_ms");
            if (time < 0 || time > duration) {
                throw new IllegalArgumentException("Keyframe time_ms " + time + " is outside clip '"
                        + clipName + "' duration");
            }
            AnimationVector position = vector(frame, "position", "pos", AnimationVector.ZERO,
                    -256.0F, 256.0F);
            AnimationVector rotation = vector(frame, "rotation", null, AnimationVector.ZERO,
                    -3_600.0F, 3_600.0F);
            AnimationVector scale = vector(frame, "scale", null, AnimationVector.ONE,
                    0.01F, 16.0F);
            AnimationClip.Interpolation frameInterpolation = interpolation(frame, "interpolation",
                    trackInterpolation);
            result.add(new AnimationClip.Keyframe(time,
                    new AnimationTransform(position, rotation, scale), frameInterpolation));
        }
        result.sort(Comparator.comparingInt(AnimationClip.Keyframe::timeMillis));
        for (int index = 1; index < result.size(); index++) {
            if (result.get(index - 1).timeMillis() == result.get(index).timeMillis()) {
                throw new IllegalArgumentException("Track '" + target + "' in clip '" + clipName
                        + "' contains duplicate time_ms " + result.get(index).timeMillis());
            }
        }
        return List.copyOf(result);
    }

    private static String actionClip(JsonElement element, String action) {
        try {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                return element.getAsString();
            }
            if (element.isJsonObject()) {
                return requiredString(element.getAsJsonObject(), "clip");
            }
        } catch (RuntimeException ignored) {
            // Replaced by the more useful validation message below.
        }
        throw new IllegalArgumentException("Action '" + action + "' must be a clip name or {\"clip\": ...}");
    }

    private static AnimationVector vector(JsonObject object, String key, String alias,
                                          AnimationVector fallback, float minimum, float maximum) {
        JsonElement element = object.get(key);
        if ((element == null || element.isJsonNull()) && alias != null) {
            element = object.get(alias);
        }
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        if (!element.isJsonArray() || element.getAsJsonArray().size() != 3) {
            throw new IllegalArgumentException("'" + key + "' must be an array of three numbers");
        }
        float[] values = new float[3];
        for (int index = 0; index < 3; index++) {
            try {
                values[index] = element.getAsJsonArray().get(index).getAsFloat();
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("'" + key + "' must contain numbers", exception);
            }
            if (!Float.isFinite(values[index]) || values[index] < minimum || values[index] > maximum) {
                throw new IllegalArgumentException("'" + key + "' values must be between "
                        + minimum + " and " + maximum);
            }
        }
        return new AnimationVector(values[0], values[1], values[2]);
    }

    private static AnimationClip.Interpolation interpolation(JsonObject object, String key,
                                                              AnimationClip.Interpolation fallback) {
        if (!object.has(key)) {
            return fallback;
        }
        return AnimationClip.Interpolation.parse(requiredString(object, key));
    }

    private static JsonObject requiredObject(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException("'" + key + "' must be an object");
        }
        return element.getAsJsonObject();
    }

    private static JsonArray requiredArray(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonArray()) {
            throw new IllegalArgumentException("'" + key + "' must be an array");
        }
        return element.getAsJsonArray();
    }

    private static int requiredInteger(JsonObject object, String key) {
        JsonElement element = object.get(key);
        try {
            if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("'" + key + "' must be an integer");
            }
            double numeric = element.getAsDouble();
            int value = element.getAsInt();
            if (!Double.isFinite(numeric) || numeric != value) {
                throw new IllegalArgumentException("'" + key + "' must be an integer");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("'" + key + "' must be an integer", exception);
        }
    }

    private static String requiredString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("'" + key + "' must be a string");
        }
        return element.getAsString();
    }

    private static boolean optionalBoolean(JsonObject object, String key, boolean fallback) {
        JsonElement element = object.get(key);
        if (element == null) {
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("'" + key + "' must be true or false");
        }
        return element.getAsBoolean();
    }

    private static final class Counter {
        private int tracks;
        private int keyframes;
    }
}
