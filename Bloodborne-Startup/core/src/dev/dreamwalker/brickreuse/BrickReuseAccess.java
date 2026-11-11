package dev.dreamwalker.brickreuse;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;
import net.minecraft.class_2960;
import net.minecraft.class_4590;
public final class BrickReuseAccess {
    public final Field loader, models;
    public final Constructor<?> key;
    public BrickReuseAccess(Field loader, Field models, Constructor<?> key) { this.loader = loader; this.models = models; this.key = key; }
    public static BrickReuseAccess create() {
        try {
            Class<?> baker = Class.forName("net.minecraft.class_1088$class_7778");
            Class<?> loader = Class.forName("net.minecraft.class_1088");
            Class<?> key = Class.forName("net.minecraft.class_1088$class_7776");
            Field a = baker.getDeclaredField("field_40571");
            Field b = loader.getDeclaredField("field_5398");
            Constructor<?> c = key.getDeclaredConstructor(class_2960.class, class_4590.class, boolean.class);
            a.setAccessible(true); b.setAccessible(true); c.setAccessible(true);
            return new BrickReuseAccess(a,b,c);
        } catch (ReflectiveOperationException | RuntimeException failure) { return new BrickReuseAccess(null,null,null); }
    }
    public Map<?,?> cache(Object baker) throws ReflectiveOperationException {
        if (loader == null || models == null || key == null || !loader.getDeclaringClass().isInstance(baker)) throw new ReflectiveOperationException("unsupported baker");
        return (Map<?,?>)models.get(loader.get(baker));
    }
}
