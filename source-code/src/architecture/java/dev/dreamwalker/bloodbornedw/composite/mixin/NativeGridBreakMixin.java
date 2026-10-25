package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla break paths preflight the exact whole footprint before any member
 * changes. Ordinary architecture cells keep vanilla's independent break path. */
@Mixin(World.class)
public abstract class NativeGridBreakMixin {
    @Inject(method = "breakBlock(Lnet/minecraft/util/math/BlockPos;ZLnet/minecraft/entity/Entity;I)Z", at = @At("HEAD"), cancellable = true)
    private void dw$nativeGridWholeBreak(BlockPos pos, boolean drop, Entity entity, int depth, CallbackInfoReturnable<Boolean> result) {
        if (NativeGridRuntime.writing() || !((Object) this instanceof ServerWorld world)) return;
        var state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof NativeGridBlock block)) return;
        int index = block.cell(state).index();
        if (NativeGridCatalog.membership(index) != null)
            result.setReturnValue(NativeGridRuntime.breakWhole(world, pos, index, entity instanceof PlayerEntity player ? player : null, drop));
    }
}
