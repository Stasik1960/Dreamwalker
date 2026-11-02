package dev.dreamwalker.bloodbornedw.visual.mixin;

import net.minecraft.client.texture.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Remapped access to ResourceTexture.TextureData's image without reflection. */
@Mixin(targets = "net.minecraft.client.texture.ResourceTexture$TextureData")
public interface V12TextureDataAccessor {
    @Accessor("image")
    NativeImage dw$getImage();
}
