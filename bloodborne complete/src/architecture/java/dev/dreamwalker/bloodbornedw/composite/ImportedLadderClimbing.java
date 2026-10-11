package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.architecture.PlacementPhysics;
import dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/** Only the two reviewed imported vertical ladders; existing ladders are unchanged. */
public final class ImportedLadderClimbing {
    private static final Set<String> IDS = Set.of("bloodborne_dw:source_hold_ladder_02", "bloodborne_dw:source_hold_ladder_03");
    private ImportedLadderClimbing() {}
    public static Optional<BlockPos> climbing(World world, Box body) {
        Set<Owner> seen = new HashSet<>();
        Box search = body.expand(.3);
        BlockPos low = new BlockPos(MathHelper.floor(search.minX),MathHelper.floor(search.minY),MathHelper.floor(search.minZ));
        BlockPos high = new BlockPos(MathHelper.floor(search.maxX),MathHelper.floor(search.maxY),MathHelper.floor(search.maxZ));
        for (BlockPos cell : BlockPos.iterate(low,high)) {
            if (!world.isChunkLoaded(cell)) continue;
            for (var contribution : CompositeRuntime.contributions(world,cell)) {
                Owner owner = contribution.owner();
                if (!IDS.contains(owner.registryId()) || !seen.add(owner)) continue;
                BlockPos root = CompositeData.pos(owner.root());
                if (!world.isChunkLoaded(root) || !(world.getBlockEntity(root) instanceof CompositeBlockEntity entity)
                        || !owner.equals(entity.resident())) continue;
                var state = world.getBlockState(root);
                if (!(state.getBlock() instanceof CompositeRootBlock block) || block.spec.importedClimbingBounds == null) continue;
                var b = block.spec.importedClimbingBounds;
                double mount = entity.payload().getDouble("MountY") + entity.payload().getDouble(VerticalMount.KEY);
                var shift = CompositeSourceShift.world(state,entity.payload());
                var local = new ImportedCollisionMath.Box(b.from().x()/16,b.from().y()/16,b.from().z()/16,b.to().x()/16,b.to().y()/16,b.to().z()/16);
                for (var transformed : ImportedCollisionMath.transform(List.of(local),state.get(CompositeRootBlock.ROTATION),mount,false)) {
                    Box zone = new Box(transformed.x0(),transformed.y0(),transformed.z0(),transformed.x1(),transformed.y1(),transformed.z1()).offset(root.getX()+shift.x(),root.getY()+shift.y(),root.getZ()+shift.z());
                    if (PlacementPhysics.overlaps(body,zone)) return Optional.of(root);
                }
            }
        }
        return Optional.empty();
    }
}
