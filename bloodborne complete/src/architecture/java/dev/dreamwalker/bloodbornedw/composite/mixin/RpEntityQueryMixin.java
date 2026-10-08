package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import dev.dreamwalker.bloodbornerp.object.RpObjectIndex;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Source models with remote feet remain selectable outside their origin's native entity section. */
@Mixin(World.class)
public abstract class RpEntityQueryMixin {
    @Inject(method="getOtherEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Box;Ljava/util/function/Predicate;)Ljava/util/List;",at=@At("RETURN"),cancellable=true)
    private void dreamwalker$remoteObjects(Entity excluded,Box query,Predicate<? super Entity> predicate,CallbackInfoReturnable<List<Entity>> cir){
        var custom=RpObjectIndex.in((World)(Object)this,query);if(custom.isEmpty())return;
        List<Entity> result=null;var seen=new HashSet<>(cir.getReturnValue());
        for(RpObjectEntity object:custom)
            if(object!=excluded&&predicate.test(object)&&seen.add(object)){
                if(result==null)result=new ArrayList<>(cir.getReturnValue());result.add(object);
            }
        if(result!=null)cir.setReturnValue(result);
    }
    @Inject(method="getEntitiesByType(Lnet/minecraft/util/TypeFilter;Lnet/minecraft/util/math/Box;Ljava/util/function/Predicate;)Ljava/util/List;",at=@At("RETURN"),cancellable=true)
    private <T extends Entity> void dreamwalker$remoteTypedObjects(TypeFilter<Entity,T> filter,Box query,Predicate<? super T> predicate,CallbackInfoReturnable<List<T>> cir){
        var custom=RpObjectIndex.in((World)(Object)this,query);if(custom.isEmpty())return;
        List<T> result=null;var seen=new HashSet<>(cir.getReturnValue());
        for(RpObjectEntity object:custom){
            T matched=filter.downcast(object);if(matched!=null&&predicate.test(matched)&&seen.add(matched)){
                if(result==null)result=new ArrayList<>(cir.getReturnValue());result.add(matched);
            }
        }
        if(result!=null)cir.setReturnValue(result);
    }
}
