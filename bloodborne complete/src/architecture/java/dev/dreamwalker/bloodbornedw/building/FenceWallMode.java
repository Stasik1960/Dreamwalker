package dev.dreamwalker.bloodbornedw.building;

import dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallBlock;
import dev.dreamwalker.bloodbornedw.tool.ToolTarget;
import net.minecraft.block.Block;
import net.minecraft.block.enums.WallShape;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class FenceWallMode {
    private FenceWallMode() {}

    /** Null leaves special actions for non-fence blocks to the original tool. */
    public static Boolean toggle(ServerPlayerEntity player, ToolTarget target) {
        var world = player.getServerWorld();
        if (target == null || target.world(player.getServer()) != world || !target.valid(player.getServer())) return null;
        var pos = BlockPos.fromLong(target.root());
        if (!world.canPlayerModifyAt(player, pos)) return null;
        var state = world.getBlockState(pos);
        Direction.Axis axis = player.getHorizontalFacing().rotateYClockwise().getAxis();
        if (state.getBlock() instanceof ConnectedFenceBlock fence) {
            if (state.getBlock() instanceof ConnectedFenceColumnBlock) {
                BlockPos other = state.get(net.minecraft.state.property.Properties.DOUBLE_BLOCK_HALF)
                        == net.minecraft.block.enums.DoubleBlockHalf.UPPER ? pos.down() : pos.up();
                if (!world.canPlayerModifyAt(player, other)) return false;
            }
            boolean entering = state.get(ConnectedFenceBlock.WALL_AXIS) == ConnectedFenceBlock.WallAxis.NONE;
            return feedback(player, entering, fence.toggleWall(world, pos, state, axis));
        }
        if (state.getBlock() instanceof PrototypeWallBlock wall && !PrototypeWallBlock.diagonalPost(state)) {
            boolean entering = state.get(PrototypeWallBlock.CONNECTIONS) == PrototypeWallBlock.Connections.AUTO;
            var next = state.with(PrototypeWallBlock.CONNECTIONS,
                    entering ? PrototypeWallBlock.Connections.MANUAL : PrototypeWallBlock.Connections.AUTO);
            if (entering) {
                next = next.with(PrototypeWallBlock.POST, false).with(PrototypeWallBlock.ROTATION, 0);
                for (Direction side : Direction.Type.HORIZONTAL) {
                    next = next.with(PrototypeWallBlock.property(side), side.getAxis() == axis ? WallShape.LOW : WallShape.NONE);
                }
            } else {
                next = wall.reconnect(next.with(PrototypeWallBlock.ROTATION, 0), world, pos);
            }
            return feedback(player, entering, world.setBlockState(pos, next, Block.NOTIFY_ALL));
        }
        return null;
    }

    public static boolean feedback(ServerPlayerEntity player, boolean entering, boolean changed) {
        if (changed) player.sendMessage(Text.literal(entering ? "Ограда: сплошная секция" : "Ограда: соединение с соседями"), true);
        return changed;
    }
}
