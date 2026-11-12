package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.DistinctForms;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.state.State;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Property;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StateManager.Builder.class)
abstract class DistinctFormStateBuilderMixin {
    @Shadow @Final private Object owner;
    @Shadow @Final private Map<String, Property<?>> namedProperties;

    @Inject(method = "build", at = @At("HEAD"))
    private void dreamwalker$omitSelectors(Function<?, ?> defaults, StateManager.Factory<?, ?> factory,
            CallbackInfoReturnable<StateManager<?, ?>> cir) {
        DistinctForms.omitSelectors(owner, namedProperties);
    }
}
