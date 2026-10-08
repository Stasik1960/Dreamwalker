package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import net.minecraft.entity.Entity;
import net.minecraft.world.EntityView;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Native placement ignores RP only; living/vehicle tests and native block permissions remain native. */
@Mixin(EntityView.class)
public interface RpBlockPlacementCollisionMixin {
    @Redirect(method="doesNotIntersectEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/shape/VoxelShape;)Z",at=@At(value="FIELD",target="Lnet/minecraft/entity/Entity;intersectionChecked:Z",opcode=Opcodes.GETFIELD))
    private boolean dreamwalker$rpNotPlacementObstacle(Entity entity){return !(entity instanceof RpObjectEntity)&&entity.intersectionChecked;}
}
