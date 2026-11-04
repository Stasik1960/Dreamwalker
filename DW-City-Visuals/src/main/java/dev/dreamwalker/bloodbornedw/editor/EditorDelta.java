package dev.dreamwalker.bloodbornedw.editor;

import java.util.Arrays;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

/** Bounded binary representation for editor appearance deltas. */
public final class EditorDelta {
    public static final int MAX_ENCODED_BYTES = 2048;
    private static final int ENTRY_BYTES = Long.BYTES + Integer.BYTES;

    public record Decoded(Identifier dimension, ChunkPos chunk, long revision,
                          long[] positions, int[] values) {
        public Decoded {
            if (positions.length != values.length) throw new IllegalArgumentException("Mismatched editor delta arrays");
        }
    }

    private EditorDelta() {}

    public static void write(PacketByteBuf buf, Identifier dimension, ChunkPos chunk,
                             long revision, EditorCells cells, long[] positions) {
        if (positions.length == 0 || positions.length > EditorCells.MAX_DELTA_CELLS)
            throw new IllegalArgumentException("Editor delta count out of range");
        buf.writeIdentifier(dimension);
        buf.writeInt(chunk.x);
        buf.writeInt(chunk.z);
        buf.writeLong(revision);
        buf.writeByte(positions.length);
        for (long position : positions) {
            int value = cells.style(position);
            if (value != -1 && !EditorCells.isStored(value))
                throw new IllegalArgumentException("Invalid editor appearance value");
            BlockPos pos = BlockPos.fromLong(position);
            if ((pos.getX() >> 4) != chunk.x || (pos.getZ() >> 4) != chunk.z)
                throw new IllegalArgumentException("Editor delta position is outside its chunk");
            buf.writeLong(position);
            buf.writeInt(value);
        }
        if (buf.readableBytes() > MAX_ENCODED_BYTES)
            throw new IllegalArgumentException("Editor delta exceeds packet budget");
    }

    public static Decoded read(PacketByteBuf buf) {
        int packetBytes = buf.readableBytes();
        if (packetBytes > MAX_ENCODED_BYTES) throw new IllegalArgumentException("Editor delta exceeds packet budget");
        Identifier dimension = buf.readIdentifier();
        ChunkPos chunk = new ChunkPos(buf.readInt(), buf.readInt());
        long revision = buf.readLong();
        int count = buf.readUnsignedByte();
        if (count == 0 || count > EditorCells.MAX_DELTA_CELLS
                || buf.readableBytes() != count * ENTRY_BYTES)
            throw new IllegalArgumentException("Invalid editor delta length");
        long[] positions = new long[count];
        int[] values = new int[count];
        for (int i = 0; i < count; i++) {
            long position = buf.readLong();
            int value = buf.readInt();
            BlockPos pos = BlockPos.fromLong(position);
            if ((value != -1 && !EditorCells.isStored(value))
                    || (pos.getX() >> 4) != chunk.x || (pos.getZ() >> 4) != chunk.z)
                throw new IllegalArgumentException("Invalid editor delta entry");
            for (int previous = 0; previous < i; previous++) {
                if (positions[previous] == position)
                    throw new IllegalArgumentException("Duplicate editor delta position");
            }
            positions[i] = position;
            values[i] = value;
        }
        return new Decoded(dimension, chunk, revision, positions, values);
    }
}
