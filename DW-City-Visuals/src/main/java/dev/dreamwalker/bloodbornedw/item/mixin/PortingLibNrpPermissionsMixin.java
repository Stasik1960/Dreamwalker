package dev.dreamwalker.bloodbornedw.item.mixin;

import dev.dreamwalker.bloodbornedw.item.DreamWalkerMining;
import dev.dreamwalker.bloodbornedw.item.NrpBreakScope;
import net.minecraft.block.Block;
import net.minecraft.block.pattern.CachedBlockPosition;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Preserve Porting Lib's break event while permitting the authorised Adventure tool. */
@Pseudo
@Mixin(targets = "io.github.fabricators_of_create.porting_lib.util.PortingHooks", remap = false)
abstract class PortingLibNrpPermissionsMixin {
    @Redirect(method = "onBlockBreakEvent(Lnet/minecraft/class_1937;Lnet/minecraft/class_1934;Lnet/minecraft/class_3222;Lnet/minecraft/class_2338;Z)I",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/class_1799;method_7940(Lnet/minecraft/class_2378;Lnet/minecraft/class_2694;)Z", remap = false),
            remap = false)
    private static boolean dw$allowScopedAdventureTool(ItemStack stack, Registry<Block> registry,
            CachedBlockPosition cachedPosition, World world, GameMode mode, ServerPlayerEntity player,
            BlockPos pos, boolean canMine) {
        if (stack.canDestroy(registry, cachedPosition)) return true;
        return world instanceof ServerWorld serverWorld && stack == player.getMainHandStack()
                && NrpBreakScope.permitsAdventure(serverWorld, player)
                && DreamWalkerMining.bypassAdventureRestriction(player, world, pos, mode);
    }
}
