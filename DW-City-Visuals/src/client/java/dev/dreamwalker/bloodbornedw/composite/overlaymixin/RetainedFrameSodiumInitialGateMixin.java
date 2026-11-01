package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.*;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.chunk.ChunkSection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Do not mark a frame-only section empty when Sodium first attaches it. */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager", remap = false)
public abstract class RetainedFrameSodiumInitialGateMixin {
    @Shadow(remap = false) @Final private ClientWorld world;
    @Redirect(method = "onSectionAdded", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/class_2826;method_38292()Z", remap = false), remap = false)
    private boolean dw$frameSection(ChunkSection section, int sectionX, int sectionY, int sectionZ) {
        return section.isEmpty() && !RetainedFrames.hasSection(
            RetainedFrames.data(world.getChunk(sectionX, sectionZ)), sectionY);
    }
}


