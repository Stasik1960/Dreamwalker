package dev.dreamwalker.bloodbornedw.item.mixin;

import dev.dreamwalker.bloodbornedw.item.DreamWalkerMining;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ClientPlayerInteractionManager.class, priority = 2000)
abstract class ClientMiningMixin {
    @Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
    private void dw$predictOnlyAuthorizedMining(BlockPos pos, net.minecraft.util.math.Direction direction,
                                                CallbackInfoReturnable<Boolean> cir) {
        if (deniesMining(pos)) cir.setReturnValue(false);
    }

    @Inject(method = "breakBlock", at = @At("HEAD"), cancellable = true)
    private void dw$predictOnlyAuthorizedRemoval(BlockPos pos,CallbackInfoReturnable<Boolean> cir) {
        if (deniesMining(pos)) cir.setReturnValue(false);
    }

    @org.spongepowered.asm.mixin.Unique
    private static boolean deniesMining(BlockPos pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.player.isCreative()) return false;
        BlockState state = client.world.getBlockState(pos);
        return DreamWalkerMining.isDreamWalkerBlock(state) && !DreamWalkerMining.permits(client.player,client.world,pos,state);
    }

    @Redirect(method = {"attackBlock","breakBlock"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isBlockBreakingRestricted(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/GameMode;)Z"))
    private boolean dw$allowAdventurePickaxe(ClientPlayerEntity player, World world, BlockPos pos, GameMode gameMode) {
        if (DreamWalkerMining.bypassAdventureRestriction(player, world, pos, gameMode)) return false;
        return player.isBlockBreakingRestricted(world, pos, gameMode);
    }

}
