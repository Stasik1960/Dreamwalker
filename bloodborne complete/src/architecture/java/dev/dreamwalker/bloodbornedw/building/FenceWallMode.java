package dev.dreamwalker.bloodbornedw.building;

import dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallBlock;
import dev.dreamwalker.bloodbornedw.tool.ToolTarget;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.WallShape;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class FenceWallMode {
    private enum Mode {
        AUTOMATIC("Ограда соединяется с соседями"),
        STRAIGHT("Ограда в режиме сплошной секции"),
        SAME_ID("Ограда соединяется только с блоками того же ID");

        private final String label;

        Mode(String label) { this.label = label; }
    }

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
                BlockPos other = state.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER ? pos.down() : pos.up();
                if (!world.canPlayerModifyAt(player, other)) return false;
            }
            if (!fence.toggleWall(world, pos, state, axis)) return false;
            BlockState current = world.getBlockState(pos);
            Mode mode = fence.sameIdOnly(current) ? Mode.SAME_ID
                    : current.get(ConnectedFenceBlock.WALL_AXIS) == ConnectedFenceBlock.WallAxis.NONE
                            ? Mode.AUTOMATIC : Mode.STRAIGHT;
            return feedback(player, mode, true);
        }
        if (state.getBlock() instanceof PrototypeWallBlock wall && !PrototypeWallBlock.diagonalPost(state)) {
            Mode mode = switch (state.get(PrototypeWallBlock.CONNECTIONS)) {
                case AUTO -> Mode.STRAIGHT;
                case MANUAL -> Mode.SAME_ID;
                case SAME_ID -> Mode.AUTOMATIC;
            };
            var next = state.with(PrototypeWallBlock.ROTATION, 0).with(PrototypeWallBlock.CONNECTIONS, switch (mode) {
                case STRAIGHT -> PrototypeWallBlock.Connections.MANUAL;
                case SAME_ID -> PrototypeWallBlock.Connections.SAME_ID;
                case AUTOMATIC -> PrototypeWallBlock.Connections.AUTO;
            });
            if (mode == Mode.STRAIGHT) {
                next = next.with(PrototypeWallBlock.POST, false);
                for (Direction side : Direction.Type.HORIZONTAL) {
                    next = next.with(PrototypeWallBlock.property(side), side.getAxis() == axis ? WallShape.TALL : WallShape.NONE);
                }
            } else {
                next = wall.reconnect(next, world, pos);
            }
            return feedback(player, mode, world.setBlockState(pos, next, Block.NOTIFY_ALL));
        }
        return null;
    }

    private static boolean feedback(ServerPlayerEntity player, Mode mode, boolean changed) {
        if (changed) player.sendMessage(Text.literal(mode.label), true);
        return changed;
    }
}
