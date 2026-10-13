package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.RemainingSourceTint;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.minecraft.client.world.BiomeColorCache;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.biome.ColorResolver;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Register only the authored NEW grass resolver, preserving all native keys
 * and cache objects. Native chunk/reset/reload lifecycle iterates this map. */
@Mixin(ClientWorld.class)
public abstract class RemainingSourceGrassCacheMixin {
    @Shadow @Final private Object2ObjectArrayMap<ColorResolver,BiomeColorCache> colorCache;

    @Inject(method="<init>",at=@At("TAIL"))
    private void bloodborne_dw$authoredGrassCache(CallbackInfo ci) {
        var resolver=RemainingSourceTint.grassResolver();
        var world=(ClientWorld)(Object)this;
        // The pinned native map is mutable. Append at construction, before
        // publication to mesh workers, without replacing its three built-ins.
        colorCache.put(resolver,new BiomeColorCache(pos->world.calculateColor(pos,resolver)));
    }
}
