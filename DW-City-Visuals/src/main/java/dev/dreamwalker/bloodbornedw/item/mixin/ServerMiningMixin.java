package dev.dreamwalker.bloodbornedw.item.mixin;

import dev.dreamwalker.bloodbornedw.item.DreamWalkerMining;
import dev.dreamwalker.bloodbornedw.item.NrpBreakScope;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerPlayerInteractionManager.class, priority = 2000)
abstract class ServerMiningMixin {
    @Shadow @Final protected ServerPlayerEntity player;

    @Inject(method = "processBlockBreakingAction", at = @At("HEAD"), cancellable = true)
    private void dw$gateBlockPackets(BlockPos pos, PlayerActionC2SPacket.Action action, Direction direction,
                                     int worldHeight, int sequence, CallbackInfo ci) {
        if (action == PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK || player.isCreative()) return;
        ServerWorld world = player.getServerWorld();
        if (!world.isChunkLoaded(pos) || pos.getY() < world.getBottomY() || pos.getY() >= world.getTopY()) return;
        BlockState state = world.getBlockState(pos);
        if (DreamWalkerMining.isDreamWalkerBlock(state) && !DreamWalkerMining.permits(player, world, pos, state)) ci.cancel();
    }

    @WrapMethod(method = "tryBreakBlock")
    private boolean dw$scopeAuthorizedBreak(BlockPos pos, Operation<Boolean> original) {
        ServerWorld world = player.getServerWorld();
        if (!world.isChunkLoaded(pos) || pos.getY() < world.getBottomY() || pos.getY() >= world.getTopY()) {
            return original.call(pos);
        }
        BlockState state = world.getBlockState(pos);
        if (!DreamWalkerMining.isDreamWalkerBlock(state) || player.isCreative()
                || DreamWalkerMining.isLantern(world, pos, state, player)) return original.call(pos);
        if (!world.getWorldBorder().contains(pos) || !world.canPlayerModifyAt(player, pos)
                || !DreamWalkerMining.permits(player, world, pos, state)) return false;
        try (var ignored = NrpBreakScope.open(player,world)) {
            return original.call(pos);
        }
    }

    @Redirect(method = {"processBlockBreakingAction", "tryBreakBlock"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;isBlockBreakingRestricted(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/GameMode;)Z"))
    private boolean dw$allowAdventurePickaxe(ServerPlayerEntity player, World world, BlockPos pos, GameMode gameMode) {
        if (DreamWalkerMining.bypassAdventureRestriction(player, world, pos, gameMode)) return false;
        return player.isBlockBreakingRestricted(world, pos, gameMode);
    }
}
