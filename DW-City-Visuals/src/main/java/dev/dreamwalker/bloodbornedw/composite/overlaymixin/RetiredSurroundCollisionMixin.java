package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import com.google.gson.JsonObject;
import dev.dreamwalker.bloodbornedw.composite.ImportedCollisionV125;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The retired surround is an empty carrier for static recipe indices in the active map. */
@Mixin(value = ImportedCollisionV125.class, remap = false)
public abstract class RetiredSurroundCollisionMixin {
    @Inject(method = "authoredExact", at = @At("HEAD"), cancellable = true)
    private static void dw$allowEmptyRetiredSurround(JsonObject row, CallbackInfoReturnable<Boolean> result) {
        if (row.get("path").getAsString().equals("source_shutter_window_surround")) {
            result.setReturnValue(true);
        }
    }
}
