package dev.dreamwalker.bloodbornedw.architecture;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Creative construction mode or an operator's editing rights; checked by the server. */
public final class BuildPermissions {
    private BuildPermissions() {}
    public static boolean canEdit(PlayerEntity player) {
        return player != null && player.getAbilities().allowModifyWorld
                && (player.getAbilities().creativeMode || player.hasPermissionLevel(2));
    }
    public static boolean canEdit(World world, PlayerEntity player, BlockPos pos) {
        return canEdit(player) && world.canPlayerModifyAt(player, pos);
    }
}
