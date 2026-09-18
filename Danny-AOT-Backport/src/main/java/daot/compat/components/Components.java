package daot.compat.components;

import java.util.function.UnaryOperator;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.Identifier;

public final class Components {
    private static final String KEY = "dannys-aot:components";
    public static <T> ComponentType<T> register(Identifier id, ComponentType<T> type) {
        if (type.id != null) throw new IllegalStateException("Component already registered " + type.id);
        type.id = id;
        return type;
    }
    @SuppressWarnings("unchecked")
    public static <T> T get(ItemStack stack, ComponentType<T> type) {
        NbtCompound root = stack.getNbt();
        if (root == null) return null;
        if (type == DataComponentTypes.CUSTOM_DATA) return (T) NbtComponent.of(root);
        NbtElement element = root.getCompound(KEY).get(type.id.toString());
        return element == null ? null : type.codec.parse(NbtOps.INSTANCE, element).result().orElse(null);
    }
    public static <T> T getOrDefault(ItemStack stack, ComponentType<T> type, T fallback) {
        T value = get(stack, type);
        return value == null ? fallback : value;
    }
    public static <T> void set(ItemStack stack, ComponentType<T> type, T value) {
        if (type == DataComponentTypes.CUSTOM_DATA) {
            stack.setNbt(((NbtComponent) value).copyNbt());
            return;
        }
        NbtElement encoded = type.codec.encodeStart(NbtOps.INSTANCE, value).result()
                .orElseThrow(() -> new IllegalArgumentException("Cannot encode component " + type.id));
        NbtCompound values = stack.getOrCreateSubNbt(KEY);
        values.put(type.id.toString(), encoded);
    }
    public static <T> void apply(ItemStack stack, ComponentType<T> type, T fallback, UnaryOperator<T> operation) {
        set(stack, type, operation.apply(getOrDefault(stack, type, fallback)));
    }
    public static <T> void remove(ItemStack stack, ComponentType<T> type) {
        if (stack.getNbt() != null) stack.getNbt().getCompound(KEY).remove(type.id.toString());
    }
    private Components() {}
}
