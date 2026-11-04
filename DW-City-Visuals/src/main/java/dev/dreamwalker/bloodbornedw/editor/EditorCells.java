package dev.dreamwalker.bloodbornedw.editor;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

/** Sparse immutable appearance overrides. Missing cells use automatic city coloring. */
public final class EditorCells {
    public static final int MAX_DELTA_CELLS = 128;
    private static final int FORMAT = 1;
    public static final EditorCells EMPTY = new EditorCells(new long[0], new int[0]);

    private final long[] positions;
    private final int[] values;

    private EditorCells(long[] positions, int[] values) {
        this.positions = positions;
        this.values = values;
    }

    public int style(BlockPos pos) { return style(pos.asLong()); }

    public int style(long pos) {
        if (positions.length == 0) return -1;
        int i = Arrays.binarySearch(positions, pos);
        return i < 0 ? -1 : values[i];
    }

    public int size() { return positions.length; }
    public long positionAt(int index) { return positions[index]; }
    public int styleAt(int index) { return values[index]; }

    /** New snapshots use versioned int values. Old packed byte arrays remain readable. */
    public NbtCompound packed() {
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("Format", FORMAT);
        nbt.putLongArray("Positions", positions.clone());
        nbt.putIntArray("Values", values.clone());
        return nbt;
    }

    public static EditorCells readPacked(NbtCompound nbt, ChunkPos chunk, int bottomY, int topY) {
        if (!nbt.contains("Positions", NbtElement.LONG_ARRAY_TYPE))
            throw new IllegalArgumentException("Invalid packed editor positions");

        long[] positions = nbt.getLongArray("Positions");
        int[] values;
        if (nbt.contains("Format")) {
            if (!nbt.contains("Format", NbtElement.INT_TYPE))
                throw new IllegalArgumentException("Invalid packed editor version tag");
            if (nbt.getInt("Format") != FORMAT || nbt.contains("Styles")
                    || !nbt.contains("Values", NbtElement.INT_ARRAY_TYPE))
                throw new IllegalArgumentException("Unsupported packed editor format");
            values = nbt.getIntArray("Values");
        } else {
            if (!nbt.contains("Styles", NbtElement.BYTE_ARRAY_TYPE) || nbt.contains("Values"))
                throw new IllegalArgumentException("Invalid legacy packed editor values");
            byte[] styles = nbt.getByteArray("Styles");
            values = new int[styles.length];
            for (int i = 0; i < styles.length; i++) values[i] = styles[i];
        }

        validateBudget(positions.length, values.length, chunk, bottomY, topY);
        for (int i = 0; i < positions.length; i++) {
            BlockPos pos = BlockPos.fromLong(positions[i]);
            if ((i > 0 && positions[i] <= positions[i - 1])
                    || pos.getX() >> 4 != chunk.x || pos.getZ() >> 4 != chunk.z
                    || pos.getY() < bottomY || pos.getY() >= topY
                    || !validStored(values[i]))
                throw new IllegalArgumentException("Invalid packed editor entry");
        }
        return positions.length == 0 ? EMPTY : new EditorCells(positions.clone(), values.clone());
    }

    NbtCompound packDelta(long[] changed) {
        int[] updates = new int[changed.length];
        for (int i = 0; i < changed.length; i++) updates[i] = style(changed[i]);
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("Format", FORMAT);
        nbt.putLongArray("Positions", changed.clone());
        nbt.putIntArray("Values", updates);
        return nbt;
    }

    static Map<Long, Integer> readDelta(NbtCompound nbt, ChunkPos chunk, int bottomY, int topY) {
        if (!nbt.contains("Positions", NbtElement.LONG_ARRAY_TYPE))
            throw new IllegalArgumentException("Invalid editor delta positions");
        long[] positions = nbt.getLongArray("Positions");
        int[] values;
        if (nbt.contains("Format")) {
            if (!nbt.contains("Format", NbtElement.INT_TYPE))
                throw new IllegalArgumentException("Invalid editor delta version tag");
            if (nbt.getInt("Format") != FORMAT || nbt.contains("Styles")
                    || !nbt.contains("Values", NbtElement.INT_ARRAY_TYPE))
                throw new IllegalArgumentException("Unsupported editor delta format");
            values = nbt.getIntArray("Values");
        } else {
            if (!nbt.contains("Styles", NbtElement.BYTE_ARRAY_TYPE) || nbt.contains("Values"))
                throw new IllegalArgumentException("Invalid legacy editor delta");
            byte[] styles = nbt.getByteArray("Styles");
            values = new int[styles.length];
            for (int i = 0; i < styles.length; i++) values[i] = styles[i];
        }
        if (positions.length != values.length || positions.length == 0
                || positions.length > MAX_DELTA_CELLS)
            throw new IllegalArgumentException("Editor delta budget");
        Map<Long, Integer> updates = new HashMap<>();
        for (int i = 0; i < positions.length; i++) {
            BlockPos pos = BlockPos.fromLong(positions[i]);
            int value = values[i];
            if (!inside(pos, chunk, bottomY, topY) || !validDelta(value)
                    || updates.put(positions[i], value) != null)
                throw new IllegalArgumentException("Invalid editor delta entry");
        }
        return updates;
    }

