package dev.dreamwalker.bloodbornedw.tool.mixin;

import dev.dreamwalker.bloodbornedw.tool.DebugToolModes;
import dev.dreamwalker.bloodbornerp.mob.RpMobEntity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = RpMobEntity.class, remap = false)
abstract class RpMobDebugRotationMixin implements DebugToolModes.ActorRotation {
    @Shadow private void stopOrder() { throw new AssertionError(); }

    @Override public void dw$rotate(boolean clockwise) {
        var actor = (RpMobEntity)(Object)this;
        float yaw = MathHelper.wrapDegrees(actor.getYaw() + (clockwise ? 45 : -45));
        actor.setYaw(yaw);
        actor.setBodyYaw(yaw);
        actor.setHeadYaw(yaw);
        actor.prevYaw = yaw;
        actor.prevBodyYaw = yaw;
        actor.prevHeadYaw = yaw;
        stopOrder();
    }
}
