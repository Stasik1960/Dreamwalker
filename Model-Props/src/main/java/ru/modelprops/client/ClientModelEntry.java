package ru.modelprops.client;

import ru.modelprops.animation.AnimationSet;
import ru.modelprops.client.render.RenderModel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ClientModelEntry(
        String id,
        String displayName,
        RenderModel model,
        AnimationSet animation,
        Map<String, byte[]> sounds
) {
    private static final List<String> LEGACY_SOUND_ACTIONS = List.of(
            "custom", "equip", "swing", "use", "attack", "idle"
    );

    public ClientModelEntry {
        animation = animation == null ? AnimationSet.EMPTY : animation;
        LinkedHashMap<String, byte[]> copiedSounds = new LinkedHashMap<>();
        if (sounds != null) {
            sounds.forEach((action, bytes) -> copiedSounds.put(action, bytes.clone()));
        }
        sounds = Collections.unmodifiableMap(copiedSounds);
    }

    public byte[] sound(String action) {
        byte[] bytes = sounds.get(action);
        return bytes == null ? null : bytes.clone();
    }

    /** Action compatibility: an old action-keyed sound wins, then its mapped clip sound. */
    public byte[] soundBytesForAction(String action) {
        byte[] exact = sounds.get(action);
        if (exact != null) {
            return exact;
        }
        String clipName = animation.clipNameForAction(action).orElse(null);
        return clipName == null ? null : sounds.get(clipName);
    }

    /** Direct clip playback: a clip-keyed sound wins, then a legacy action alias sound. */
    public byte[] soundBytesForClip(String clipName) {
        byte[] exact = sounds.get(clipName);
        if (exact != null) {
            return exact;
        }
        for (String action : LEGACY_SOUND_ACTIONS) {
            if (animation.clipNameForAction(action).filter(clipName::equals).isPresent()) {
                byte[] legacy = sounds.get(action);
                if (legacy != null) {
                    return legacy;
                }
            }
        }
        return null;
    }
}
