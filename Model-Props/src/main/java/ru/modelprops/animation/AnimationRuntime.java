package ru.modelprops.animation;

import net.minecraft.item.ItemStack;

/**
 * Bridge between the renderer and the networking/action controller. Installing a provider is optional;
 * static models continue to render with an empty pose.
 */
public final class AnimationRuntime {
    private static final PoseProvider EMPTY_PROVIDER = (stack, modelId, nowNanos) -> AnimationPose.EMPTY;
    private static volatile PoseProvider provider = EMPTY_PROVIDER;

    private AnimationRuntime() {
    }

    public static void setProvider(PoseProvider newProvider) {
        provider = newProvider == null ? EMPTY_PROVIDER : newProvider;
    }

    public static void clearProvider() {
        provider = EMPTY_PROVIDER;
    }

    public static AnimationPose pose(ItemStack stack, String modelId, long nowNanos) {
        AnimationPose result = provider.pose(stack, modelId, nowNanos);
        return result == null ? AnimationPose.EMPTY : result;
    }

    @FunctionalInterface
    public interface PoseProvider {
        AnimationPose pose(ItemStack stack, String modelId, long nowNanos);
    }
}
