package dev.dreamwalker.bloodbornedw.runtime;

import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Point;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Resolves each independent owner by its own selection geometry, including shared carriers. */
public final class TargetResolver {
    private TargetResolver() {}
    public record Target(Owner owner, double distance) {}
    public record Targets(List<Target> candidates, List<Owner> deferred, List<Owner> stale) {
        public Targets { candidates = List.copyOf(candidates); deferred = List.copyOf(deferred); stale = List.copyOf(stale); }
        public Target nearest() { return candidates.isEmpty() ? null : candidates.get(0); }
    }
    public static Targets raycastCell(TransactionCore.WorldAccess world, Cell cell, Point origin, Point direction, double limit) {
        if (direction.x() == 0 && direction.y() == 0 && direction.z() == 0) throw new IllegalArgumentException("Zero ray direction");
        if (!Double.isFinite(limit) || limit < 0) throw new IllegalArgumentException("Invalid ray limit");
        if (!world.isLoaded(cell)) return new Targets(List.of(), List.of(), List.of());
        List<Target> candidates = new ArrayList<>(); List<Owner> deferred = new ArrayList<>(), stale = new ArrayList<>();
        for (Owner owner : TransactionCore.owners(world.read(cell))) {
            TransactionCore.OwnerInspection inspection = TransactionCore.inspectOwner(world, owner, cell);
            switch (inspection.status()) {
                case DEFERRED -> deferred.add(owner);
                case STALE -> stale.add(owner);
                case LIVE -> {
                    double distance = inspection.instance().at(cell).selection().stream()
                            .mapToDouble(box -> box.rayDistance(cell, origin, direction, limit)).min().orElse(Double.POSITIVE_INFINITY);
                    if (Double.isFinite(distance)) candidates.add(new Target(owner, distance));
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(Target::distance).thenComparing(Target::owner));
        return new Targets(candidates, deferred, stale);
    }
}
