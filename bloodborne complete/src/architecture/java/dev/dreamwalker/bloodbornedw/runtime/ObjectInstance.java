package dev.dreamwalker.bloodbornedw.runtime;

import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Footprint;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/** One semantic object with exact instance identity; registry IDs do not prove ownership. */
public record ObjectInstance(Owner owner, CellSnapshot.BlockData rootData, Map<Cell, Footprint> cells) {
    public ObjectInstance {
        Objects.requireNonNull(owner);
        Objects.requireNonNull(rootData);
        TreeMap<Cell, Footprint> ordered = new TreeMap<>(cells);
        ordered.forEach((cell, footprint) -> { Objects.requireNonNull(cell); Objects.requireNonNull(footprint); });
        if (!ordered.containsKey(Cell.ORIGIN)) throw new IllegalArgumentException("Object footprint omits its root");
        cells = Collections.unmodifiableMap(ordered);
    }

    public record Owner(UUID instanceId, String registryId, Cell root) implements Comparable<Owner> {
        public Owner {
            Objects.requireNonNull(instanceId); Objects.requireNonNull(root);
            if (registryId == null || !registryId.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))
                throw new IllegalArgumentException("Invalid registry ID");
        }
        @Override public int compareTo(Owner other) {
            int order = root.compareTo(other.root);
            if (order == 0) order = registryId.compareTo(other.registryId);
            return order == 0 ? instanceId.compareTo(other.instanceId) : order;
        }
    }

    public Footprint at(Cell absoluteCell) { return cells.get(absoluteCell.subtract(owner.root)); }

    public ObjectInstance rotate(int quarterTurns, RotationHook hook) {
        TreeMap<Cell, Footprint> rotated = new TreeMap<>();
        cells.forEach((cell, footprint) -> rotated.put(cell.rotate(quarterTurns), footprint.rotate(quarterTurns)));
        return new ObjectInstance(owner, Objects.requireNonNull(hook.rotate(rootData, Math.floorMod(quarterTurns, 4))), rotated);
    }

    /** Hooks rotate only catalog-declared properties, retaining opaque block entity data. */
    @FunctionalInterface public interface RotationHook {
        CellSnapshot.BlockData rotate(CellSnapshot.BlockData data, int quarterTurns);
    }
}
