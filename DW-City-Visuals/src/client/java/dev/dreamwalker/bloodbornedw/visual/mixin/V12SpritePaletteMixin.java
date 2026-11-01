package dev.dreamwalker.bloodbornedw.visual.mixin;

import dev.dreamwalker.bloodbornedw.visual.V12Palettes;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpriteContents.class)
abstract class V12SpritePaletteMixin {
    @Shadow @Final private Identifier id;
    @Shadow @Final private NativeImage image;
    @Inject(method = "<init>", at = @At("RETURN"))
    private void dw$palette(CallbackInfo ci) { V12Palettes.apply(id, image); }
}
