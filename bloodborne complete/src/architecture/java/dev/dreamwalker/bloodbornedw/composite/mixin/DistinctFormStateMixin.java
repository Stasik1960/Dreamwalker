package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.DistinctForms;
import net.minecraft.state.State;
import net.minecraft.state.property.Property;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(State.class)
abstract class DistinctFormStateMixin {
    @Shadow @Final protected Object owner;

    // The renderer and physics can still read the original pose through their existing selectors.
    @Inject(method = "get", at = @At(value = "NEW", target = "java/lang/IllegalArgumentException"), cancellable = true)
    private void dreamwalker$readFixedSelector(Property<?> property, CallbackInfoReturnable<Comparable<?>> cir) {
        Comparable<?> value = DistinctForms.selector(owner, property);
        if (value != null) cir.setReturnValue(value);
    }

    @Inject(method = "with", at = @At(value = "NEW", target = "java/lang/IllegalArgumentException", ordinal = 0), cancellable = true)
    private void dreamwalker$keepFixedSelector(Property<?> property, Comparable<?> value, CallbackInfoReturnable<State<?, ?>> cir) {
        if (DistinctForms.selector(owner, property) != null) cir.setReturnValue((State<?, ?>) (Object) this);
    }
}
