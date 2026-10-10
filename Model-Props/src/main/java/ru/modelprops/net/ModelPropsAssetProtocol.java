package ru.modelprops.net;

import net.minecraft.util.Identifier;
import ru.modelprops.ModelProps;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Packet identifiers and hard limits shared by the optional animation/sound
 * extension.  Upload v2 deliberately uses new channels: a 1.1 client can keep
 * using the legacy two-file upload without ever decoding the extended header.
 */
public final class ModelPropsAssetProtocol {
    public static final int MAX_ANIMATION_BYTES = 512 * 1024;
    public static final int MAX_SOUND_BYTES = 2 * 1024 * 1024;
    public static final int MAX_SOUNDS_PER_MODEL = 32;
    public static final int MAX_LEGACY_SOUNDS_PER_MODEL = 8;
    public static final int MAX_SOUND_KEY_LENGTH = 64;
    public static final int MAX_SOUND_TOTAL_BYTES = 8 * 1024 * 1024;

    private static final Pattern SOUND_KEY_PATTERN = Pattern.compile("[A-Za-z0-9_.-]{1," + MAX_SOUND_KEY_LENGTH + "}");
    private static final Set<String> WINDOWS_DEVICE_NAMES = Set.of(
            "con", "prn", "aux", "nul",
            "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
            "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9"
    );

    public static final byte PART_MODEL_JSON = 0;
    public static final byte PART_TEXTURE_PNG = 1;
    public static final byte PART_ANIMATION_JSON = 2;
    public static final byte PART_SOUND_OGG = 3;

    public static final Set<String> ACTIONS = Set.of(
            "equip", "swing", "use", "attack", "custom"
    );

    public static final Identifier UPLOAD_BEGIN_V2 = ModelProps.id("upload_v2_begin");
    public static final Identifier UPLOAD_CHUNK_V2 = ModelProps.id("upload_v2_chunk");
    public static final Identifier UPLOAD_COMMIT_V2 = ModelProps.id("upload_v2_commit");
    public static final Identifier UPLOAD_BEGIN_V3 = ModelProps.id("upload_v3_begin");
    public static final Identifier UPLOAD_CHUNK_V3 = ModelProps.id("upload_v3_chunk");
    public static final Identifier UPLOAD_COMMIT_V3 = ModelProps.id("upload_v3_commit");

    public static final Identifier ANIMATION_BEGIN = ModelProps.id("animation_begin");
    public static final Identifier ANIMATION_CHUNK = ModelProps.id("animation_chunk");
    public static final Identifier ANIMATION_END = ModelProps.id("animation_end");
    public static final Identifier SOUND_BEGIN = ModelProps.id("sound_begin");
    public static final Identifier SOUND_CHUNK = ModelProps.id("sound_chunk");
    public static final Identifier SOUND_END = ModelProps.id("sound_end");
    public static final Identifier SOUND_V2_BEGIN = ModelProps.id("sound_v2_begin");
    public static final Identifier SOUND_V2_CHUNK = ModelProps.id("sound_v2_chunk");
    public static final Identifier SOUND_V2_END = ModelProps.id("sound_v2_end");

    private ModelPropsAssetProtocol() {
    }

    public static boolean validAction(String action) {
        return action != null && ACTIONS.contains(action);
    }

    /**
     * A sound key is also used as an on-disk file name. Keep it portable and
     * path-free on every supported server platform.
     */
    public static boolean validSoundKey(String key) {
        if (key == null || !SOUND_KEY_PATTERN.matcher(key).matches()
                || ".".equals(key) || "..".equals(key) || key.endsWith(".")) {
            return false;
        }

        String lower = key.toLowerCase(Locale.ROOT);
        int extension = lower.indexOf('.');
        String baseName = extension >= 0 ? lower.substring(0, extension) : lower;
        return !WINDOWS_DEVICE_NAMES.contains(baseName);
    }

    /**
     * Encodes a slash-containing model path into one collision-free directory
     * segment. Percent is not legal in Minecraft identifiers, so foo/bar and
     * foo can never share an upload target or become parent/child directories.
     */
    public static String encodeSoundPath(String modelPath) {
        return modelPath.replace("%", "%25").replace("/", "%2F");
    }
}
