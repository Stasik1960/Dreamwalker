package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import java.util.function.Predicate;
import java.util.stream.Stream;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keep real glass blended when the same cell also contains opaque/cutout artwork. */
@Mixin(targets = "dev.dreamwalker.bloodbornedw.composite.StaticArchitectureCatalog$Recipe", remap = false)
public abstract class StaticRecipeLayerMixin {
    @Redirect(method = "<init>", at = @At(value = "INVOKE",
        target = "Ljava/util/stream/Stream;allMatch(Ljava/util/function/Predicate;)Z", remap = false), remap = false)
    private <T> boolean dw$anyTranslucent(Stream<T> stream, Predicate<? super T> predicate) {
        return stream.anyMatch(predicate);
    }
}
