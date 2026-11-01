package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.*;

import java.util.Map;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Footprint;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Standing physics use the canonical spec; hanging remains on the v22 repair. */
@Mixin(value = CompositeSourceShift.class, priority = 2000, remap = false)
public abstract class StandardLanternStandingFootprintMixin {
    @Invoker("physicalFootprint")
    private static Map<Cell, Footprint> dw$physical(CompositeSpec spec, BlockState state, NbtCompound payload) { throw new AssertionError(); }
    @Inject(method = "footprint", at = @At("HEAD"), cancellable = true)
    private static void dw$standing(CompositeSpec spec, BlockState state, NbtCompound payload, CallbackInfoReturnable<Map<Cell, Footprint>> cir) {
        if (spec.id.toString().equals("bloodborne_dw:source_standard_lantern") && state.get(CompositeRootBlock.VARIANT) == 1) cir.setReturnValue(dw$physical(spec, state, payload));
    }
}
