package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.CompositeArchitecture;
import dev.dreamwalker.bloodbornedw.composite.CompositeItem;
import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import dev.dreamwalker.bloodbornedw.composite.DistinctForms;
import java.util.Map;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CompositeArchitecture.class)
abstract class DistinctFormRegistrationMixin {
    @Shadow @Final private static Map<String, CompositeRootBlock> BLOCKS;
    @Shadow @Final private static Map<String, CompositeItem> ITEMS;

    @Inject(method = "initialize", at = @At(value = "INVOKE", target = "Ljava/util/Map;values()Ljava/util/Collection;"), remap = false)
    private static void dreamwalker$registerForms(String[] paths, CallbackInfo ci) {
        // Run before the composite block entity type captures its supported blocks.
        DistinctForms.register(BLOCKS, ITEMS);
    }
}
