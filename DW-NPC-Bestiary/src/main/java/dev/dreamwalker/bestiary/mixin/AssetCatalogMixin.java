package dev.dreamwalker.bestiary.mixin;

import dev.dreamwalker.bestiary.ActorSpec;
import dev.dreamwalker.bloodbornerp.content.AssetCatalog;
import dev.dreamwalker.bloodbornerp.content.AssetSpec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AssetCatalog.class, remap = false)
abstract class AssetCatalogMixin {
    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private static void bestiaryAsset(String id, CallbackInfoReturnable<AssetSpec> callback) {
        ActorSpec actor = ActorSpec.find(id);
        if (actor != null) callback.setReturnValue(actor.asset());
    }
}
