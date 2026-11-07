package dev.dreamwalker.bloodbornedw.editor.mixin;

import dev.dreamwalker.bloodbornedw.architecture.mount.NativeMountClient;
import dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity;
import dev.dreamwalker.bloodbornedw.visual.CityFacadesClient;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Resolve the mounted model at its root without retaining a world-bound wrapper. */
@Mixin(value = NativeMountClient.class, remap = false)
public abstract class NativeMountPaletteMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_778;method_3367(Lnet/minecraft/class_4587$class_4665;Lnet/minecraft/class_4588;Lnet/minecraft/class_2680;Lnet/minecraft/class_1087;FFFII)V"), remap = false)
    private static void dw$palette(BlockModelRenderer renderer, MatrixStack.Entry entry, VertexConsumer consumer, BlockState state, BakedModel model, float red, float green, float blue, int light, int overlay, CompositeBlockEntity entity, MatrixStack matrices, VertexConsumerProvider consumers, int packedLight, int packedOverlay) {
        if (dev.dreamwalker.bloodbornedw.visual.CityVisualClient.isAlt() && entity.getWorld() != null && dev.dreamwalker.bloodbornedw.editor.EditorRenderScope.style(entity.getWorld(), entity.getPos()) >= 0) model = CityFacadesClient.visibleModel(model, entity.getPos(), state, entity.getWorld(), false);
        renderer.render(entry, consumer, state, dev.dreamwalker.bloodbornedw.tool.DebugModels.apply(model, entity.getPos(), state),
                red, green, blue, light, overlay);
    }
}
