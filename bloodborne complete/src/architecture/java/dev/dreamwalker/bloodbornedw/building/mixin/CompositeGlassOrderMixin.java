package dev.dreamwalker.bloodbornedw.building.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.dreamwalker.bloodbornedw.building.CompositeGlassPass;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps translucent owner faces from writing depth while player and armor buffers are still queued. */
@Mixin(WorldRenderer.class)
abstract class CompositeGlassOrderMixin {
    @Unique
    private final CompositeGlassPass dw$glass = new CompositeGlassPass();

    @WrapMethod(method = "render")
    private void dw$finishGlassFrame(MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline,
                                     Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmap,
                                     Matrix4f projection, Operation<Void> render) {
        CompositeGlassPass previous = CompositeGlassPass.suspend();
        try {
            render.call(matrices, tickDelta, limitTime, renderBlockOutline, camera, gameRenderer, lightmap, projection);
        } finally {
            dw$glass.finish(previous);
        }
    }

    // Sodium 0.5 invokes the dispatcher between these same world-renderer phases.
    @Inject(method = "render", at = @At(value = "CONSTANT", args = "stringValue=blockentities"))
    private void dw$beginGlassGathering(CallbackInfo ci) {
        dw$glass.beginGathering();
    }

    @Inject(method = "render", at = @At(value = "CONSTANT", args = "stringValue=destroyProgress"))
    private void dw$endGlassGathering(CallbackInfo ci) {
        dw$glass.endGathering();
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/render/WorldRenderer;renderLayer(Lnet/minecraft/client/render/RenderLayer;Lnet/minecraft/client/util/math/MatrixStack;DDDLorg/joml/Matrix4f;)V"))
    private void dw$drawGlassAfterEntities(WorldRenderer renderer, RenderLayer layer, MatrixStack matrices,
                                          double x, double y, double z, Matrix4f projection, Operation<Void> render) {
        if (layer == RenderLayer.getTranslucent()) dw$glass.drawAfterEntities();
        render.call(renderer, layer, matrices, x, y, z, projection);
    }
}
