package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.*;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SourceFallbackCarrierV1271.class, remap = false)
public abstract class RetainedFrameCarrierMixin {
    @Inject(method = "settings", at = @At("RETURN"))
    private static void dw$settings(boolean opaque, CallbackInfoReturnable<AbstractBlock.Settings> cir) {
        cir.getReturnValue()
            .solidBlock((s,w,p) -> !RetainedFrameState.matches(s) && SourceFallbackCarrierV1271.source(s).isSolidBlock(w,p))
            .suffocates((s,w,p) -> !RetainedFrameState.matches(s) && SourceFallbackCarrierV1271.source(s).shouldSuffocate(w,p))
            .blockVision((s,w,p) -> !RetainedFrameState.matches(s) && SourceFallbackCarrierV1271.source(s).shouldBlockVision(w,p));
    }

    @Inject(method = "collisionOverride", at = @At("HEAD"), cancellable = true)
    private static void dw$collision(BlockState s, CallbackInfoReturnable<VoxelShape> cir) {
        if (RetainedFrameState.matches(s)) cir.setReturnValue(VoxelShapes.empty());
    }

    @Inject(method = {"method_9571", "method_25959"}, at = @At("HEAD"), cancellable = true)
    private void dw$emptyShape(BlockState s, BlockView w, BlockPos p, CallbackInfoReturnable<VoxelShape> cir) {
        if (RetainedFrameState.matches(s)) cir.setReturnValue(VoxelShapes.empty());
    }

    @Inject(method = "method_37403", at = @At("HEAD"), cancellable = true)
    private void dw$notFull(BlockState s, BlockView w, BlockPos p, CallbackInfoReturnable<Boolean> cir) {
        if (RetainedFrameState.matches(s)) cir.setReturnValue(false);
    }

    @Inject(method = "method_9579", at = @At("HEAD"), cancellable = true)
    private void dw$transparent(BlockState s, BlockView w, BlockPos p, CallbackInfoReturnable<Boolean> cir) {
        if (RetainedFrameState.matches(s)) cir.setReturnValue(true);
    }

    @Inject(method = "method_9505", at = @At("HEAD"), cancellable = true)
    private void dw$opacity(BlockState s, BlockView w, BlockPos p, CallbackInfoReturnable<Integer> cir) {
        if (RetainedFrameState.matches(s)) cir.setReturnValue(0);
    }

    @Inject(method = "method_9575", at = @At("HEAD"), cancellable = true)
    private void dw$ambient(BlockState s, BlockView w, BlockPos p, CallbackInfoReturnable<Float> cir) {
        if (RetainedFrameState.matches(s)) cir.setReturnValue(1.0F);
    }

    @Inject(method = "method_9522", at = @At("HEAD"), cancellable = true)
    private void dw$side(BlockState s, BlockState adjacent, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (RetainedFrameState.matches(s)) cir.setReturnValue(false);
    }

    @Inject(method = "method_9526", at = @At("HEAD"), cancellable = true)
    private void dw$sided(BlockState s, CallbackInfoReturnable<Boolean> cir) {
        if (RetainedFrameState.matches(s)) cir.setReturnValue(true);
    }
}

