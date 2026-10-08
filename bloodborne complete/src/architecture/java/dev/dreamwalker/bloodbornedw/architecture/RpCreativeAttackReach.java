package dev.dreamwalker.bloodbornedw.architecture;

import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import dev.dreamwalker.bloodbornerp.object.RpObjectSelection;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** The existing RP editing ray replaces only an optional library's origin-distance precondition. */
public final class RpCreativeAttackReach {
    private RpCreativeAttackReach() {}

    /** Null unless the ordinary server editing ray selects this exact permitted RP instance. */
    public static Vec3d editablePartHit(PlayerEntity player, Entity target) {
        if (!(player instanceof ServerPlayerEntity)
                || !(player.getWorld() instanceof ServerWorld world)
                || !(target instanceof RpObjectEntity object)
                || player.isRemoved() || object.isRemoved()
                || object.getWorld() != world || !player.isCreative()
                || !player.getAbilities().allowModifyWorld || BuildingTool.isHeld(player)) return null;
        var selected = RpObjectSelection.playerTarget(player, 6);
        return selected != null && selected.getEntity() == object
                && world.canPlayerModifyAt(player, BlockPos.ofFloored(selected.getPos())) ? selected.getPos() : null;
    }

    public static boolean permitsPartEditing(PlayerEntity player, Entity target, double effectiveCapSquared) {
        Vec3d hit = editablePartHit(player, target);
        return hit != null && player.getEyePos().squaredDistanceTo(hit) <= effectiveCapSquared;
    }
}
