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

public final class ShutterFloorPlacement {
    private static final String FLOOR_MOUNTED = "DwShutterFloorMounted";
    private static final String LEGACY_FLOOR_CENTERED = "DwShutterFloorCentered";
    public static final String WINDOW_PATH = "source_shutter_window";

    private ShutterFloorPlacement() {}

    public static NbtCompound markFloorMount(NbtCompound payload) {
        NbtCompound mounted = payload == null ? new NbtCompound() : payload.copy();
        mounted.putBoolean(FLOOR_MOUNTED, true);
        return mounted;
    }

    public static NbtCompound forItem(CompositeSpec spec, NbtCompound payload) {
        if (payload == null || !payload.getBoolean(LEGACY_FLOOR_CENTERED) || !spec.id.getPath().equals(WINDOW_PATH)) {
            return payload;
        }
        NbtCompound art = payload.copy();
        var source = CompositeSourceShift.local(art);
        NbtList shift = new NbtList();
        shift.add(NbtDouble.of(source.x()));
        shift.add(NbtDouble.of(source.y()));
        shift.add(NbtDouble.of(source.z() + 7.0 / 16));
        art.put("SourceShift", shift);
        art.remove(LEGACY_FLOOR_CENTERED);
        return art;
    }

    public static List<Box> placementBoxes(ObjectInstance object, List<Box> boxes) {
        if (!object.owner().registryId().equals("bloodborne_dw:" + WINDOW_PATH)) return boxes;
        var payload = CompositeData.nbt(object.rootData().blockEntityNbt());
        if (!payload.getBoolean(FLOOR_MOUNTED) && !payload.getBoolean(LEGACY_FLOOR_CENTERED)) return boxes;

        // The sill meets the floor. Keep its authored volume in the stored footprint.
        double floor = object.owner().root().y();
        List<Box> aboveFloor = new ArrayList<>(boxes.size());
        for (Box box : boxes) {
            if (box.maxY <= floor + PlacementPhysics.EPS) continue;
            aboveFloor.add(box.minY < floor ? new Box(box.minX, floor, box.minZ, box.maxX, box.maxY, box.maxZ) : box);
        }
        return aboveFloor;
    }
}
