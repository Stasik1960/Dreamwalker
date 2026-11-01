package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.CompositeClient;
import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import dev.dreamwalker.bloodbornedw.composite.ShutterGlassLayers;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

@Mixin(value = CompositeClient.class, remap = false)
public abstract class ShutterItemLayerMixin {
    @WrapMethod(method = "renderParts(Ldev/dreamwalker/bloodbornedw/composite/CompositeRootBlock;Lnet/minecraft/class_2680;Lnet/minecraft/class_4587;Lnet/minecraft/class_4597;IILjava/lang/String;Lnet/minecraft/class_2338;Lnet/minecraft/class_2487;)V", remap = false)
    private static void dw$shutterItemGlass(CompositeRootBlock block, BlockState state,
            MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay, String instanceId,
            BlockPos root, NbtCompound payload, Operation<Void> original) {
        VertexConsumerProvider routed = block.spec.id.getPath().equals("source_shutter_window")
            ? layer -> ShutterGlassLayers.consumer(consumers, layer, block) : consumers;
        original.call(block, state, matrices, routed, light, overlay, instanceId, root, payload);
    }
}
