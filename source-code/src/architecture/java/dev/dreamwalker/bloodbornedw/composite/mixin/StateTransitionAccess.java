package dev.dreamwalker.bloodbornedw.composite.mixin;

import com.google.common.collect.Table;
import net.minecraft.state.State;
import net.minecraft.state.property.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Storage-only field access. The setter is used only by the exact own-registry
 * post-registration pass, never by gameplay or foreign namespace blocks. */
@Mixin(State.class)
public interface StateTransitionAccess {
    @Accessor("withTable") Table<Property<?>,Comparable<?>,?> bloodborne_dw$transitions();
    @Accessor("withTable") void bloodborne_dw$transitions(Table<Property<?>,Comparable<?>,?> table);
}
