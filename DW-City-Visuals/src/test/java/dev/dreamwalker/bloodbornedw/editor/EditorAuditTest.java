package dev.dreamwalker.bloodbornedw.editor;

import java.util.Map;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

public final class EditorAuditTest {
    public static void main(String[] args) {
        if (EditorProtocol.COMMANDS.contains("stamp") || EditorProtocol.acceptsAction("stamp")
                || EditorProtocol.MODES.contains("stamp") || !EditorProtocol.acceptsAction("paint")) {
            throw new AssertionError("Legacy stamp can still replace brush painting");
        }

        long removed = new BlockPos(-2, 20, -3).asLong();
        long recolored = new BlockPos(-3, 31, -4).asLong();
        long untouched = new BlockPos(-5, 25, -4).asLong();
        ChunkPos chunk = new ChunkPos(-1, -1);
        EditorCells initial = EditorCells.EMPTY.withAll(Map.of(removed, 1, recolored, 2, untouched, 3));
        EditorCells expected = initial.withAll(Map.of(removed, -1, recolored, 4));
        NbtCompound delta = expected.packDelta(new long[]{recolored, removed});
        EditorCells applied = initial.withAll(EditorCells.readDelta(delta, chunk, -64, 320));
        if (applied.size() != 2 || applied.style(removed) != -1 || applied.style(recolored) != 4
                || applied.style(untouched) != 3 || initial.style(removed) != 1) {
            throw new AssertionError("Editor delta did not preserve unchanged cells or remove a color");
        }
        EditorCells loaded = EditorCells.readPacked(applied.packed(), chunk, -64, 320);
        if (!loaded.entries().equals(applied.entries())) throw new AssertionError("Disk snapshot did not round trip");
        reject(expected.packDelta(new long[]{removed, removed}), chunk);
        reject(expected.packDelta(new long[]{new BlockPos(0, 20, -3).asLong()}), chunk);
        reject(expected.packDelta(new long[EditorCells.MAX_DELTA_CELLS + 1]), chunk);
        System.out.println("Editor audit checks passed");
    }

    private static void reject(NbtCompound invalid, ChunkPos chunk) {
        try {
            EditorCells.readDelta(invalid, chunk, -64, 320);
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Invalid editor delta was accepted");
    }
}
