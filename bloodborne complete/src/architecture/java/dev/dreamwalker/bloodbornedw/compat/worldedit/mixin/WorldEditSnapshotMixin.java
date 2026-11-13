package dev.dreamwalker.bloodbornedw.compat.worldedit.mixin;

import dev.dreamwalker.bloodbornedw.compat.worldedit.WorldEditBridge;
import dev.dreamwalker.bloodbornedw.compat.worldedit.WorldEditNativeWrites;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import java.util.List;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=WorldEditBridge.class,remap=false)
public abstract class WorldEditSnapshotMixin {
    @Inject(method="nativeSettings",at=@At("HEAD"),cancellable=true,remap=false)
    private static void dreamwalker$includeCityHistory(ServerWorld world,BlockPos pos,CallbackInfoReturnable<Boolean> callback){
        if(ForcedBlockRemoval.nativeBridgeVisual(world,pos)>=0||ForcedBlockRemoval.nativeVisual(world,pos)>=0
                ||ForcedBlockRemoval.retainedFrame(world,pos)!=null)
            callback.setReturnValue(true);
    }

    @Inject(method="owners",at=@At("RETURN"),cancellable=true)
    private static void dreamwalker$includeNativeResident(ServerWorld world,BlockPos pos,CallbackInfoReturnable<List<Owner>> callback){
        if(world.getBlockEntity(pos) instanceof CompositeBlockEntity be&&be.resident()!=null
                &&!callback.getReturnValue().contains(be.resident()))
            callback.setReturnValue(ForcedBlockRemoval.ownersAt(world,pos));
    }

    @Inject(method={
        "snapshot(Lnet/minecraft/class_3218;Lnet/minecraft/class_2338;Ljava/util/List;Ljava/util/List;)Lnet/minecraft/class_2487;",
        "snapshot(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/BlockPos;Ljava/util/List;Ljava/util/List;)Lnet/minecraft/nbt/NbtCompound;"
    },at=@At("RETURN"),remap=false)
    private static void dreamwalker$capturePhysicalCarrier(ServerWorld world,BlockPos pos,List<Owner> owners,
            List<RpObjectEntity> entities,CallbackInfoReturnable<NbtCompound> callback){
        var snapshot=callback.getReturnValue();
        int bridge=ForcedBlockRemoval.nativeBridgeVisual(world,pos);
        if(bridge>=0)snapshot.putInt(WorldEditNativeWrites.BRIDGE_VISUAL,bridge);
        var frame=ForcedBlockRemoval.retainedFrame(world,pos);
        if(frame!=null)snapshot.put(WorldEditNativeWrites.RETAINED_FRAME,
                CompositeData.data(CompositeData.data(frame,new NbtCompound())));
        if(snapshot.contains("NativeData"))return;
        var state=world.getBlockState(pos);
        int nativeVisual=ForcedBlockRemoval.nativeVisual(world,pos);
        if(nativeVisual>=0&&state.equals(ForcedBlockRemoval.nativeBridgeState(nativeVisual))){
            NbtCompound payload=new NbtCompound();
            payload.putInt("NativeVisualIndex",nativeVisual);
            snapshot.put("NativeData",CompositeData.data(CompositeData.data(state,payload)));
            return;
        }
        if(owners.isEmpty())return;
        var be=world.getBlockEntity(pos);
        if(state.isAir()||state.isOf(CompositeArchitecture.CELL)
                ||be instanceof CompositeBlockEntity composite&&composite.resident()!=null)return;
        snapshot.put("NativeData",CompositeData.data(CompositeData.data(state,be==null?new NbtCompound():be.createNbt())));
    }
}
