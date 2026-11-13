package dev.dreamwalker.bloodbornedw.building;

import dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity;
import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;

/** Collects translucent composite faces only during world block entity rendering. */
public final class CompositeGlassPass {
    private static final ThreadLocal<CompositeGlassPass> GATHERING = new ThreadLocal<>();

    private VertexConsumerProvider.Immediate glass;
    private boolean queued;

    public static CompositeGlassPass suspend() {
        CompositeGlassPass previous = GATHERING.get();
        GATHERING.remove();
        return previous;
    }

    public void beginGathering() {
        GATHERING.set(this);
    }

    public void endGathering() {
        GATHERING.remove();
    }

    public void finish(CompositeGlassPass previous) {
        endGathering();
        if (previous != null) GATHERING.set(previous);
        if (queued) {
            // The frame never reached the translucent layer. Discard its pending vertices.
            glass = null;
            queued = false;
        }
    }

    public static VertexConsumerProvider ordered(BlockEntity entity, VertexConsumerProvider original) {
        CompositeGlassPass pass = GATHERING.get();
        if (pass == null || !(entity instanceof CompositeBlockEntity)
                || entity.getWorld() == null || entity.getWorld() != MinecraftClient.getInstance().world
                || !(entity.getCachedState().getBlock() instanceof CompositeRootBlock block)
                || !block.spec.translucent && !block.spec.id.getPath().equals("source_shutter_window")) {
            return original;
        }
        return layer -> {
            if (layer != RenderLayer.getTranslucent()) return original.getBuffer(layer);
            if (pass.glass == null) {
                pass.glass = VertexConsumerProvider.immediate(new BufferBuilder(layer.getExpectedBufferSize()));
            }
            pass.queued = true;
            return pass.glass.getBuffer(layer);
        };
    }

    public void drawAfterEntities() {
        if (!queued) return;
        MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers().draw();
        glass.draw();
        queued = false;
    }
}
