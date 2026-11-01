package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity;
import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import dev.dreamwalker.bloodbornedw.composite.ShutterGlassLayers;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

@Mixin(targets = "dev.dreamwalker.bloodbornedw.composite.CompositeWorldMesh", remap = false)
public abstract class ShutterMeshLayerMixin {
    @WrapMethod(method = "render", remap = false)
    private static void dw$shutterGlass(CompositeBlockEntity entity, CompositeRootBlock block,
            BlockState state, NbtCompound payload, MatrixStack matrices, VertexConsumerProvider consumers,
            int overlay, Operation<Void> original) {
        VertexConsumerProvider routed = block.spec.id.getPath().equals("source_shutter_window")
            ? layer -> ShutterGlassLayers.consumer(consumers, layer, block) : consumers;
        original.call(entity, block, state, payload, matrices, routed, overlay);
    }
}
