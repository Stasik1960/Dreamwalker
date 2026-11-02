package dev.dreamwalker.bloodbornedw.visual.mixin;

import dev.dreamwalker.bloodbornedw.visual.V12Palettes;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.ResourceTexture;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies the same one-time palette to non-atlas textures such as RP entities. */
@Mixin(ResourceTexture.class)
abstract class V12ResourceTexturePaletteMixin {
    @Shadow @Final protected Identifier location;

    @Inject(method = "loadTextureData", at = @At("RETURN"))
    private void dw$palette(ResourceManager resourceManager,
                            CallbackInfoReturnable<?> cir) {
        if (!location.getNamespace().equals("bloodborne_rp")
                || !location.getPath().equals("textures/entities/decorations/statue.png")) return;
        Object textureData = cir.getReturnValue();
        if (textureData == null) return;
        NativeImage image = ((V12TextureDataAccessor) (Object) textureData).dw$getImage();
        if (image != null) V12Palettes.apply(location, image);
    }
}
