package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallBlock;
import dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallItem;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/** New placement chooses the paired wall form; saved blocks retain their own form. */
public final class PlacementWallArt {
    private PlacementWallArt() {}

    public static ItemStack prepare(ItemPlacementContext context) {
        ItemStack working = context.getStack().copy();
        var tag = working.getOrCreateSubNbt("BlockStateTag");
        int material = PrototypeWallItem.material(working);
        boolean diagonal = PlacementRotation.diagonal(context);
        // Wall artwork's zero-facing convention is opposite the player's yaw.
        int yaw = (PlacementRotation.yaw(context.getPlayerYaw(), context) + 4) & 7;
        tag.putString("rotation", Integer.toString(yaw));
        if (material == 0 && diagonal) {
            if (!"manual".equals(tag.getString("connections"))) {
                if (!neighbourhoodReady(context)) return null;
                PrototypeWallBlock block = (PrototypeWallBlock)((BlockItem)working.getItem()).getBlock();
                BlockState nativeState = block.getPlacementState(context);
                if (nativeState != null) {
                    BlockState local = block.fromNativeState(nativeState.with(PrototypeWallBlock.ROTATION, yaw), nativeState);
                    tag.putString("up", Boolean.toString(local.get(PrototypeWallBlock.POST)));
                    for (Direction direction : Direction.Type.HORIZONTAL)
                        tag.putString(direction.asString(), local.get(PrototypeWallBlock.property(direction)).asString());
                }
            }
            tag.putString("connections", "manual");
        }
        // For the retained fence, the existing item selects material 1 (diagonal
        // post) or 6 (cardinal fence) from this validated rotation parity. Its own
        // conversion, collision and inventory accounting run normally afterward.
        return working;
    }

    /** Vanilla wall connection sampling must not wait for neighbouring FULL futures. */
    private static boolean neighbourhoodReady(ItemPlacementContext context) {
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        if (!chunkReady(world, pos) || !chunkReady(world, pos.up())) return false;
        for (Direction direction : Direction.Type.HORIZONTAL)
            if (!chunkReady(world, pos.offset(direction))) return false;
        return true;
    }

    private static boolean chunkReady(World world, BlockPos pos) {
        return world instanceof ServerWorld server
            ? server.getChunkManager().getWorldChunk(pos.getX() >> 4, pos.getZ() >> 4) != null
            : world.isChunkLoaded(pos);
    }
}
