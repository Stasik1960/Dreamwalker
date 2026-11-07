package dev.dreamwalker.bloodbornedw.tool.mixin;

import dev.dreamwalker.bloodbornedw.tool.DebugAdjustments;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RpObjectEntity.class, remap = false)
abstract class RpObjectDebugDataMixin implements DebugAdjustments.ObjectSettings {
    @Unique private static final TrackedData<Integer> dw$DEBUG =
            DataTracker.registerData(RpObjectEntity.class, TrackedDataHandlerRegistry.INTEGER);

    @Inject(method = "method_5693()V", at = @At("TAIL"), remap = false)
    private void dw$trackDebugData(CallbackInfo ci) {
        ((RpObjectEntity)(Object)this).getDataTracker().startTracking(dw$DEBUG, 0);
    }

    @Override public int dw$debugSettings() {
        return ((RpObjectEntity)(Object)this).getDataTracker().get(dw$DEBUG);
    }

    @Override public void dw$debugSettings(int value) {
        ((RpObjectEntity)(Object)this).getDataTracker().set(dw$DEBUG, DebugAdjustments.sanitize(value));
    }

    @Inject(method = "method_5652(Lnet/minecraft/class_2487;)V", at = @At("TAIL"), remap = false)
    private void dw$writeDebugData(NbtCompound nbt, CallbackInfo ci) {
        int value = dw$debugSettings();
        if (value != 0) nbt.putInt("DwDebugSettings", value);
    }

    @Inject(method = "method_5749(Lnet/minecraft/class_2487;)V", at = @At("TAIL"), remap = false)
    private void dw$readDebugData(NbtCompound nbt, CallbackInfo ci) {
        dw$debugSettings(nbt.getInt("DwDebugSettings"));
    }
}
