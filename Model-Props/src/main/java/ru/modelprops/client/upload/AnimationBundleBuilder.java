package ru.modelprops.client.upload;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import ru.modelprops.animation.AnimationParser;
import ru.modelprops.animation.AnimationSet;
import ru.modelprops.net.ModelPropsAssetProtocol;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds the one canonical animation sidecar used by upload v2/v3 from several source files. */
public final class AnimationBundleBuilder {
    public static final int MAX_FILES = 32;

    private static final Gson GSON = new Gson();
    private static final List<String> ACTION_ORDER = List.of(
            "idle", "equip", "swing", "use", "attack", "custom"
    );

    private AnimationBundleBuilder() {
    }

    public static Bundle empty() {
        return new Bundle(new byte[0], List.of(), 0);
    }

    public static Bundle build(List<Source> sources) {
        if (sources == null || sources.isEmpty()) {
            return empty();
        }
        if (sources.size() > MAX_FILES) {
            throw new IllegalArgumentException("At most " + MAX_FILES + " animation JSON files can be selected");
        }

        JsonObject mergedClips = new JsonObject();
        LinkedHashMap<String, String> mergedActions = new LinkedHashMap<>();
        ArrayList<String> clipNames = new ArrayList<>();

        for (Source source : sources) {
            byte[] bytes = source.bytes();
            if (bytes.length < 1 || bytes.length > ModelPropsAssetProtocol.MAX_ANIMATION_BYTES) {
                throw new IllegalArgumentException(source.label()
                        + ": animation JSON must be between 1 byte and 512 KiB");
            }
            String json = decodeUtf8(bytes, source.label());
            AnimationSet parsed;
            JsonObject root;
            try {
                parsed = AnimationParser.parse(json);
                JsonElement element = JsonParser.parseString(json);
                if (!element.isJsonObject()) {
                    throw new IllegalArgumentException("Animation JSON root must be an object");
                }
                root = element.getAsJsonObject();
            } catch (JsonParseException | IllegalArgumentException exception) {
                throw new IllegalArgumentException(source.label() + ": " + exception.getMessage(), exception);
            }

            JsonObject clips = root.getAsJsonObject("clips");
            for (Map.Entry<String, JsonElement> entry : clips.entrySet()) {
                if (mergedClips.has(entry.getKey())) {
                    throw new IllegalArgumentException("Duplicate animation clip '" + entry.getKey()
                            + "' in " + source.label());
                }
                mergedClips.add(entry.getKey(), entry.getValue().deepCopy());
                clipNames.add(entry.getKey());
            }

            for (Map.Entry<String, String> entry : parsed.actions().entrySet()) {
                String previous = mergedActions.putIfAbsent(entry.getKey(), entry.getValue());
                if (previous != null && !previous.equals(entry.getValue())) {
                    throw new IllegalArgumentException("Conflicting animation action '" + entry.getKey()
                            + "': clips '" + previous + "' and '" + entry.getValue() + "'");
                }
            }
        }

        JsonObject merged = new JsonObject();
        merged.addProperty("format_version", AnimationParser.FORMAT_VERSION);
        JsonObject actions = new JsonObject();
        for (String action : ACTION_ORDER) {
            String clip = mergedActions.get(action);
            if (clip != null) {
                actions.addProperty(action, clip);
            }
        }
        if (!actions.isEmpty()) {
            merged.add("actions", actions);
        }
        merged.add("clips", mergedClips);

        byte[] canonical = GSON.toJson(merged).getBytes(StandardCharsets.UTF_8);
        if (canonical.length > ModelPropsAssetProtocol.MAX_ANIMATION_BYTES) {
            throw new IllegalArgumentException("Merged animation JSON is larger than 512 KiB");
        }
        try {
            AnimationParser.validate(new String(canonical, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid merged animation: " + exception.getMessage(), exception);
        }
        return new Bundle(canonical, List.copyOf(clipNames), sources.size());
    }

    private static String decodeUtf8(byte[] bytes, String label) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException(label + ": animation JSON is not valid UTF-8", exception);
        }
    }

    public record Source(String label, byte[] bytes) {
        public Source {
            label = label == null || label.isBlank() ? "Animation JSON" : label;
            bytes = bytes == null ? new byte[0] : bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }

    public record Bundle(byte[] jsonBytes, List<String> clipNames, int fileCount) {
        public Bundle {
            jsonBytes = jsonBytes == null ? new byte[0] : jsonBytes.clone();
            clipNames = List.copyOf(clipNames);
        }

        @Override
        public byte[] jsonBytes() {
            return jsonBytes.clone();
        }

        public int clipCount() {
            return clipNames.size();
        }

        public int byteLength() {
            return jsonBytes.length;
        }
    }
}
