package dev.dreamwalker.bloodbornedw.editor;

import java.util.Map;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;

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
        EditorCells updated = initial.withAll(Map.of(removed, -1, recolored, 4));
        NbtCompound delta = updated.packDelta(new long[]{recolored, removed});
        EditorCells applied = initial.withAll(EditorCells.readDelta(delta, chunk, -64, 320));
        if (applied.size() != 2 || applied.style(removed) != -1 || applied.style(recolored) != 4
                || applied.style(untouched) != 3 || initial.style(removed) != 1) {
            throw new AssertionError("Editor delta did not preserve unchanged cells or remove a color");
        }
        EditorCells loaded = EditorCells.readPacked(applied.packed(), chunk, -64, 320);
        if (!loaded.entries().equals(applied.entries())) throw new AssertionError("Disk snapshot did not round trip");

        int black = EditorCells.rgb(0x000000);
        int white = EditorCells.rgb(0xffffff);
        if (black != 0x01000000 || white != 0x01ffffff || !EditorCells.isRgb(black)
                || EditorCells.isRgb(0) || !EditorCells.isStored(0) || EditorCells.isStored(5)) {
            throw new AssertionError("RGB tags overlap legacy values or have an invalid encoding");
        }
        long blackPos = new BlockPos(-4, 22, -3).asLong();
        long whitePos = new BlockPos(-3, 22, -3).asLong();
        EditorCells rgbCells = EditorCells.EMPTY.withAll(Map.of(blackPos, black, whitePos, white));
        EditorCells rgbLoaded = EditorCells.readPacked(rgbCells.packed(), chunk, -64, 320);
        if (rgbLoaded.style(blackPos) != black || rgbLoaded.style(whitePos) != white)
            throw new AssertionError("RGB black/white did not round trip");
        NbtCompound legacyPacked = new NbtCompound();
        long[] legacyPositions = new long[5];
        for (int i = 0; i < legacyPositions.length; i++)
            legacyPositions[i] = new BlockPos(-7 + i, 20, -3).asLong();
        legacyPacked.putLongArray("Positions", legacyPositions);
        legacyPacked.putByteArray("Styles", new byte[]{0, 1, 2, 3, 4});
        EditorCells legacyPackedCells = EditorCells.readPacked(legacyPacked, chunk, -64, 320);
        for (int i = 0; i < 5; i++) {
            if (legacyPackedCells.style(legacyPositions[i]) != i)
                throw new AssertionError("Legacy packed style changed during read");
        }
        NbtList legacyList = new NbtList();
        NbtCompound legacyRow = new NbtCompound();
        legacyRow.putLong("Pos", removed);
        legacyRow.putByte("Style", (byte) 4);
        legacyList.add(legacyRow);
        if (EditorCells.read(legacyList, chunk, -64, 320).style(removed) != 4)
            throw new AssertionError("Legacy list style 4 changed during read");
        try {
            rgbCells.nbt();
            throw new AssertionError("RGB value was truncated into the legacy list format");
        } catch (IllegalStateException expected) {
            // The legacy list format stores only the original palette byte.
        }
        NbtCompound malformedVersion = new NbtCompound();
        malformedVersion.putString("Format", "1");
        malformedVersion.putLongArray("Positions", new long[]{removed});
        malformedVersion.putByteArray("Styles", new byte[]{0});
        try {
            EditorCells.readPacked(malformedVersion, chunk, -64, 320);
            throw new AssertionError("Malformed packed version tag was accepted");
        } catch (IllegalArgumentException expected) {
            // A malformed current-format marker cannot fall back to legacy decoding.
        }

        EditorCells mixed = rgbCells.with(removed,0);
        if (mixed.with(blackPos,black) != mixed)
            throw new AssertionError("Writing the same RGB changed the immutable snapshot");
        EditorCells reset = mixed.with(blackPos,-1);
        EditorCells readReset = EditorCells.readPacked(reset.packed(),chunk,-64,320);
        if (readReset.style(blackPos) != -1 || readReset.style(removed) != 0 || readReset.style(whitePos) != white)
            throw new AssertionError("Reset lost explicit legacy zero or a neighboring RGB");
        EditorCells repeated = mixed;
        for (int rgb = 0; rgb < 10_000; rgb++) repeated = repeated.with(blackPos,EditorCells.rgb(rgb));
        if (repeated.size() != mixed.size() || repeated.style(blackPos) != EditorCells.rgb(9999))
            throw new AssertionError("Repeated colors accumulated sparse entries");

        PacketByteBuf wire = PacketByteBufs.create();
        try {
            EditorDelta.write(wire, new Identifier("bloodborne_dw", "test"), chunk,
                    17L, rgbCells, new long[]{blackPos, whitePos});
            EditorDelta.Decoded decoded = EditorDelta.read(wire);
            if (!decoded.dimension().equals(new Identifier("bloodborne_dw", "test"))
                    || !decoded.chunk().equals(chunk) || decoded.revision() != 17L
                    || decoded.positions().length != 2 || decoded.values()[0] != black
                    || decoded.values()[1] != white || wire.isReadable()) {
                throw new AssertionError("Binary editor delta did not round trip");
            }
        } finally {
            wire.release();
        }
        rejectsInvalidWireValue(removed, chunk);

        reject(updated.packDelta(new long[]{removed, removed}), chunk);
        reject(updated.packDelta(new long[]{new BlockPos(0, 20, -3).asLong()}), chunk);
        reject(updated.packDelta(new long[EditorCells.MAX_DELTA_CELLS + 1]), chunk);
        System.out.println("Editor audit checks passed");
    }

    private static void rejectsInvalidWireValue(long position, ChunkPos chunk) {
        PacketByteBuf invalid = PacketByteBufs.create();
        try {
            invalid.writeIdentifier(new Identifier("bloodborne_dw", "test"));
            invalid.writeInt(chunk.x);
            invalid.writeInt(chunk.z);
            invalid.writeLong(18L);
            invalid.writeByte(1);
            invalid.writeLong(position);
            invalid.writeInt(0x02000001);
            try {
                EditorDelta.read(invalid);
            } catch (IllegalArgumentException expected) {
                return;
            }
            throw new AssertionError("Malformed binary appearance tag was accepted");
        } finally {
            invalid.release();
        }
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
