package dev.dreamwalker.bestiary.mixin;

import dev.dreamwalker.bestiary.ActorSpec;
import dev.dreamwalker.bloodbornerp.mob.MobAnimationSelector;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MobAnimationSelector.class, remap = false)
abstract class MobAnimationSelectorMixin {
    @Inject(method = "attacks", at = @At("HEAD"), cancellable = true)
    private static void bestiaryAttacks(String id, CallbackInfoReturnable<List<String>> callback) {
        if (ActorSpec.find(id) != null) callback.setReturnValue(List.of("attack1", "attack2"));
    }
}
