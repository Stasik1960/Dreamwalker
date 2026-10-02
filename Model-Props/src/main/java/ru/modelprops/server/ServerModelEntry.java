package ru.modelprops.server;

import ru.modelprops.animation.AnimationParser;
import ru.modelprops.animation.AnimationSet;
import ru.modelprops.animation.ModelAnimationTargets;
import ru.modelprops.net.ModelPropsAssetProtocol;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ServerModelEntry {
    private static final List<String> LEGACY_SOUND_ACTIONS = List.of(
            "custom", "equip", "swing", "use", "attack", "idle"
    );
    private final String id;
    private final String displayName;
    private final String json;
    private final Map<String, byte[]> textures;
    private final String animationJson;
    private final AnimationSet animation;
    private final Map<String, byte[]> sounds;
    private final Set<String> actions;

    public ServerModelEntry(String id, String displayName, String json, Map<String, byte[]> textures) {
        this(id, displayName, json, textures, "", Map.of());
    }

    public ServerModelEntry(String id, String displayName, String json, Map<String, byte[]> textures,
                            String animationJson, Map<String, byte[]> sounds) {
        this.id = id;
        this.displayName = displayName;
        this.json = json;
        this.textures = immutableBytes(textures);
        this.animationJson = animationJson == null ? "" : animationJson;
        this.animation = AnimationParser.parse(this.animationJson, ModelAnimationTargets.fromModelJson(this.json));
        this.sounds = immutableBytes(sounds);

        LinkedHashSet<String> foldedSoundKeys = new LinkedHashSet<>();
        for (String soundKey : this.sounds.keySet()) {
            if (!ModelPropsAssetProtocol.validSoundKey(soundKey)) {
                throw new IllegalArgumentException("Invalid named sound key '" + soundKey + "'");
            }
            if (!foldedSoundKeys.add(soundKey.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Named sound keys must be unique ignoring case");
            }
            if (!ModelPropsAssetProtocol.validAction(soundKey) && !this.animation.hasClip(soundKey)) {
                throw new IllegalArgumentException(
                        "Named sound '" + soundKey + "' does not match an animation clip");
            }
        }

        LinkedHashSet<String> available = new LinkedHashSet<>(this.animation.actions().keySet());
        this.sounds.keySet().stream()
                .filter(ModelPropsAssetProtocol::validAction)
                .forEach(available::add);
        this.actions = Collections.unmodifiableSet(available);
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String json() {
        return json;
    }

    public Map<String, byte[]> textures() {
        return textures;
    }

    /** Empty when this model has no animation sidecar. */
    public String animationJson() {
        return animationJson;
    }

    /** Parsed once when the catalog entry is constructed. */
    public AnimationSet animation() {
        return animation;
    }

    public boolean hasClip(String clipName) {
        return animation.hasClip(clipName);
    }

    /** OGG data keyed by a built-in action or canonical animation clip name. */
    public Map<String, byte[]> sounds() {
        return sounds;
    }

    public Set<String> actions() {
        return actions;
    }

    public boolean hasAction(String action) {
        return actions.contains(action);
    }

    /** Preserve legacy action sounds first, then fall back to the mapped clip sound. */
    public Optional<String> soundKeyForAction(String action) {
        if (sounds.containsKey(action)) {
            return Optional.of(action);
        }
        Optional<String> clipName = animation.clipNameForAction(action);
        if (clipName.isPresent() && sounds.containsKey(clipName.get())) {
            return clipName;
        }
        return Optional.empty();
    }

    public Optional<byte[]> soundForAction(String action) {
        return soundKeyForAction(action).map(key -> sounds.get(key).clone());
    }

    public Optional<byte[]> soundForClip(String clipName) {
        byte[] sound = sounds.get(clipName);
        if (sound != null) {
            return Optional.of(sound.clone());
        }
        for (String action : LEGACY_SOUND_ACTIONS) {
            if (animation.clipNameForAction(action).filter(clipName::equals).isPresent()) {
                byte[] legacy = sounds.get(action);
                if (legacy != null) {
                    return Optional.of(legacy.clone());
                }
            }
        }
        return Optional.empty();
    }

    private static Map<String, byte[]> immutableBytes(Map<String, byte[]> source) {
        LinkedHashMap<String, byte[]> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, value.clone()));
        return Collections.unmodifiableMap(copy);
    }
}
