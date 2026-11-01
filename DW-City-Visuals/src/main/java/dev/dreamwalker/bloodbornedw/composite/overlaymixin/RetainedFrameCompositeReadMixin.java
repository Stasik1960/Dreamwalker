package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.*;

import dev.dreamwalker.bloodbornedw.runtime.CellSnapshot;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FabricCompositeWorld.class, remap = false)
public abstract class RetainedFrameCompositeReadMixin {
    @Shadow @Final public ServerWorld world;
    @Inject(method = "read", at = @At("RETURN"), cancellable = true)
    private void dw$frameIsFree(Cell cell, CallbackInfoReturnable<CellSnapshot> cir) {
        CellSnapshot snapshot = cir.getReturnValue(); var pos = CompositeData.pos(cell);
        if (RetainedFramePlacement.active() && snapshot.kind() == CellSnapshot.Kind.FOREIGN
            && snapshot.resident() == null && snapshot.guests().isEmpty() && world.getBlockEntity(pos) == null
            && RetainedFrameState.matches(world.getBlockState(pos)))
            cir.setReturnValue(new CellSnapshot(CellSnapshot.Kind.AIR, snapshot.data(), null, java.util.List.of()));
    }
}
