package dev.dreamwalker.bloodbornedw.composite;

import com.google.common.collect.ImmutableMap;
import net.minecraft.state.property.Property;

/** Used only by the exact own-registry startup storage pass. */
public interface StateEntriesAccess {
    void bloodborne_dw$sharedEntries(ImmutableMap<Property<?>,Comparable<?>> entries);
}