    public Map<Long, Integer> entries() {
        Map<Long, Integer> result = new HashMap<>();
        for (int i = 0; i < positions.length; i++) result.put(positions[i], values[i]);
        return Map.copyOf(result);
    }

    public static int rgb(int rawColor) {
        if (rawColor < 0 || rawColor > 0x00ffffff)
            throw new IllegalArgumentException("RGB color must be in 0..0xFFFFFF");
        return 0x01000000 | rawColor;
    }

    public static boolean isRgb(int value) { return (value & 0xff000000) == 0x01000000; }

    public static boolean isStored(int value) { return value >= 0 && value <= 4 || isRgb(value); }

    public EditorCells with(long pos, int value) { return withAll(Map.of(pos, value)); }

    public EditorCells withAll(Map<Long, Integer> updates) {
        if (updates.isEmpty()) return this;
        long[] keys = new long[updates.size()];
        int k = 0;
        for (Map.Entry<Long, Integer> entry : updates.entrySet()) {
            if (!validDelta(entry.getValue())) throw new IllegalArgumentException("Invalid editor appearance");
            keys[k++] = entry.getKey();
        }
        Arrays.sort(keys);
        int count = positions.length;
        boolean changed = false;
        for (long key : keys) {
            int i = Arrays.binarySearch(positions, key), value = updates.get(key);
            int old = i < 0 ? -1 : values[i];
            if (old != value) {
                changed = true;
                if (old < 0) count++;
                else if (value < 0) count--;
            }
        }
        if (!changed) return this;
        if (count == 0) return EMPTY;
        long[] nextPositions = new long[count];
        int[] nextValues = new int[count];
        int i = 0, j = 0, out = 0;
        while (i < positions.length || j < keys.length) {
            if (j == keys.length || (i < positions.length && positions[i] < keys[j])) {
                nextPositions[out] = positions[i];
                nextValues[out++] = values[i++];
            } else {
                long key = keys[j++];
                int value = updates.get(key);
                if (i < positions.length && positions[i] == key) i++;
                if (value >= 0) {
                    nextPositions[out] = key;
                    nextValues[out++] = value;
                }
            }
        }
        return new EditorCells(nextPositions, nextValues);
    }

    /** Legacy list representation is retained for old save readers and QA fixtures. */
    public NbtList nbt() {
        NbtList list = new NbtList();
        for (int i = 0; i < positions.length; i++) {
            if (!isStored(values[i]) || isRgb(values[i]))
                throw new IllegalStateException("Legacy editor list cannot serialize RGB values");
            NbtCompound row = new NbtCompound();
            row.putLong("Pos", positions[i]);
            row.putByte("Style", (byte) values[i]);
            list.add(row);
        }
        return list;
    }

    public static EditorCells read(NbtList list, ChunkPos chunk, int bottomY, int topY) {
        if ((!list.isEmpty() && list.getHeldType() != NbtElement.COMPOUND_TYPE)
                || topY < bottomY || list.size() > ((long) topY - bottomY) * 256)
            throw new IllegalArgumentException("Editor cell budget");
        Map<Long, Integer> map = new HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            NbtCompound row = list.getCompound(i);
            if (!row.contains("Pos", NbtElement.LONG_TYPE) || !row.contains("Style", NbtElement.BYTE_TYPE))
                throw new IllegalArgumentException("Invalid editor cell");
            long p = row.getLong("Pos");
            BlockPos pos = BlockPos.fromLong(p);
            int value = row.getByte("Style");
            if (!inside(pos, chunk, bottomY, topY) || value < 0 || value > 4
                    || map.put(p, value) != null)
                throw new IllegalArgumentException("Invalid editor position/style");
        }
        return EMPTY.withAll(map);
    }

    private static void validateBudget(int positionCount, int valueCount, ChunkPos chunk,
                                       int bottomY, int topY) {
        if (positionCount != valueCount || topY < bottomY
                || positionCount > ((long) topY - bottomY) * 256)
            throw new IllegalArgumentException("Packed editor budget");
    }

    private static boolean inside(BlockPos pos, ChunkPos chunk, int bottomY, int topY) {
        return (pos.getX() >> 4) == chunk.x && (pos.getZ() >> 4) == chunk.z
                && pos.getY() >= bottomY && pos.getY() < topY;
    }

    private static boolean validStored(int value) {
        return isStored(value);
    }

    private static boolean validDelta(int value) {
        return value == -1 || validStored(value);
    }
}
