package dev.dreamwalker.bloodbornedw.visual.mixin;

import java.util.Arrays;
import dev.dreamwalker.bloodbornedw.visual.CityVisualPackProvider;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(MinecraftClient.class)
public abstract class VisualPackProviderMixin {
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/resource/ResourcePackManager;<init>([Lnet/minecraft/resource/ResourcePackProvider;)V"), index = 0)
    private ResourcePackProvider[] dwCityVisualsProvider(ResourcePackProvider[] providers) {
        ResourcePackProvider[] result = Arrays.copyOf(providers, providers.length + 1);
        result[providers.length] = new CityVisualPackProvider(); return result;
    }
}
