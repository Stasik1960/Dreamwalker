package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Footprint;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.shape.VoxelShape;

/** Precombined per native recipe, with the original non-player shape retained. */
public record StudioStaticShapes(VoxelShape players, VoxelShape blocks, VoxelShape outline) {
    public interface Access { StudioStaticShapes dw$studioShapes(); }
    public static final StudioStaticShapes UNEDITED = new StudioStaticShapes(null, null, null);

    public static StudioStaticShapes combine(StaticArchitectureCatalog.Recipe recipe) {
        if (recipe.fragments().stream().noneMatch(part -> StudioFootprints.staticPose(part.pose()) != null)) return UNEDITED;
        List<Box> players = new ArrayList<>(), blocks = new ArrayList<>(), outline = new ArrayList<>();
        for (var part : recipe.fragments()) {
            var origin = part.origin();
            var cell = new Cell(-origin.x(), -origin.y(), -origin.z());
            var layout = StudioFootprints.staticPose(part.pose());
            if (layout == null) {
                var stock = part.pose().footprint().getOrDefault(cell, Footprint.EMPTY);
                players.addAll(stock.collision());
                blocks.addAll(stock.collision());
                outline.addAll(part.pose().outline().getOrDefault(cell, List.of()));
            } else {
                players.addAll(layout.players().getOrDefault(cell, List.of()));
                blocks.addAll(layout.blocks().getOrDefault(cell, List.of()));
                outline.addAll(layout.outline().getOrDefault(cell, List.of()));
            }
        }
        return new StudioStaticShapes(ImportedPhysicalShapes.exact(players), ImportedPhysicalShapes.exact(blocks),
                ImportedPhysicalShapes.exact(outline));
    }

    public static StudioStaticShapes of(StaticArchitectureCatalog.Recipe recipe) {
        return ((Access) (Object) recipe).dw$studioShapes();
    }
}
