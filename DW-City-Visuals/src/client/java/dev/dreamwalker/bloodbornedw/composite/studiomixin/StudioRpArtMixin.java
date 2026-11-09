package dev.dreamwalker.bloodbornedw.composite.studiomixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.dreamwalker.bloodbornedw.composite.StudioRpGeometry;
import dev.dreamwalker.bloodbornerp.client.CatalogObjectRenderer;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = CatalogObjectRenderer.class, remap = false)
public abstract class StudioRpArtMixin {
    @WrapMethod(method = "render(Ldev/dreamwalker/bloodbornerp/object/RpObjectEntity;FFLnet/minecraft/class_4587;Lnet/minecraft/class_4597;I)V")
    private void dw$shiftRenderedObject(RpObjectEntity object, float yaw, float delta, MatrixStack matrices,
            VertexConsumerProvider consumers, int light, Operation<Void> original) {
        var shift = StudioRpGeometry.renderOffset(object, MathHelper.lerpAngleDegrees(delta, object.prevYaw, object.getYaw()),
                MathHelper.lerp(delta, object.prevPitch, object.getPitch()));
        if (shift.equals(Vec3d.ZERO)) {
            original.call(object, yaw, delta, matrices, consumers, light);
            return;
        }
        matrices.push();
        try {
            matrices.translate(shift.x, shift.y, shift.z);
            original.call(object, yaw, delta, matrices, consumers, light);
        } finally {
            matrices.pop();
        }
    }
}
