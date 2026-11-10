package dev.dreamwalker.bloodbornedw.tool.mixin;

import dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity;
import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import dev.dreamwalker.bloodbornedw.editor.mixin.CompositeMeshRequestAccessor;
import dev.dreamwalker.bloodbornedw.tool.DebugAdjustments;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "dev.dreamwalker.bloodbornedw.composite.CompositeWorldMesh", remap = false)
abstract class CompositePoseMatrixMixin {
    @Redirect(method = "prepare", at = @At(value = "NEW", target = "net/minecraft/class_4587"))
    private static MatrixStack dw$preparedPose(@Coerce Object request) {
        var data = (CompositeMeshRequestAccessor) request;
        return dw$pose(data.dw$owner(), data.dw$state());
    }

    @Redirect(method = "bake", at = @At(value = "NEW", target = "net/minecraft/class_4587"))
    private static MatrixStack dw$bakedPose(MinecraftClient client, CompositeBlockEntity owner,
            CompositeRootBlock block, BlockState state, NbtCompound payload, int overlay,
            java.util.Map<Long, Long> dependencies) {
        return dw$pose(owner, state);
    }

    private static MatrixStack dw$pose(CompositeBlockEntity owner, BlockState state) {
        MatrixStack matrix = new MatrixStack();
        var world = owner.getWorld();
        int settings = world == null ? 0 : DebugAdjustments.renderPose(world, owner.getPos(), state);
        if (DebugAdjustments.hasPose(settings)) {
            matrix.translate(DebugAdjustments.x(settings) + 0.5, DebugAdjustments.y(settings), DebugAdjustments.z(settings) + 0.5);
            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-45F * DebugAdjustments.turns(settings)));
            matrix.translate(-0.5, 0, -0.5);
        }
        return matrix;
    }
}
