package daot.compat.components;

import net.minecraft.nbt.NbtCompound;

public final class DataComponentTypes {
    public static final ComponentType<NbtComponent> CUSTOM_DATA = ComponentType.<NbtComponent>builder()
            .codec(NbtCompound.CODEC.xmap(NbtComponent::of, NbtComponent::copyNbt)).build();
    private DataComponentTypes() {}
}
