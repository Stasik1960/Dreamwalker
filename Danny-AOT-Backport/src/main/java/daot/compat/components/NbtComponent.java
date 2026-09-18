package daot.compat.components;

import java.util.function.Consumer;
import net.minecraft.nbt.NbtCompound;

public record NbtComponent(NbtCompound value) {
    public static final NbtComponent DEFAULT = new NbtComponent(new NbtCompound());
    public static NbtComponent of(NbtCompound tag) { return new NbtComponent(tag.copy()); }
    public NbtCompound copyNbt() { return value.copy(); }
    public NbtComponent apply(Consumer<NbtCompound> operation) {
        NbtCompound copy = copyNbt();
        operation.accept(copy);
        return new NbtComponent(copy);
    }
}
