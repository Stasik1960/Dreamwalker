package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.architecture.RpCollisionShapes;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.EntityView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** RP selection/culling bounds do not become whole-volume player obstacles. */
@Mixin(EntityView.class)
public interface RpPhysicalCollisionMixin {
    @Inject(method="getEntityCollisions",at=@At("RETURN"),cancellable=true)
    private void dreamwalker$physical(Entity mover,Box query,CallbackInfoReturnable<List<VoxelShape>> cir) {
        List<VoxelShape> before=cir.getReturnValue();
        List<VoxelShape> after=RpCollisionShapes.append((EntityView)this,mover,query,before);
        if(after!=before)cir.setReturnValue(after);
    }
}
