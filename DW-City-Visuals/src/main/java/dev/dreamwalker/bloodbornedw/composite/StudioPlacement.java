package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.architecture.NoCollidePlacement;
import dev.dreamwalker.bloodbornedw.architecture.PlacementPhysics;
import dev.dreamwalker.bloodbornerp.object.RpObjectIndex;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

public final class StudioPlacement {
    private StudioPlacement() {}

    public static boolean rpBlocks(World world, Box proposed, Entity ignored) {
        if (NoCollidePlacement.ignoringBlocks()) return false;
        for (var object : RpObjectIndex.in(world, proposed)) {
            if (object == ignored) continue;
            var layout = StudioRpGeometry.layout(object);
            if (layout == null) continue;
            for (var box : layout.blocks()) if (PlacementPhysics.overlaps(proposed, box)) return true;
        }
        return false;
    }
}
