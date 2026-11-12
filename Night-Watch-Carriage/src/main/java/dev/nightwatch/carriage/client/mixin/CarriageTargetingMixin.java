package dev.nightwatch.carriage.client.mixin;

import dev.nightwatch.carriage.CarriageEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class CarriageTargetingMixin {
    @Shadow @Final private MinecraftClient client;

    @Inject(method = "updateTargetedEntity", at = @At("RETURN"))
    private void nightwatchcarriage$pickSeat(float tickDelta, CallbackInfo callback) {
        if (client.world == null || client.player == null || client.interactionManager == null ||
            client.getCameraEntity() != client.player || client.player.hasVehicle() || client.player.isSpectator()) return;

        double reach = client.interactionManager.hasExtendedReach() ? CarriageEntity.BOARDING_RANGE : 3.0;
        Vec3d start = client.player.getCameraPosVec(tickDelta);
        Vec3d end = start.add(client.player.getRotationVec(1).multiply(reach));
        HitResult vanilla = client.crosshairTarget;
        double vanillaDistance = vanilla == null || vanilla.getType() == HitResult.Type.MISS
            ? Double.POSITIVE_INFINITY : start.squaredDistanceTo(vanilla.getPos());

        CarriageEntity closest = null;
        Vec3d point = null;
        double distance = reach * reach;
        // The draw shafts extend past the carriage's collision box.
        for (CarriageEntity carriage : client.world.getEntitiesByClass(CarriageEntity.class,
            new Box(start, end).expand(3.15), entity -> !entity.isRemoved())) {
            if (carriage.squaredDistanceTo(client.player) > CarriageEntity.BOARDING_RANGE * CarriageEntity.BOARDING_RANGE) {
                continue;
            }
            CarriageEntity.BoardingHit hit = carriage.raycastBoarding(start, end, tickDelta);
            if (hit == null) continue;
            double hitDistance = start.squaredDistanceTo(hit.point());
            boolean ownHull = vanilla instanceof EntityHitResult entityHit && entityHit.getEntity() == carriage;
            if (hitDistance < distance && (ownHull || hitDistance < vanillaDistance)) {
                closest = carriage;
                point = hit.point();
                distance = hitDistance;
            }
        }
        if (closest == null || client.world.raycast(new RaycastContext(start, point, RaycastContext.ShapeType.OUTLINE,
            RaycastContext.FluidHandling.NONE, client.player)).getType() != HitResult.Type.MISS) return;

        // Report the same spot on the model using the pose the server uses for boarding.
        client.crosshairTarget = new EntityHitResult(closest, closest.boardingPacketPoint(point, tickDelta));
        client.targetedEntity = closest;
    }
}
