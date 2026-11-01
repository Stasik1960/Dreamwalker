package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.*;

import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerInteractionManager.class)
public abstract class StandardLanternAttackMixin {
    @Shadow @Final protected ServerPlayerEntity player;
    @Inject(method = "processBlockBreakingAction", at = @At("HEAD"), cancellable = true)
    private void dw$attack(BlockPos pos, PlayerActionC2SPacket.Action action, Direction direction, int worldHeight, int sequence, CallbackInfo ci) {
        if ((action == PlayerActionC2SPacket.Action.START_DESTROY_BLOCK || action == PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK || action == PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK) && StandardLanternGameplay.attack(player, pos, action == PlayerActionC2SPacket.Action.START_DESTROY_BLOCK)) ci.cancel();
    }
}

