package dev.dreamwalker.bloodbornedw.editor.worldedit.mixin;
import dev.dreamwalker.bloodbornedw.editor.worldedit.AppearanceTag;

import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import dev.dreamwalker.bloodbornedw.editor.CityEditorServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.dreamwalker.bloodbornedw.compat.worldedit.WorldEditBridge$ObjectExtent", remap = false)
abstract class ObjectExtentMixin {
    @Shadow @Final private ServerWorld world;
    @Shadow @Final private boolean outsideHistory;

    @Inject(method = "getFullBlock", at = @At("RETURN"), cancellable = true, remap = false)
    private void dw$capture(BlockVector3 vector, CallbackInfoReturnable<BaseBlock> cir) {
        cir.setReturnValue(AppearanceTag.capture(world, new BlockPos(vector.getX(), vector.getY(), vector.getZ()), cir.getReturnValue()));
    }

    @Inject(method = "setBlock", at = @At("HEAD"), remap = false)
    private void dw$validate(BlockVector3 vector, BlockStateHolder<?> value, CallbackInfoReturnable<Boolean> cir) throws InputParseException {
        if (outsideHistory) return;
        if (!AppearanceTag.valid(value.toBaseBlock())) throw new InputParseException("Invalid city editor clipboard appearance");
        var pos = new BlockPos(vector.getX(),vector.getY(),vector.getZ());
        if (world.getServer().isOnThread() && value.getBlockType().getId().startsWith("bloodborne_dw:")
                && !CityEditorServer.canQueueAppearance(world,pos))
            throw new InputParseException("Очередь цветов DreamWalker занята. Повторите вставку после обработки.");
    }

    // Return ordinal 1 is the bridge's explicit selected-empty no-op; ordinal 0 is the outer adapter.
    @Inject(method = "setBlock", at = { @At(value = "RETURN", ordinal = 2), @At(value = "RETURN", ordinal = 3), @At(value = "RETURN", ordinal = 4), @At(value = "RETURN", ordinal = 5) }, remap = false)
    private void dw$restore(BlockVector3 vector, BlockStateHolder<?> value, CallbackInfoReturnable<Boolean> cir) {
        if (outsideHistory || !cir.getReturnValue() || !world.getServer().isOnThread()) return;
        BlockPos pos = new BlockPos(vector.getX(), vector.getY(), vector.getZ());
        int style = CityEditorServer.supported(world.getBlockState(pos)) ? AppearanceTag.style(value.toBaseBlock(), world, pos) : -1;
        CityEditorServer.queueAppearance(world, pos, style);
    }
}

