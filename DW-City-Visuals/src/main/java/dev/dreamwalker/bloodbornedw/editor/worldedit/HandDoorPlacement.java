package dev.dreamwalker.bloodbornedw.editor.worldedit;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.function.block.BlockReplace;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.world.block.BaseBlock;

/** Places the two halves of an ordinary mod door through the same WorldEdit history. */
public final class HandDoorPlacement extends BlockReplace {
    public static final String ID = "bloodborne_dw:door_90960_plain";

    public static final class Marker extends BaseBlock {
        public Marker(BaseBlock block) {
            super(block.toImmutableState());
        }
    }

    private final Extent extent;
    private final BaseBlock lower;
    private final BaseBlock upper;
    private final Property<String> half;

    public HandDoorPlacement(Extent extent, Marker marker) {
        super(extent, marker);
        this.extent = extent;
        half = marker.getBlockType().getProperty("half");
        lower = marker.with(half, "lower");
        upper = marker.with(half, "upper");
    }

    @Override
    public boolean apply(BlockVector3 pos) throws WorldEditException {
        BlockVector3 top = pos.add(0, 1, 0);
        BlockVector3 floor = pos.add(0, -1, 0);
        if (top.getY() > extent.getMaximumPoint().getY() || floor.getY() < extent.getMinimumPoint().getY()) return false;

        BaseBlock before = extent.getFullBlock(pos);
        if (before.getBlockType().equals(lower.getBlockType()) && "upper".equals(before.getState(half))) return false;
        if (!extent.getBlock(floor).getBlockType().getMaterial().isSolid()) return false;
        var above = extent.getBlock(top);
        if (!above.getBlockType().getMaterial().isAir() && !upper.equalsFuzzy(above)) return false;

        if (extent instanceof EditSession session) {
            var mask = session.getMask();
            if (mask != null && (!mask.test(pos) || !mask.test(top))) return false;
            int limit = session.getBlockChangeLimit();
            if (limit >= 0 && limit - session.getBlockChangeCount() < 2) return false;
        }

        boolean bottomChanged = extent.setBlock(pos, lower);
        if (!bottomChanged && !lower.equalsFuzzy(extent.getBlock(pos))) return false;
        boolean complete = false;
        try {
            boolean topChanged = extent.setBlock(top, upper);
            complete = topChanged || upper.equalsFuzzy(extent.getBlock(top));
            return complete && (bottomChanged || topChanged);
        } finally {
            if (!complete && bottomChanged) extent.setBlock(pos, before);
        }
    }
}
