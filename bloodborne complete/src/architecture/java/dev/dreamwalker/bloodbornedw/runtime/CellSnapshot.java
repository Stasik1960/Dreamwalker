package dev.dreamwalker.bloodbornedw.runtime;

import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

/** Immutable world snapshot. The Fabric adapter must capture the entire BE payload. */
public record CellSnapshot(Kind kind, BlockData data, Owner resident, List<Owner> guests) {
    public enum Kind { AIR, HELPER, ROOT, FOREIGN }

    public CellSnapshot {
        Objects.requireNonNull(kind); Objects.requireNonNull(data);
        TreeSet<Owner> ordered = new TreeSet<>(guests);
        if (ordered.size() != guests.size()) throw new IllegalArgumentException("Duplicate guest binding");
        if ((kind == Kind.ROOT) != (resident != null)) throw new IllegalArgumentException("Root ownership mismatch");
        if (resident != null && ordered.stream().anyMatch(owner -> owner.instanceId().equals(resident.instanceId())))
            throw new IllegalArgumentException("Root is also its own guest");
        if (kind == Kind.AIR && !ordered.isEmpty()) throw new IllegalArgumentException("Air cannot carry guests");
        guests = List.copyOf(ordered);
    }

    public CellSnapshot withGuests(List<Owner> nextGuests) { return new CellSnapshot(kind, data, resident, nextGuests); }

    public static final class BlockData {
        private final String blockId;
        private final Map<String, String> properties;
        private final byte[] blockEntityNbt;
        public BlockData(String blockId, Map<String, String> properties, byte[] blockEntityNbt) {
            this.blockId = Objects.requireNonNull(blockId);
            TreeMap<String, String> ordered = new TreeMap<>(properties);
            ordered.forEach((key, value) -> { Objects.requireNonNull(key); Objects.requireNonNull(value); });
            this.properties = Collections.unmodifiableMap(ordered);
            this.blockEntityNbt = Objects.requireNonNull(blockEntityNbt).clone();
        }
        public String blockId() { return blockId; }
        public Map<String, String> properties() { return properties; }
        public byte[] blockEntityNbt() { return blockEntityNbt.clone(); }
        public BlockData withProperties(Map<String, String> next) { return new BlockData(blockId, next, blockEntityNbt); }
        public BlockData withNbt(byte[] nbt) { return new BlockData(blockId, properties, nbt); }
        @Override public boolean equals(Object value) {
            return value instanceof BlockData other && blockId.equals(other.blockId) && properties.equals(other.properties)
                    && Arrays.equals(blockEntityNbt, other.blockEntityNbt);
        }
        @Override public int hashCode() { return Objects.hash(blockId, properties, Arrays.hashCode(blockEntityNbt)); }
        @Override public String toString() { return blockId + properties + " [BE bytes=" + blockEntityNbt.length + "]"; }
    }
}
