package ru.modelprops.animation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AnimationSampler {
    private AnimationSampler() {
    }

    public static AnimationPose sample(AnimationClip clip, long elapsedMillis) {
        if (clip == null || clip.tracks().isEmpty()) {
            return AnimationPose.EMPTY;
        }
        long time = clip.loop()
                ? Math.floorMod(elapsedMillis, clip.durationMillis())
                : Math.max(0L, Math.min(elapsedMillis, clip.durationMillis()));
        Map<String, AnimationTransform> result = new LinkedHashMap<>();
        clip.tracks().forEach((target, frames) -> result.put(target, sampleTrack(frames, time)));
        return new AnimationPose(result);
    }

    private static AnimationTransform sampleTrack(List<AnimationClip.Keyframe> frames, long time) {
        AnimationClip.Keyframe first = frames.get(0);
        if (frames.size() == 1 || time <= first.timeMillis()) {
            return first.transform();
        }
        for (int index = 1; index < frames.size(); index++) {
            AnimationClip.Keyframe right = frames.get(index);
            if (time < right.timeMillis()) {
                AnimationClip.Keyframe left = frames.get(index - 1);
                float span = right.timeMillis() - left.timeMillis();
                float amount = span <= 0.0F ? 1.0F : (time - left.timeMillis()) / span;
                return AnimationTransform.interpolate(
                        left.transform(), right.transform(), left.interpolation().apply(amount)
                );
            }
        }
        return frames.get(frames.size() - 1).transform();
    }
}
