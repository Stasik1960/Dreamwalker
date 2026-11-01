package dev.dreamwalker.bloodbornedw.composite;

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

    /** The ladder calls floor(yaw / 45 + 0.5) rather than Math.round. */
    public static int biasedSteps(float biasedSteps, ItemPlacementContext context) {
        return yaw((biasedSteps - 0.5F) * 45.0F, context);
    }
}
