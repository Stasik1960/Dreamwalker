package dev.nightwatch.carriage.client.mixin;

import dev.nightwatch.carriage.CarriageEntity;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
abstract class PlayerEntityRendererMixin {
    @Unique private float nightwatchcarriage$previousBodyYaw;
    @Unique private float nightwatchcarriage$bodyYaw;

    @Inject(method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
        at = @At("HEAD"))
    private void nightwatchcarriage$faceCarriage(AbstractClientPlayerEntity player, float yaw, float delta,
        MatrixStack matrices, VertexConsumerProvider vertices, int light, CallbackInfo ci) {
        if (!(player.getVehicle() instanceof CarriageEntity carriage)) return;
        nightwatchcarriage$previousBodyYaw = player.prevBodyYaw;
        nightwatchcarriage$bodyYaw = player.bodyYaw;
        player.prevBodyYaw = carriage.prevYaw;
        player.bodyYaw = carriage.getYaw();
    }

    @Inject(method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
        at = @At("RETURN"))
    private void nightwatchcarriage$restoreHeading(AbstractClientPlayerEntity player, float yaw, float delta,
        MatrixStack matrices, VertexConsumerProvider vertices, int light, CallbackInfo ci) {
        if (!(player.getVehicle() instanceof CarriageEntity)) return;
        player.prevBodyYaw = nightwatchcarriage$previousBodyYaw;
        player.bodyYaw = nightwatchcarriage$bodyYaw;
    }
}
