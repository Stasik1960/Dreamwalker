package dev.dreamwalker.bloodbornedw.composite;

import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;

/** Select an existing eight-step yaw before placement geometry is planned. */
public final class PlacementRotation {
    private PlacementRotation() {}

    public static boolean diagonal(ItemPlacementContext context) {
        return context.getPlayer() != null && context.getPlayer().isSneaking();
    }

    public static int yaw(float degrees, ItemPlacementContext context) {
        boolean diagonal = diagonal(context);
        float wrapped = Float.isFinite(degrees) ? degrees % 360.0F : 0.0F;
        int quarter = Math.floorMod(Math.round((wrapped - (diagonal ? 45.0F : 0.0F)) / 90.0F), 4);
        return quarter * 2 + (diagonal ? 1 : 0);
    }

    /** Original call sites pass player yaw divided by 45. */
    public static int steps(float steps, ItemPlacementContext context) {
        return yaw(steps * 45.0F, context);
    }

    public static int perpendicular(float degrees, ItemPlacementContext context) {
        return (yaw(degrees, context) + 2) & 7;
    }

    /** South-facing players see a model in the X/Y plane. Normalize art authored along Z. */
    public static int fitModel(int rotation, BlockState state) {
        if (!(state.getBlock() instanceof CompositeRootBlock block) || state.getBlock() instanceof ImportedStairBlock) return rotation;
        var variant = block.spec.variant(state);
        if (ThinWindowRootBlock.mount(state) != ThinWindowRootBlock.Mount.VERTICAL) {
            return fitBounds(rotation, block.spec.mountedBounds(state));
        }
        if (variant.visible() != null) return fitBounds(rotation, variant.visible());
        double minX = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (var part : variant.closed().selection()) {
            minX = Math.min(minX, part.from().x());
            maxX = Math.max(maxX, part.to().x());
            minZ = Math.min(minZ, part.from().z());
            maxZ = Math.max(maxZ, part.to().z());
        }
        return fit(rotation, maxX - minX, maxZ - minZ);
    }

    private static int fitBounds(int rotation, CompositeSpec.Bounds bounds) {
        return fit(rotation, bounds.to().x() - bounds.from().x(), bounds.to().z() - bounds.from().z());
    }

    private static int fit(int rotation, double widthX, double widthZ) {
        return widthZ > widthX + 1e-4 ? (rotation + 2) & 7 : rotation;
    }

    /** The ladder calls floor(yaw / 45 + 0.5) rather than Math.round. */
    public static int biasedSteps(float biasedSteps, ItemPlacementContext context) {
        return yaw((biasedSteps - 0.5F) * 45.0F, context);
    }
}
