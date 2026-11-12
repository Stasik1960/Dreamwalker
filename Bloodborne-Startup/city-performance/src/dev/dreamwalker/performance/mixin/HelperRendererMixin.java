package dev.dreamwalker.performance.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.dreamwalker.bloodbornedw.composite.CompositeArchitecture;
import dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity;
import net.minecraft.class_2586;
import net.minecraft.class_824;
import net.minecraft.class_827;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = class_824.class, remap = false)
public abstract class HelperRendererMixin {
    @ModifyReturnValue(method = "method_3550", at = @At("RETURN"), remap = false)
    private <E extends class_2586> class_827<E> dw$skipHelper(class_827<E> renderer, E entity) {
        // The release renderer draws composite roots and mounted native blocks.
        // The generic helper carrier is neither. Its shape, ownership and native
        // bridge models are consumed independently of the block entity renderer.
        if (!(entity instanceof CompositeBlockEntity)
                || entity.method_11010().method_26204() != CompositeArchitecture.CELL) {
            return renderer;
        }
        if (renderer != null && renderer.getClass().getName().equals(
                "dev.dreamwalker.bloodbornedw.composite.CompositeClient$2")) {
            return null;
        }
        return renderer;
    }
}
