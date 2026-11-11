package dev.dreamwalker.bloodbornedw.composite;

import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;

/** New placement chooses the paired wall form; saved blocks retain their own form. */
public final class PlacementWallArt {
    private PlacementWallArt() {}

    public static ItemStack prepare(ItemPlacementContext context) {
        ItemStack working = context.getStack().copy();
        var tag = working.getOrCreateSubNbt("BlockStateTag");
        // New fences always use automatic connections on the cardinal grid.
        int yaw = (PlacementRotation.perpendicular(context.getPlayerYaw(), context) + 4) & 6;
        tag.putString("rotation", Integer.toString(yaw));
        tag.putString("connections", "auto");
        return working;
    }

}
