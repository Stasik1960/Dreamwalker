package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.CompactStateTransitions;
import dev.dreamwalker.bloodbornedw.composite.WallStateLookup;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = CompactStateTransitions.class, remap = false)
public abstract class WallStateLookupMixin {
    @ModifyVariable(method = "createOwned", at = @At("HEAD"), argsOnly = true, ordinal = 1, remap = false)
    private static Map<?, ?> dw$indexWalls(Map<?, ?> states, Object owner, Map<?, ?> values, Map<?, ?> originalStates, Object currentState) {
        return WallStateLookup.prepare(owner, states);
    }
}
