package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import dev.dreamwalker.bloodbornedw.architecture.PlacementPhysics;
import java.util.Optional;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Climbing uses the functional RP ladder strip independently of its solid rails. */
@Mixin(LivingEntity.class)
public abstract class RpClimbingMixin {
    @Shadow private Optional<BlockPos> climbingPos;
    @Inject(method="isClimbing",at=@At("RETURN"),cancellable=true)
    private void dreamwalker$climb(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity mover=(LivingEntity)(Object)this;
        if(mover.isSpectator())return;
        var nativeState=mover.getWorld().getBlockState(mover.getBlockPos());
        if(cir.getReturnValue()&&nativeState.getBlock() instanceof dev.dreamwalker.bloodbornedw.architecture.PrototypeLadderBlock&&dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.offset(mover.getWorld(),mover.getBlockPos())!=0){cir.setReturnValue(false);climbingPos=Optional.empty();}
        var mounted=dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.climbing(mover.getWorld(),mover.getBoundingBox());
        if(mounted.isPresent()){climbingPos=mounted;cir.setReturnValue(true);return;}
        if(cir.getReturnValue())return;
        Box body=mover.getBoundingBox();
        for(RpObjectEntity object:dev.dreamwalker.bloodbornerp.object.RpObjectIndex.in(mover.getWorld(),body))
            if(object.assetId().equals("ladder"))
            for(Box zone:object.climbingBoxes())if(PlacementPhysics.overlaps(body,zone)) {
                climbingPos=Optional.of(mover.getBlockPos());cir.setReturnValue(true);return;
            }
    }
}
