package dev.dreamwalker.bloodbornedw.compat.worldedit.mixin;

import dev.dreamwalker.bloodbornedw.compat.worldedit.WorldEditNativeWrites;
import dev.dreamwalker.bloodbornedw.composite.CompositeData;
import dev.dreamwalker.bloodbornedw.composite.FabricCompositeWorld;
import dev.dreamwalker.bloodbornedw.runtime.CellSnapshot;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import net.minecraft.block.Block;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=FabricCompositeWorld.class,remap=false)
public abstract class WorldEditNativeCarrierMixin {
    @Shadow @Final public ServerWorld world;

    @Inject(method="writeSilently",at=@At("HEAD"))
    private void dreamwalker$restorePhysicalCarrier(Cell cell,CellSnapshot next,CallbackInfoReturnable<Boolean> callback){
        if(next.kind()!=CellSnapshot.Kind.FOREIGN)return;
        var nativeWrites=WorldEditNativeWrites.EXPLICIT.get();
        var pos=CompositeData.pos(cell);
        if(nativeWrites==null||!next.data().equals(nativeWrites.get(pos)))return;
        var state=CompositeData.state(next.data());
        if(!world.getBlockState(pos).equals(state))world.setBlockState(pos,state,Block.FORCE_STATE|Block.SKIP_DROPS);
        var payload=CompositeData.nbt(next.data().blockEntityNbt());
        var be=world.getBlockEntity(pos);
        if(be!=null&&!payload.isEmpty()&&!payload.equals(be.createNbt())){
            be.readNbt(payload);
            be.markDirty();
        }
    }
}
