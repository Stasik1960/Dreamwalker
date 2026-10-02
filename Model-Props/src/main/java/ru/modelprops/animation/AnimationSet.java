package ru.modelprops.animation;

import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

public final class AnimationSet {
    public static final Set<String> SUPPORTED_ACTIONS = Set.of(
            "idle", "equip", "swing", "use", "attack", "custom"
    );
    public static final AnimationSet EMPTY = new AnimationSet(Map.of(), Map.of());

    private final Map<String, AnimationClip> clips;
    private final Map<String, String> actions;

    public AnimationSet(Map<String, AnimationClip> clips, Map<String, String> actions) {
        this.clips = Map.copyOf(clips);
        this.actions = Map.copyOf(actions);
    }

    public Map<String, AnimationClip> clips() {
        return clips;
    }

    public Map<String, String> actions() {
        return actions;
    }

    public boolean isEmpty() {
        return clips.isEmpty();
    }

    public boolean hasClip(String clipName) {
        return clips.containsKey(clipName);
    }

    public boolean hasAction(String action) {
        return actions.containsKey(action);
    }

    /** Returns the canonical clip name bound to a built-in action alias. */
    public Optional<String> clipNameForAction(String action) {
        return Optional.ofNullable(actions.get(action));
    }

    public Optional<AnimationClip> clip(String clipName) {
        return Optional.ofNullable(clips.get(clipName));
    }

    /** Resolves an action alias through the {@code actions} map. */
    public Optional<AnimationClip> resolveAction(String action) {
        return clipNameForAction(action).flatMap(this::clip);
    }

    /** Resolves only a direct, canonical clip name; action aliases are not considered. */
    public Optional<AnimationClip> resolveDirectClip(String clipName) {
        return clip(clipName);
    }

    /** Returns every action alias currently mapped to the canonical clip name. */
    public Set<String> actionsForClip(String clipName) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        actions.forEach((action, target) -> {
            if (target.equals(clipName)) {
                result.add(action);
            }
        });
        return Set.copyOf(result);
    }

    public Optional<AnimationClip> clipForAction(String action) {
        return resolveAction(action);
    }

    public AnimationPose sampleClip(String clipName, long elapsedMillis) {
        return AnimationSampler.sample(clips.get(clipName), elapsedMillis);
    }

    public AnimationPose sampleAction(String action, long elapsedMillis) {
        return AnimationSampler.sample(clipForAction(action).orElse(null), elapsedMillis);
    }
}
