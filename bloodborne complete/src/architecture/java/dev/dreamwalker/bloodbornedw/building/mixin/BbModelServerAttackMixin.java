package dev.dreamwalker.bloodbornedw.building.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.dreamwalker.bloodbornedw.building.BbModelClickScope;
import dev.dreamwalker.bloodbornedw.tool.BbToolService;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ServerPlayerInteractionManager.class)
abstract class BbModelServerAttackMixin {
    @Shadow @Final protected ServerPlayerEntity player;

    @WrapMethod(method = "processBlockBreakingAction")
    private void dw$allowAuthorizedToolAttack(BlockPos pos, PlayerActionC2SPacket.Action action, Direction direction,
                                              int worldHeight, int sequence, Operation<Void> original) {
        var world = player.getServerWorld();
        if (action != PlayerActionC2SPacket.Action.START_DESTROY_BLOCK || player.isCreative()
                || !player.getMainHandStack().isOf(BbToolService.ITEM)
                || !world.isInBuildLimit(pos) || pos.getY() >= worldHeight || !world.isChunkLoaded(pos)
                || !world.getWorldBorder().contains(pos)
                || player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos))
                    > ServerPlayNetworkHandler.MAX_BREAK_SQUARED_DISTANCE) {
            original.call(pos, action, direction, worldHeight, sequence);
            return;
        }
        try (var ignored = BbModelClickScope.open(player, world, pos)) {
            original.call(pos, action, direction, worldHeight, sequence);
        }
    }
}
