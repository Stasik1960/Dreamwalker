package dev.dreamwalker.bloodbornedw.building.mixin;

import dev.dreamwalker.bloodbornedw.tool.QuarterBlockShift;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.dreamwalker.bloodbornedw.tool.DebugAdjustments", remap = false)
abstract class QuarterBlockShiftMixin {
    @Inject(method = "x", at = @At("HEAD"), cancellable = true)
    private static void dw$quarterX(int value, CallbackInfoReturnable<Double> result) {
        result.setReturnValue(QuarterBlockShift.offset(value, 0));
    }

    @Inject(method = "y", at = @At("HEAD"), cancellable = true)
    private static void dw$quarterY(int value, CallbackInfoReturnable<Double> result) {
        result.setReturnValue(QuarterBlockShift.offset(value, 1));
    }

    @Inject(method = "z", at = @At("HEAD"), cancellable = true)
    private static void dw$quarterZ(int value, CallbackInfoReturnable<Double> result) {
        result.setReturnValue(QuarterBlockShift.offset(value, 2));
    }

    @Inject(method = "hasPose", at = @At("HEAD"), cancellable = true)
    private static void dw$hasPose(int value, CallbackInfoReturnable<Boolean> result) {
        result.setReturnValue(QuarterBlockShift.hasPose(value));
    }

    @Inject(method = "sanitize", at = @At("HEAD"), cancellable = true)
    private static void dw$sanitize(int value, CallbackInfoReturnable<Integer> result) {
        result.setReturnValue(QuarterBlockShift.sanitize(value));
    }

    @Inject(method = "moved", at = @At("HEAD"), cancellable = true)
    private static void dw$move(int value, Direction direction, CallbackInfoReturnable<Integer> result) {
        int axis = switch (direction.getAxis()) {
            case X -> 0;
            case Y -> 1;
            case Z -> 2;
        };
        result.setReturnValue(QuarterBlockShift.move(value, axis, direction.getDirection().offset()));
    }
}
