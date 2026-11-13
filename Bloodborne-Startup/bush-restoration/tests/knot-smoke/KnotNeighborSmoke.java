import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.launch.knot.Knot;

public final class KnotNeighborSmoke {
    public static void main(String[] args) throws Throwable {
        ClassLoader loader = new Knot(EnvType.CLIENT).init(new String[]{"--version", "1.20.1", "--gameDir", ".", "--accessToken", "0"});
        Thread.currentThread().setContextClassLoader(loader);
        String[] smoke = {
            "net.minecraft.class_4970$class_4971",
            "net.minecraft.class_2508",
            "net.minecraft.class_2551",
            "net.minecraft.class_2544",
            "net.minecraft.class_2389",
            "net.minecraft.class_2349",
            "dev.dreamwalker.bloodbornedw.composite.FabricCompositeWorld",
            "dev.dreamwalker.bloodbornedw.composite.CompositeRuntime",
            "dev.dreamwalker.bloodbornedw.composite.AutoStoneFence",
            "dev.dreamwalker.bloodbornedw.tool.DebugAdjustments",
            "dev.dreamwalker.bloodbornedw.composite.StudioRpGeometry",
            "dev.dreamwalker.bloodbornedw.composite.ImportedCollisionMigration",
            "net.minecraft.class_761",
            "net.minecraft.class_824"
        };
        for (String name : smoke) {
            Class.forName(name, false, loader).getDeclaredMethods();
            System.out.println("PASS transform " + name);
        }
        verifyQuarterShift(loader);
    }

