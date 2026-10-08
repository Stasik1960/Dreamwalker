package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.architecture.RpCollisionShapes;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Lithium can defer the World entity query; the engine's shared shape list still exists. */
@Mixin(Entity.class)
public abstract class RpMovementCollisionMixin {
    @ModifyVariable(method = "adjustMovementForCollisions(Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/Vec3d;",
                    at = @At("STORE"), ordinal = 0)
    private List<VoxelShape> dreamwalker$workingShapes(List<VoxelShape> original, Vec3d movement) {
        return RpCollisionShapes.forMovement((Entity)(Object)this, movement, original);
    }
}
