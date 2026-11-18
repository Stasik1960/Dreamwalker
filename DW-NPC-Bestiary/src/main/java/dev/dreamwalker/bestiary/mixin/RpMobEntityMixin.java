package dev.dreamwalker.bestiary.mixin;

import dev.dreamwalker.bestiary.ActorSpec;
import dev.dreamwalker.bestiary.Bestiary;
import dev.dreamwalker.bloodbornerp.mob.RpMobEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RpMobEntity.class)
abstract class RpMobEntityMixin {
    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void keepBestiaryActorPersistent(NbtCompound nbt, CallbackInfo callback) {
        RpMobEntity actor = (RpMobEntity) (Object) this;
        // Reading partial summon NBT can clear the flag set by the constructor.
        if (ActorSpec.find(actor.assetId()) != null) actor.setPersistent();
    }

    @Inject(method = "playToolAttack", at = @At("RETURN"), remap = false)
    private void playBestiaryAttackSound(CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValueZ()) return;
        RpMobEntity actor = (RpMobEntity) (Object) this;
        ActorSpec spec = ActorSpec.find(actor.assetId());
        if (spec != null) actor.playSound(Bestiary.sound(spec, "attack"), 0.75f, 1);
    }
}
