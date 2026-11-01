package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.*;

import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.world.WorldSlice", remap = false)
public abstract class RetainedFrameSodiumGateMixin {
    @Redirect(method = "prepare", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/class_2826;method_38292()Z", remap = false), remap = false)
    private static boolean dw$frameSection(ChunkSection section, World world, ChunkSectionPos pos, @Coerce Object cache) {
        return section.isEmpty() && !RetainedFrames.hasSection(
            RetainedFrames.data(world.getChunk(pos.getSectionX(), pos.getSectionZ())), pos.getSectionY());
    }
}


