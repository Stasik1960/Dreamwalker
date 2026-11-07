package dev.dreamwalker.bloodbornedw.editor.worldedit;

import dev.dreamwalker.bloodbornedw.composite.CompositeRuntime;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import dev.dreamwalker.bloodbornerp.object.RpObjectIndex;
import dev.dreamwalker.bloodbornerp.object.RpObjectSelection;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;

/** Find space behind a DreamWalker model using Minecraft's actual collision geometry. */
public final class CompassPassage {
    public enum Outcome { VANILLA, OBSTRUCTED, TELEPORTED }

    private static final double STEP = 0.125;

    private record RayHit(BlockHitResult hit, boolean dreamwalker) {}

    private CompassPassage() {}

    public static Outcome teleport(ServerPlayerEntity player, int maxDistance) {
        if (maxDistance <= 0) return Outcome.VANILLA;
        ServerWorld world = player.getServerWorld();
        Vec3d start = player.getEyePos();
        Vec3d direction = player.getRotationVector();
        Vec3d end = start.add(direction.multiply(maxDistance));
        ShapeContext context = ShapeContext.of(player);
        RayHit first = BlockView.raycast(start, end, context, (shape, pos) -> {
            var chunk = world.getChunkManager().getWorldChunk(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) return new RayHit(null, false);
            BlockState state = chunk.getBlockState(pos);
            BlockHitResult hit = state.getOutlineShape(world, pos, shape).raycast(start, end, pos);
            if (hit == null) hit = state.getCollisionShape(world, pos, shape).raycast(start, end, pos);
            if (hit == null) return null;
            return new RayHit(hit, isDreamwalker(world, pos, state));
        }, ignored -> null);

        double hitDistance = first == null || first.hit() == null ? maxDistance : start.distanceTo(first.hit().getPos());
        BlockPos selectedBlock = first != null && first.dreamwalker() ? first.hit().getBlockPos() : null;
        RpObjectEntity selectedObject = null;
        List<RpObjectEntity> objects = RpObjectIndex.in(world, new Box(start, end).expand(2));
        for (RpObjectEntity object : objects) {
            var point = RpObjectSelection.hit(object, start, end).orElse(null);
            if (point == null) continue;
            double distance = start.distanceTo(point);
            if (distance >= hitDistance) continue;
            hitDistance = distance;
            selectedBlock = null;
            selectedObject = object;
        }
        if (selectedBlock == null && selectedObject == null) return Outcome.VANILLA;
        Box aimedCell = selectedBlock == null ? null : new Box(selectedBlock);
        List<Box> aimedObject = selectedObject == null ? List.of() : selectedObject.interactionBoxes();

        double minDistance = hitDistance + player.getWidth() * 0.5 + STEP;
        Box original = player.getBoundingBox();
        for (double distance = minDistance; distance <= maxDistance; distance += STEP) {
            Vec3d delta = direction.multiply(distance);
            Box destination = original.offset(delta);
            if (!loaded(world, destination)) return Outcome.OBSTRUCTED;
            if (destination.minY < world.getBottomY() || destination.maxY > world.getTopY()
                    || !world.getWorldBorder().contains(destination)
                    || (aimedCell != null && aimedCell.intersects(destination))
                    || aimedObject.stream().anyMatch(destination::intersects)) continue;
            if (!world.isSpaceEmpty(player, destination)) continue;
            if (blockedDestination(world, context, destination)) continue;
            boolean intersectsObject = objects.stream().anyMatch(object ->
                    object.activePhysicalBoxes().stream().anyMatch(destination::intersects));
            if (intersectsObject) continue;
            player.networkHandler.requestTeleport(player.getX() + delta.x, player.getY() + delta.y,
                    player.getZ() + delta.z, player.getYaw(), player.getPitch());
            player.fallDistance = 0;
            return Outcome.TELEPORTED;
        }
        return Outcome.OBSTRUCTED;
    }

    private static boolean isDreamwalker(ServerWorld world, BlockPos pos, BlockState state) {
        return Registries.BLOCK.getId(state.getBlock()).getNamespace().equals("bloodborne_dw")
                || !CompositeRuntime.contributions(world, pos).isEmpty();
    }

    private static boolean loaded(ServerWorld world, Box box) {
        for (int x = MathHelper.floor(box.minX) >> 4; x <= MathHelper.floor(box.maxX) >> 4; x++) {
            for (int z = MathHelper.floor(box.minZ) >> 4; z <= MathHelper.floor(box.maxZ) >> 4; z++) {
                if (world.getChunkManager().getWorldChunk(x, z) == null) return false;
            }
        }
        return true;
    }

    private static boolean blockedDestination(ServerWorld world, ShapeContext context, Box box) {
        BlockPos min = BlockPos.ofFloored(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.ofFloored(box.maxX, box.maxY, box.maxZ);
        for (BlockPos pos : BlockPos.iterate(min, max)) {
            var state = world.getBlockState(pos);
            if (state.isIn(BlockTags.FIRE) || state.isOf(Blocks.POWDER_SNOW) || state.getFluidState().isIn(FluidTags.LAVA)) return true;
            if (isDreamwalker(world, pos, state)) {
                for (Box part : state.getOutlineShape(world, pos, context).getBoundingBoxes()) {
                    if (box.intersects(part.offset(pos))) return true;
                }
            }
        }
        return false;
    }
}
