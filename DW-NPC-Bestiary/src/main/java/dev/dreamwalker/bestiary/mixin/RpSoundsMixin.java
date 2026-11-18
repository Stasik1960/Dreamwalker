package dev.dreamwalker.bestiary.mixin;

import dev.dreamwalker.bestiary.ActorSpec;
import dev.dreamwalker.bestiary.Bestiary;
import dev.dreamwalker.bloodbornerp.content.RpSounds;
import net.minecraft.sound.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RpSounds.class, remap = false)
abstract class RpSoundsMixin {
    @Inject(method = "mob", at = @At("HEAD"), cancellable = true)
    private static void bestiarySound(String id, String event, SoundEvent fallback,
                                      CallbackInfoReturnable<SoundEvent> callback) {
        ActorSpec actor = ActorSpec.find(id);
        if (actor != null) {
            SoundEvent sound = Bestiary.sound(actor, event);
            callback.setReturnValue(sound != null ? sound : fallback);
        }
    }
}
