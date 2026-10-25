package dev.dreamwalker.bloodbornedw.composite.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Lithium 0.11.4's codec copy uses global registry bits to size temporary
 * local palettes. A section contains at most 4096 entries, independently of
 * the global ID width. Change only the initial capacity: Lithium's existing
 * array/map growth and indexBits/resize semantics remain intact. No cache. */
@Pseudo
@Mixin(targets="me.jellysquid.mods.lithium.common.world.chunk.LithiumHashPalette",remap=false)
public abstract class LithiumPaletteAllocationMixin {
    // Pinned 1.20.1 intermediary descriptor. Other constructors delegate to
    // this allocator or copy already allocated storage. Slot 4 is capacity,
    // not the independent indexBits constructor argument in slot 2.
    @ModifyVariable(method="<init>(Lnet/minecraft/class_2359;ILnet/minecraft/class_2835;)V",at=@At("STORE"),index=4,remap=false,require=1)
    private int bloodborne_dw$sectionCapacity(int capacity){return Math.min(capacity,4096);}
}
