package dev.dreamwalker.bloodbornedw.tool.mixin;

import dev.dreamwalker.bloodbornedw.tool.DebugAdjustments;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Apply the pose outside the source-part rotations, in root/world axes. */
@Mixin(targets = "dev.dreamwalker.bloodbornedw.composite.StaticArchitectureClient", remap = false)
abstract class StaticPoseMatrixMixin {
    @Redirect(method = "renderSource", at = @At(value = "NEW", target = "net/minecraft/class_4587", ordinal = 0))
    private static MatrixStack dw$worldPose(BlockModelRenderer renderer, BlockRenderView world, BlockPos root,
            MatrixStack outer, VertexConsumer vertices, int overlay, @Coerce Object block, BlockState state,
            @Coerce Object shift, double mountY, java.util.List<?> parts, @Coerce Object pose,
            @Coerce Object owner, boolean wholeSourceOptics) {
        MatrixStack matrix = new MatrixStack();
        var clientWorld = net.minecraft.client.MinecraftClient.getInstance().world;
        int settings = clientWorld == null ? 0 : DebugAdjustments.renderPose(clientWorld, root, state);
        if (DebugAdjustments.hasPose(settings)) {
            matrix.translate(DebugAdjustments.x(settings) + 0.5, DebugAdjustments.y(settings), DebugAdjustments.z(settings) + 0.5);
            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-45F * DebugAdjustments.turns(settings)));
            matrix.translate(-0.5, 0, -0.5);
        }
        return matrix;
    }
}
