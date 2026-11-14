package dev.dreamwalker.bloodbornedw.tool.mixin;

import dev.dreamwalker.bloodbornedw.tool.ActorPatrol;
import dev.dreamwalker.bloodbornerp.mob.RpMobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RpMobEntity.class, remap = false)
abstract class RpMobPatrolMixin implements ActorPatrol.Control {
    @Unique private static final String DW_PATROL = "DreamWalkerPatrol";
    @Unique private ActorPatrol dw$movement;
    @Unique private boolean dw$internalOrder;

    @Shadow private void stopOrder() { throw new AssertionError(); }
    @Shadow private int animationTicks;

    @Override public ActorPatrol dw$patrol() {
        if (dw$movement == null) dw$movement = new ActorPatrol();
        return dw$movement;
    }

    @Override public boolean dw$navigate(BlockPos ground) {
        dw$internalOrder = true;
        try {
            return dw$actor().orderTo(ground);
        } finally {
            dw$internalOrder = false;
        }
    }

    @Override public void dw$stopMoving() {
        RpMobEntity actor = dw$actor();
        stopOrder();
        actor.getMoveControl().moveTo(actor.getX(), actor.getY(), actor.getZ(), 0);
        actor.setMovementSpeed(0);
        actor.setForwardSpeed(0);
        actor.setSidewaysSpeed(0);
    }

    @Inject(method = "orderTo(Lnet/minecraft/class_2338;)Z", at = @At("HEAD"), cancellable = true)
    private void dw$manualOrder(BlockPos ground, CallbackInfoReturnable<Boolean> result) {
        if (dw$internalOrder) return;
        RpMobEntity actor = dw$actor();
        result.setReturnValue(!actor.getWorld().isClient && !actor.isCorpse() && actor.isAlive()
                && dw$patrol().manualOrder(actor, ground));
    }

    @Inject(method = "method_5773()V", at = @At("RETURN"))
    private void dw$movementTick(CallbackInfo ci) {
        RpMobEntity actor = dw$actor();
        if (!actor.getWorld().isClient && dw$movement != null && animationTicks <= 0) dw$movement.tick(actor);
    }

    @Inject(method = "method_5994()Lnet/minecraft/class_3414;", at = @At("HEAD"), cancellable = true)
    private void dw$suppressIdle(CallbackInfoReturnable<SoundEvent> result) {
        if (dw$movement == null || !dw$movement.idleSounds()) result.setReturnValue(null);
    }

    @Inject(method = "method_5652(Lnet/minecraft/class_2487;)V", at = @At("TAIL"))
    private void dw$savePatrol(NbtCompound root, CallbackInfo ci) {
        if (dw$movement == null) return;
        var state = new NbtCompound();
        dw$movement.writeNbt(state);
        if (!state.isEmpty()) root.put(DW_PATROL, state);
    }

    @Inject(method = "method_5749(Lnet/minecraft/class_2487;)V", at = @At("TAIL"))
    private void dw$loadPatrol(NbtCompound root, CallbackInfo ci) {
        if (root.contains(DW_PATROL) || dw$movement != null) dw$patrol().readNbt(root.getCompound(DW_PATROL));
        if (dw$movement != null && dw$movement.active()) dw$stopMoving();
    }

    @Unique private RpMobEntity dw$actor() { return (RpMobEntity) (Object) this; }
}
