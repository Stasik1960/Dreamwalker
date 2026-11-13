package dev.dreamwalker.bloodbornedw.building;

import dev.dreamwalker.bloodbornedw.architecture.PlacementPhysics;
import dev.dreamwalker.bloodbornedw.composite.CompositeData;
import dev.dreamwalker.bloodbornedw.composite.CompositeSourceShift;
import dev.dreamwalker.bloodbornedw.composite.CompositeSpec;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

public final class ShutterFloorPlacement {
    private static final String FLOOR_CENTERED = "DwShutterFloorCentered";
    // The authored pane is at Z=6/16 plus the Studio offset of 9/16.
    private static final double CENTER_DELTA = -7.0 / 16;

    private ShutterFloorPlacement() {}

    public static NbtCompound forPlacement(CompositeSpec spec, Direction face, NbtCompound payload) {
        if (face != Direction.UP || !spec.id.getPath().equals("source_shutter_window")) return payload;
        NbtCompound centered = payload == null ? new NbtCompound() : payload.copy();
        if (!centered.getBoolean(FLOOR_CENTERED)) {
            shift(centered, CENTER_DELTA);
            centered.putBoolean(FLOOR_CENTERED, true);
        }
        return centered;
    }

    public static NbtCompound forItem(CompositeSpec spec, NbtCompound payload) {
        if (payload == null || !payload.getBoolean(FLOOR_CENTERED) || !spec.id.getPath().equals("source_shutter_window")) {
            return payload;
        }
        NbtCompound art = payload.copy();
        shift(art, -CENTER_DELTA);
        art.remove(FLOOR_CENTERED);
        return art;
    }

    public static List<Box> placementBoxes(ObjectInstance object, List<Box> boxes) {
        if (!object.owner().registryId().equals("bloodborne_dw:source_shutter_window")
                || !CompositeData.nbt(object.rootData().blockEntityNbt()).getBoolean(FLOOR_CENTERED)) return boxes;

        double floor = object.owner().root().y();
        List<Box> aboveFloor = new ArrayList<>(boxes.size());
        for (Box box : boxes) {
            if (box.maxY <= floor + PlacementPhysics.EPS) continue;
            aboveFloor.add(box.minY < floor ? new Box(box.minX, floor, box.minZ, box.maxX, box.maxY, box.maxZ) : box);
        }
        return aboveFloor;
    }

    private static void shift(NbtCompound payload, double delta) {
        var source = CompositeSourceShift.local(payload);
        var updated = new CompositeSourceShift.Shift(source.x(), source.y(), source.z() + delta);
        NbtList shift = new NbtList();
        shift.add(NbtDouble.of(updated.x()));
        shift.add(NbtDouble.of(updated.y()));
        shift.add(NbtDouble.of(updated.z()));
        payload.put("SourceShift", shift);
    }
}
