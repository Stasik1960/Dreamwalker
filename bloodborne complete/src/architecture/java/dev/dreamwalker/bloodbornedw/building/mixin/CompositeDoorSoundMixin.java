package dev.dreamwalker.bloodbornedw.building.mixin;

import dev.dreamwalker.bloodbornedw.building.DoorSounds;
import dev.dreamwalker.bloodbornedw.composite.CompositeData;
import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import dev.dreamwalker.bloodbornedw.composite.CompositeRuntime;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CompositeRuntime.class, remap = false)
abstract class CompositeDoorSoundMixin {
    @Inject(method = "notifyOpenTransition", at = @At("HEAD"))
    private static void dreamwalker$playCommittedDoorSound(ServerWorld world, Owner owner, BlockState before,
            BlockState after, PlayerEntity actor, TransactionCore.Result result, CallbackInfo ci) {
        var open = CompositeRootBlock.OPEN;
        if (result.outcome() == TransactionCore.Outcome.COMMITTED && before.isOf(after.getBlock())
                && after.contains(open) && !before.get(open).equals(after.get(open))) {
            DoorSounds.architecture(world, CompositeData.pos(owner.root()), after, after.get(open));
        }
    }
}
