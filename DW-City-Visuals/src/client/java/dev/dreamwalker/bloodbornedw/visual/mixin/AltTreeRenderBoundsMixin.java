package dev.dreamwalker.bloodbornedw.visual.mixin;

import dev.dreamwalker.bloodbornedw.visual.CityVisualClient;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import net.minecraft.client.render.Frustum;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Extend only ALT tree render culling to the uploaded project's finite envelope.
 * The entity's server-derived movement and selection geometry is unchanged. */
@Mixin(targets = "dev.dreamwalker.bloodbornerp.client.CatalogObjectRenderer", remap = false)
abstract class AltTreeRenderBoundsMixin {
    // Baseline mod methods are unmapped; this descriptor selects its typed overload
    // rather than the compiler-generated Entity bridge, using intermediary Frustum.
    @Inject(method = "shouldRender(Ldev/dreamwalker/bloodbornerp/object/RpObjectEntity;Lnet/minecraft/class_4604;DDD)Z",
            at = @At("RETURN"), cancellable = true, remap = false)
    private void dw$altTreeEnvelope(RpObjectEntity entity, Frustum frustum, double x, double y, double z,
                                   CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() || !CityVisualClient.isAlt()) return;
        String id = entity.assetId();
        if (!(id.equals("tree1") || id.equals("tree2") || id.equals("tree3")) || !entity.shouldRender(x, y, z)) return;
        // All three uploaded geometries declare width=32, height=30, offset=(0,12,0).
        double scale = entity.asset().scale() * entity.objectScale();
        if (!Double.isFinite(scale) || scale <= 0) return;
        double pitch = Math.toRadians(entity.getPitch()), pc = Math.cos(pitch), ps = Math.sin(pitch);
        double yaw = Math.toRadians(entity.getYaw() - 90), yc = Math.cos(yaw), ys = Math.sin(yaw);
        Box envelope = null;
        for (int corner = 0; corner < 8; corner++) {
            double vx = ((corner & 1) == 0 ? -16 : 16) * scale;
            double vy = ((corner & 2) == 0 ? -3 : 27) * scale;
            double vz = ((corner & 4) == 0 ? -16 : 16) * scale;
            double px = vx * pc - vy * ps, py = vx * ps + vy * pc;
            Vec3d point = new Vec3d(px * yc + vz * ys, py, -px * ys + vz * yc).add(entity.getPos());
            Box cornerBox = new Box(point, point);
            envelope = envelope == null ? cornerBox : envelope.union(cornerBox);
        }
        cir.setReturnValue(entity.ignoreCameraFrustum || frustum.isVisible(envelope.union(entity.getVisibilityBoundingBox()).expand(.5)));
    }
}
