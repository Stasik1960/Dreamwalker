package dev.dreamwalker.bloodbornedw.compat.worldedit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.dreamwalker.bloodbornedw.compat.worldedit.WorldEditObjectTransactions;
import dev.dreamwalker.bloodbornedw.compat.worldedit.WorldEditNativeWrites;
import dev.dreamwalker.bloodbornedw.runtime.CellSnapshot.BlockData;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import java.util.List;
import java.util.Map;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value=WorldEditObjectTransactions.class,remap=false)
public abstract class WorldEditNativeWriteMixin {
    @WrapMethod(method={
        "replace(Lnet/minecraft/class_3218;Ljava/util/List;Ljava/util/List;Ljava/util/Map;)Ldev/dreamwalker/bloodbornedw/runtime/TransactionCore\u0024Result;",
        "replace(Lnet/minecraft/server/world/ServerWorld;Ljava/util/List;Ljava/util/List;Ljava/util/Map;)Ldev/dreamwalker/bloodbornedw/runtime/TransactionCore\u0024Result;"
    },remap=false)
    private static TransactionCore.Result dreamwalker$explicitNativeWrites(ServerWorld world,List<ObjectInstance.Owner> remove,
            List<ObjectInstance> add,Map<BlockPos,BlockData> nativeChanges,Operation<TransactionCore.Result> original){
        var previous=WorldEditNativeWrites.EXPLICIT.get();
        WorldEditNativeWrites.EXPLICIT.set(nativeChanges);
        try{return original.call(world,remove,add,nativeChanges);}
        finally{
            if(previous==null)WorldEditNativeWrites.EXPLICIT.remove();
            else WorldEditNativeWrites.EXPLICIT.set(previous);
        }
    }
}
