package dev.dreamwalker.bloodbornedw.visual.mixin;

import dev.dreamwalker.bloodbornedw.visual.NativeSessionPublication;
import java.util.Map;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BakedModelManager.class)
abstract class NativeSessionApplyMixin {
    @Shadow private Map<Identifier,BakedModel> models;
    @Inject(method="upload",at=@At("RETURN"))
    private void dw$commit(CallbackInfo ci) { NativeSessionPublication.publish(models); }
}
