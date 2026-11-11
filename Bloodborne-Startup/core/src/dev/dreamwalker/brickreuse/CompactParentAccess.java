package dev.dreamwalker.brickreuse;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.function.Function;
public final class CompactParentAccess {
    private static final ClassValue<Optional<Field>> FIELD = new ClassValue<>() {
        @Override protected Optional<Field> computeValue(Class<?> type) {
            if (!type.isSynthetic() || !type.getName().startsWith("net.minecraft.class_1088$$Lambda")) return Optional.empty();
            Field found = null;
            for (Field field : type.getDeclaredFields()) {
                if (!field.getType().getName().equals("net.minecraft.class_1088")) continue;
                if (found != null) return Optional.empty();
                found = field;
            }
            if (found == null) return Optional.empty();
            try { found.setAccessible(true); return Optional.of(found); }
            catch (RuntimeException failure) { return Optional.empty(); }
        }
    };
    public static boolean calledFromModelLoader() {
        return StackWalker.getInstance().walk(frames -> frames.anyMatch(frame -> frame.getClassName().equals("net.minecraft.class_1088") && frame.getMethodName().equals("method_45875")));
    }
    public static Object owner(Function<?,?> resolver) {
        try {
            Field field = FIELD.get(resolver.getClass()).orElse(null);
            return field == null ? null : field.get(resolver);
        } catch (ReflectiveOperationException | RuntimeException failure) { return null; }
    }
}