    private static void verifyQuarterShift(ClassLoader loader) throws Throwable {
        Class<?> adjustment = loader.loadClass("dev.dreamwalker.bloodbornedw.tool.DebugAdjustments");
        Class<?> directionClass = loader.loadClass("net.minecraft.class_2350");
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(adjustment, MethodHandles.lookup());
        MethodHandle sanitize = lookup.findStatic(adjustment, "sanitize", MethodType.methodType(int.class, int.class));
        MethodHandle turned = lookup.findStatic(adjustment, "turned", MethodType.methodType(int.class, int.class));
        MethodHandle turns = lookup.findStatic(adjustment, "turns", MethodType.methodType(int.class, int.class));
        MethodHandle hasPose = lookup.findStatic(adjustment, "hasPose", MethodType.methodType(boolean.class, int.class));
        MethodType offsetType = MethodType.methodType(double.class, int.class);
        MethodHandle[] offsets = {
            lookup.findStatic(adjustment, "x", offsetType),
            lookup.findStatic(adjustment, "y", offsetType),
            lookup.findStatic(adjustment, "z", offsetType)
        };
        MethodHandle moved = lookup.findStatic(adjustment, "moved", MethodType.methodType(int.class, int.class, directionClass))
            .asType(MethodType.methodType(int.class, int.class, Object.class));

        for (int legacy = 0; legacy < (1 << 24); legacy++) {
            int x = signedHalf(legacy, 0);
            int y = signedHalf(legacy, 6);
            int z = signedHalf(legacy, 12);
            boolean valid = Math.abs(x) <= 16 && Math.abs(y) <= 16 && Math.abs(z) <= 16;
            int expected = valid ? legacy : legacy & (7 << 21);
            if ((int) sanitize.invokeExact(legacy) != expected
                || (double) offsets[0].invokeExact(legacy) != x * 0.5
                || (double) offsets[1].invokeExact(legacy) != y * 0.5
                || (double) offsets[2].invokeExact(legacy) != z * 0.5
                || (int) turns.invokeExact(legacy) != (legacy >>> 18 & 7)
                || (boolean) hasPose.invokeExact(legacy) != ((legacy & ((1 << 21) - 1)) != 0)) {
                throw new AssertionError("Legacy packed adjustment changed at " + legacy);
            }
        }

        String[] directionComponents = {"method_10148", "method_10164", "method_10165"};
        for (Object direction : directionClass.getEnumConstants()) {
            int axis = 0;
            int sign;
            while ((sign = (int) directionClass.getMethod(directionComponents[axis]).invoke(direction)) == 0) axis++;
            for (int rotationAndFlags = 0; rotationAndFlags < 64; rotationAndFlags++) {
                int value = rotationAndFlags << 18;
                for (int steps = 1; steps <= 32; steps++) {
                    int prior = value;
                    value = (int) moved.invokeExact(value, direction);
                    if ((double) offsets[axis].invokeExact(value) != sign * steps * 0.25
                        || (int) sanitize.invokeExact(value) != value
                        || (value >>> 18 & 63) != rotationAndFlags
                        || (double) offsets[(axis + 1) % 3].invokeExact(value) != 0
                        || (double) offsets[(axis + 2) % 3].invokeExact(value) != 0
                        || !(boolean) hasPose.invokeExact(value)) {
                        throw new AssertionError("Quarter step failed for " + direction + " at " + steps);
                    }
                    Object opposite = directionClass.getMethod("method_10153").invoke(direction);
                    if ((int) moved.invokeExact(value, opposite) != prior) {
                        throw new AssertionError("Quarter step failed to reverse for " + direction + " at " + steps);
                    }
                }
                if ((int) moved.invokeExact(value, direction) != value) throw new AssertionError("Offset exceeded eight blocks");
            }
        }

        Class<?> nbt = loader.loadClass("net.minecraft.class_2487");
        Class<?> tag = loader.loadClass("net.minecraft.class_2520");
        Class<?> nbtList = loader.loadClass("net.minecraft.class_2499");
        Class<?> saved = loader.loadClass(adjustment.getName() + "$Saved");
        Class<?> entry = loader.loadClass(adjustment.getName() + "$Entry");
        var read = saved.getDeclaredMethod("read", nbt);
        var write = saved.getDeclaredMethod("method_75", nbt);
        var entries = saved.getDeclaredField("entries");
        read.setAccessible(true);
        write.setAccessible(true);
        entries.setAccessible(true);
        var settings = entry.getMethod("value");
        int legacy = 61 | 16 << 6 | 48 << 12 | 5 << 18 | 7 << 21;
        Object direction = directionClass.getEnumConstants()[5];
        int modern = (int) moved.invokeExact(legacy, direction);
        if (modern == legacy || (modern & (7 << 24)) == 0) throw new AssertionError("Fixture lacks a quarter fraction");
        int rotated = (int) turned.invokeExact(modern);
        if ((rotated & ~(7 << 18)) != (modern & ~(7 << 18)) || (int) turns.invokeExact(rotated) != 6) {
            throw new AssertionError("Rotation changed fractional offset or flags");
        }
        Object compound = nbt.getConstructor().newInstance();
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) nbtList.getConstructor().newInstance();
        int[] encoded = {legacy, modern, rotated};
        for (int index = 0; index < encoded.length; index++) {
            Object item = nbt.getConstructor().newInstance();
            nbt.getMethod("method_10582", String.class, String.class).invoke(item, "Registry", "bloodborne_dw:composite_cell");
            nbt.getMethod("method_10544", String.class, long.class).invoke(item, "Root", (long) index);
            nbt.getMethod("method_10569", String.class, int.class).invoke(item, "Settings", encoded[index]);
            list.add(item);
        }
        nbt.getMethod("method_10566", String.class, tag).invoke(compound, "Entries", list);
        Object savedState = read.invoke(null, compound);
        Object persisted = write.invoke(savedState, nbt.getConstructor().newInstance());
        for (Object decoded : new Object[]{savedState, read.invoke(null, persisted)}) {
            Map<?, ?> map = (Map<?, ?>) entries.get(decoded);
            for (int index = 0; index < encoded.length; index++) {
                if ((int) settings.invoke(map.get((long) index)) != encoded[index]) throw new AssertionError("NBT offset changed");
            }
        }
        System.out.println("PASS 16,777,216 legacy values, all 6 directions, 12,288 quarter steps and reversals, offset bounds, rotation and NBT read/write");
    }

    private static int signedHalf(int value, int shift) {
        int half = value >>> shift & 63;
        return half < 32 ? half : half - 64;
    }
}
