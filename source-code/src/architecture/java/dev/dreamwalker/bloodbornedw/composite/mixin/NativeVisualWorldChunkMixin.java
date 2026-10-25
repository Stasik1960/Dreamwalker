package dev.dreamwalker.bloodbornedw.composite.mixin;
import dev.dreamwalker.bloodbornedw.composite.*;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(WorldChunk.class)
public abstract class NativeVisualWorldChunkMixin {
    @Inject(method="<init>(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/world/chunk/ProtoChunk;Lnet/minecraft/world/chunk/WorldChunk$EntityLoader;)V",at=@At("RETURN"))
    private void dw$promoteVisuals(ServerWorld world,ProtoChunk proto,WorldChunk.EntityLoader loader,CallbackInfo ci){NativeVisuals.data((WorldChunk)(Object)this,NativeVisuals.data(proto));dev.dreamwalker.bloodbornedw.architecture.FauxWaterlogging.data((WorldChunk)(Object)this,dev.dreamwalker.bloodbornedw.architecture.FauxWaterlogging.data(proto));}
    @Inject(method="setBlockState",at=@At("RETURN"))
    private void dw$changedVisuals(BlockPos pos,BlockState state,boolean moved,CallbackInfoReturnable<BlockState> ci){if(ci.getReturnValue()!=null){NativeVisuals.reconcile((WorldChunk)(Object)this,pos,state);dev.dreamwalker.bloodbornedw.architecture.FauxWaterlogging.changed((WorldChunk)(Object)this,pos,ci.getReturnValue(),state);}}
}
