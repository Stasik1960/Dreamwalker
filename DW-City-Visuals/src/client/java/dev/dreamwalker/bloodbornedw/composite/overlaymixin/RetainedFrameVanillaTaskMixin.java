package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.*;

import com.llamalad7.mixinextras.sugar.Local;
import java.util.Set;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.chunk.BlockBufferBuilderStorage;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.class_846$class_851$class_4578", remap = false)
public abstract class RetainedFrameVanillaTaskMixin {
    @Inject(method = "method_22785", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/class_2338;method_10097(Lnet/minecraft/class_2338;Lnet/minecraft/class_2338;)Ljava/lang/Iterable;", ordinal = 0, remap = false), remap = false)
    private void dw$renderFrames(float x, float y, float z, BlockBufferBuilderStorage storage, CallbackInfoReturnable<?> ci,
        @Local(ordinal = 0) BlockPos origin, @Local ChunkRendererRegion region, @Local MatrixStack matrices,
        @Local Set<RenderLayer> layers, @Local Random random, @Local BlockRenderManager manager) {
        RetainedFrameVanillaEmitter.render(origin, region, matrices, layers, random, manager, storage);
    }
}


