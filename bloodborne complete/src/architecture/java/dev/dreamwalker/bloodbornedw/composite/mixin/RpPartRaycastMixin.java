package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import dev.dreamwalker.bloodbornerp.object.RpObjectSelection;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep native/living targeting, substituting source-part tests only for player's RP candidates. */
@Mixin(ProjectileUtil.class)
public abstract class RpPartRaycastMixin {
    private static final String RAY="raycast(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Box;Ljava/util/function/Predicate;D)Lnet/minecraft/util/hit/EntityHitResult;";
    @Redirect(method=RAY,at=@At(value="INVOKE",target="Lnet/minecraft/world/World;getOtherEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Box;Ljava/util/function/Predicate;)Ljava/util/List;"))
    private static List<Entity> dreamwalker$nativeCandidates(World world,Entity viewer,Box query,Predicate<? super Entity> predicate){
        return world.getOtherEntities(viewer,query,viewer instanceof PlayerEntity?e->!(e instanceof RpObjectEntity)&&predicate.test(e):predicate);
    }
    @Inject(method=RAY,at=@At("RETURN"),cancellable=true)
    private static void dreamwalker$sourceParts(Entity viewer,Vec3d start,Vec3d end,Box query,Predicate<Entity> predicate,double maximumSquared,CallbackInfoReturnable<EntityHitResult> cir){
        if(viewer instanceof PlayerEntity)cir.setReturnValue(RpObjectSelection.raycast(viewer,start,end,query,predicate,maximumSquared,cir.getReturnValue()));
    }
}
