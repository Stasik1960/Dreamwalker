package dev.dreamwalker.bloodbornedw.building.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity;
import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Keeps translucent owner faces from writing depth while player and armor buffers are still queued. */
@Mixin(WorldRenderer.class)
abstract class CompositeGlassOrderMixin {
    @Unique
    private VertexConsumerProvider.Immediate dw$glass;
    @Unique
    private boolean dw$glassQueued;

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/render/block/entity/BlockEntityRenderDispatcher;render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V"))
    private void dw$queueCompositeGlass(BlockEntityRenderDispatcher dispatcher, BlockEntity entity, float tickDelta,
                                       MatrixStack matrices, VertexConsumerProvider consumers, Operation<Void> render) {
        if (!(entity instanceof CompositeBlockEntity)
                || !(entity.getCachedState().getBlock() instanceof CompositeRootBlock block)
                || !block.spec.translucent && !block.spec.id.getPath().equals("source_shutter_window")) {
            render.call(dispatcher, entity, tickDelta, matrices, consumers);
            return;
        }
        VertexConsumerProvider ordered = layer -> {
            if (layer != RenderLayer.getTranslucent()) return consumers.getBuffer(layer);
            if (dw$glass == null) {
                dw$glass = VertexConsumerProvider.immediate(new BufferBuilder(layer.getExpectedBufferSize()));
            }
            dw$glassQueued = true;
            return dw$glass.getBuffer(layer);
        };
        render.call(dispatcher, entity, tickDelta, matrices, ordered);
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/render/WorldRenderer;renderLayer(Lnet/minecraft/client/render/RenderLayer;Lnet/minecraft/client/util/math/MatrixStack;DDDLorg/joml/Matrix4f;)V"))
    private void dw$drawGlassAfterEntities(WorldRenderer renderer, RenderLayer layer, MatrixStack matrices,
                                          double x, double y, double z, Matrix4f projection, Operation<Void> render) {
        if (layer == RenderLayer.getTranslucent() && dw$glassQueued) {
            dw$glassQueued = false;
            var buffers = MinecraftClient.getInstance().getBufferBuilders();
            buffers.getEntityVertexConsumers().draw();
            dw$glass.draw();
        }
        render.call(renderer, layer, matrices, x, y, z, projection);
    }
}
