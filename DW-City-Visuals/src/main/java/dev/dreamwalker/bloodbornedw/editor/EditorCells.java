package dev.dreamwalker.bloodbornedw.editor;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

/** Sparse immutable palette overrides. Missing cells use automatic city coloring. */
public final class EditorCells {
    public static final EditorCells EMPTY = new EditorCells(new long[0], new byte[0]);
    private final long[] positions;
    private final byte[] styles;
    private EditorCells(long[] positions, byte[] styles) { this.positions = positions; this.styles = styles; }
    public int style(BlockPos pos) {
        if (positions.length == 0) return -1;
        int i = Arrays.binarySearch(positions, pos.asLong());
        return i < 0 ? -1 : styles[i];
    }
    public int size() { return positions.length; }
    public long positionAt(int index) { return positions[index]; }
    public int styleAt(int index) { return styles[index]; }
    /** Flat arrays avoid an NBT compound allocation for every painted cell. */
    public NbtCompound packed() {
        NbtCompound nbt = new NbtCompound();
        nbt.putLongArray("Positions", positions.clone()); nbt.putByteArray("Styles", styles.clone());
        return nbt;
    }
    public static EditorCells readPacked(NbtCompound nbt, ChunkPos chunk, int bottomY, int topY) {
        if (!nbt.contains("Positions", NbtElement.LONG_ARRAY_TYPE) || !nbt.contains("Styles", NbtElement.BYTE_ARRAY_TYPE)) throw new IllegalArgumentException("Invalid packed editor cells");
        long[] positions = nbt.getLongArray("Positions"); byte[] styles = nbt.getByteArray("Styles");
        if (positions.length != styles.length || topY < bottomY || positions.length > ((long)topY-bottomY)*256) throw new IllegalArgumentException("Packed editor budget");
        for (int i=0;i<positions.length;i++) {
            BlockPos pos = BlockPos.fromLong(positions[i]);
            if ((i>0 && positions[i]<=positions[i-1]) || pos.getX()>>4!=chunk.x || pos.getZ()>>4!=chunk.z || pos.getY()<bottomY || pos.getY()>=topY || styles[i]<0 || styles[i]>4) throw new IllegalArgumentException("Invalid packed editor entry");
        }
        return positions.length==0?EMPTY:new EditorCells(positions.clone(),styles.clone());
    }
    public Map<Long, Byte> entries() {
        Map<Long, Byte> result = new HashMap<>();
        for (int i = 0; i < positions.length; i++) result.put(positions[i], styles[i]);
        return Map.copyOf(result);
    }
    public EditorCells with(long pos, int style) { return withAll(Map.of(pos, style)); }
    public EditorCells withAll(Map<Long, Integer> updates) {
        if (updates.isEmpty()) return this;
        long[] keys = new long[updates.size()];
        int k = 0;
        for (Map.Entry<Long, Integer> entry : updates.entrySet()) {
            int style = entry.getValue();
            if (style < -1 || style > 4) throw new IllegalArgumentException("Invalid editor style");
            keys[k++] = entry.getKey();
        }
        Arrays.sort(keys);
        int count = positions.length;
        boolean changed = false;
        for (long key : keys) {
            int i = Arrays.binarySearch(positions, key), value = updates.get(key);
            int old = i < 0 ? -1 : styles[i];
            if (old != value) { changed = true; if (old < 0) count++; else if (value < 0) count--; }
        }
        if (!changed) return this;
        if (count == 0) return EMPTY;
        long[] nextPositions = new long[count]; byte[] nextStyles = new byte[count];
        int i = 0, j = 0, out = 0;
        while (i < positions.length || j < keys.length) {
            if (j == keys.length || (i < positions.length && positions[i] < keys[j])) {
                nextPositions[out] = positions[i]; nextStyles[out++] = styles[i++];
            } else {
                long key = keys[j++]; int value = updates.get(key);
                if (i < positions.length && positions[i] == key) i++;
                if (value >= 0) { nextPositions[out] = key; nextStyles[out++] = (byte)value; }
            }
        }
        return new EditorCells(nextPositions, nextStyles);
    }
    public NbtList nbt() {
        NbtList list = new NbtList();
        for (int i = 0; i < positions.length; i++) {
            NbtCompound row = new NbtCompound(); row.putLong("Pos", positions[i]); row.putByte("Style", styles[i]); list.add(row);
        }
        return list;
    }
    public static EditorCells read(NbtList list, ChunkPos chunk, int bottomY, int topY) {
        if ((!list.isEmpty() && list.getHeldType() != NbtElement.COMPOUND_TYPE) || topY < bottomY || list.size() > ((long)topY - bottomY) * 256)
            throw new IllegalArgumentException("Editor cell budget");
        Map<Long, Integer> map = new HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            NbtCompound row = list.getCompound(i);
            if (!row.contains("Pos", NbtElement.LONG_TYPE) || !row.contains("Style", NbtElement.BYTE_TYPE)) throw new IllegalArgumentException("Invalid editor cell");
            long p = row.getLong("Pos"); BlockPos pos = BlockPos.fromLong(p); int style = row.getByte("Style");
            if (!new ChunkPos(pos).equals(chunk) || pos.getY() < bottomY || pos.getY() >= topY || style < 0 || style > 4 || map.put(p, style) != null) throw new IllegalArgumentException("Invalid editor position/style");
        }
        return EMPTY.withAll(map);
    }
}
