package dev.dreamwalker.bloodbornedw.tool.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.dreamwalker.bloodbornedw.tool.DebugAdjustments;
import dev.dreamwalker.bloodbornerp.client.CatalogObjectRenderer;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CatalogObjectRenderer.class, remap = false)
abstract class RpDebugRenderMixin {
    @WrapMethod(method = "render(Ldev/dreamwalker/bloodbornerp/object/RpObjectEntity;FFLnet/minecraft/class_4587;Lnet/minecraft/class_4597;I)V")
    private void dw$renderPose(RpObjectEntity object, float yaw, float tickDelta, MatrixStack matrices,
            VertexConsumerProvider consumers, int light, Operation<Void> original) {
        int pose = DebugAdjustments.objectSettings(object);
        if (!DebugAdjustments.hasPose(pose)) {
            original.call(object, yaw, tickDelta, matrices, consumers, light);
            return;
        }
        matrices.push();
        try {
            matrices.translate(DebugAdjustments.x(pose), DebugAdjustments.y(pose), DebugAdjustments.z(pose));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-45F * DebugAdjustments.turns(pose)));
            original.call(object, yaw, tickDelta, matrices, consumers, light);
        } finally {
            matrices.pop();
        }
    }

    @Inject(method = "shouldRender(Ldev/dreamwalker/bloodbornerp/object/RpObjectEntity;Lnet/minecraft/class_4604;DDD)Z",
            at = @At("RETURN"), cancellable = true)
    private void dw$cullMovedModel(RpObjectEntity object, Frustum frustum, double cameraX, double cameraY,
            double cameraZ, CallbackInfoReturnable<Boolean> cir) {
        int pose = DebugAdjustments.objectSettings(object);
        if (!cir.getReturnValue() && DebugAdjustments.hasPose(pose)
                && object.squaredDistanceTo(cameraX, cameraY, cameraZ) < 128 * 128
                && frustum.isVisible(object.visualBounds().expand(DebugAdjustments.turns(pose) == 0 ? 0 : 2)
                        .offset(DebugAdjustments.x(pose), DebugAdjustments.y(pose), DebugAdjustments.z(pose)))) {
            cir.setReturnValue(true);
        }
    }
}
