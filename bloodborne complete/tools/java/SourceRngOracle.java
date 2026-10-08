package dev.dreamwalker.qa;
import java.lang.reflect.*;
import java.util.*;

/** Calls the ACTUAL Mojang 1.18.2 Mth and WeightedBakedModel bytecode. */
public final class SourceRngOracle {
    public static void main(String[] args) throws Exception {
        java.io.PrintStream output = System.out;
        Class.forName("ab").getMethod("a").invoke(null);
        Class.forName("yv").getMethod("a").invoke(null);
        Class<?> math = Class.forName("ajl");
        Method seedMethod = math.getMethod("c", int.class, int.class, int.class);
        Class<?> modelInterface = Class.forName("fck");
        Class<?> entryInterface = Class.forName("auu");
        Method wrap = entryInterface.getMethod("a", Object.class, int.class);
        Class<?> weighted = Class.forName("fcv");
        Method quads = Arrays.stream(weighted.getMethods()).filter(m -> m.getName().equals("a")
            && m.getParameterCount() == 3 && m.getParameterTypes()[2] == Random.class).findFirst().orElseThrow();
        for (String line : args) {
            String[] fields = line.split(":");
            String[] coords = fields[0].split(",");
            int x = Integer.parseInt(coords[0]), y = Integer.parseInt(coords[1]), z = Integer.parseInt(coords[2]);
            String[] weights = fields[1].split(",");
            List<Object> entries = new ArrayList<>();
            for (int i = 0; i < weights.length; i++) {
                final int index = i;
                Object model = Proxy.newProxyInstance(modelInterface.getClassLoader(), new Class<?>[]{modelInterface},
                    (proxy, method, parameters) -> {
                        if (method.getReturnType() == List.class) return List.of(index);
                        if (method.getName().equals("toString")) return "source-oracle-" + index;
                        if (method.getReturnType() == boolean.class) return false;
                        return null;
                    });
                entries.add(wrap.invoke(null, model, Integer.parseInt(weights[i])));
            }
            long seed = (Long) seedMethod.invoke(null, x, y, z);
            Object selection = weighted.getConstructor(List.class).newInstance(entries);
            List<?> result = (List<?>) quads.invoke(selection, null, null, new Random(seed));
            output.println(x + "," + y + "," + z + ":" + fields[1] + ":" + seed + ":" + result.get(0));
        }
    }
}
