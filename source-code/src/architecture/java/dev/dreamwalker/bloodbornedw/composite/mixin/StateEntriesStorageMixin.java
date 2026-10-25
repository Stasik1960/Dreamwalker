package dev.dreamwalker.bloodbornedw.composite.mixin;

import com.google.common.collect.ImmutableMap;
import dev.dreamwalker.bloodbornedw.composite.StateEntriesAccess;
import net.minecraft.state.State;
import net.minecraft.state.property.Property;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

/** Keeps Minecraft's declared ImmutableMap field and immutable maps. */
@Mixin(State.class)
public abstract class StateEntriesStorageMixin implements StateEntriesAccess {
    @Shadow @Final @Mutable private ImmutableMap<Property<?>,Comparable<?>> entries;
    @Override public void bloodborne_dw$sharedEntries(ImmutableMap<Property<?>,Comparable<?>> shared){entries=shared;}
}
