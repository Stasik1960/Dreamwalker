package dev.dreamwalker.bloodbornedw.building;

import dev.dreamwalker.bloodbornedw.tool.BbToolService;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Admits one non-mining BB tool attack through the packet's mining precheck. */
public final class BbModelClickScope implements AutoCloseable {
    private static final ThreadLocal<BbModelClickScope> ACTIVE = new ThreadLocal<>();

    private final BbModelClickScope previous;
    private final PlayerEntity player;
    private final World world;
    private final BlockPos pos;
    private boolean checked;

    private BbModelClickScope(PlayerEntity player, World world, BlockPos pos) {
        previous = ACTIVE.get();
        this.player = player;
        this.world = world;
        this.pos = pos.toImmutable();
        ACTIVE.set(this);
    }

    public static BbModelClickScope open(PlayerEntity player, World world, BlockPos pos) {
        if (player == null || world == null || player.isCreative() || !BbToolService.canEdit(player)
                || !player.getMainHandStack().isOf(BbToolService.ITEM)
                || !world.canPlayerModifyAt(player, pos)) return null;
        return new BbModelClickScope(player, world, pos);
    }

    public static boolean accept(PlayerEntity player, World world, BlockPos pos) {
        BbModelClickScope scope = ACTIVE.get();
        if (scope == null || scope.checked || scope.player != player || scope.world != world || !scope.pos.equals(pos)) {
            return false;
        }
        scope.checked = true;
        return true;
    }

    @Override
    public void close() {
        if (previous == null) ACTIVE.remove();
        else ACTIVE.set(previous);
    }
}
