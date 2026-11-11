package dev.dreamwalker.bloodbornedw.composite;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Adds a view-depth bias without changing projected coverage or texture interpolation. */
public final class SurfaceDepthShaders {
    private static final Logger LOGGER = LoggerFactory.getLogger("DreamWalker");
    private static final String SODIUM_POSITION = "gl_Position = u_ProjectionMatrix * u_ModelViewMatrix * vec4(position, 1.0);";
    private static final String COMPOSITE_POSITION = "gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);";
    private static final String COMPOSITE_LIGHT = "minecraft_sample_lightmap(Sampler2, UV2)";
    private static final MethodHandle IRIS_ACTIVE = irisActive();
    private static volatile boolean compositePatched;

    private SurfaceDepthShaders() {}

    public static String sodium(String source) {
        if (!SodiumCompatibility.verifiedClient() || !once(source, SODIUM_POSITION)) return source;
        return source.replace(SODIUM_POSITION, SODIUM_POSITION + """

                uint dw_rank = (a_Position.x >> 30u) | ((a_Position.y >> 30u) << 2u)
                        | ((a_LightAndData.z >> 5u) << 4u);
                """ + bias("float(dw_rank)", "u_ProjectionMatrix"));
    }

    public static String composite(String source) {
        compositePatched = once(source, COMPOSITE_POSITION) && once(source, COMPOSITE_LIGHT);
        if (!compositePatched) {
            LOGGER.warn("DreamWalker surface priority is unavailable for the installed cutout vertex shader.");
            return source;
        }
        return source.replace(COMPOSITE_POSITION, COMPOSITE_POSITION + """

                int dw_rank = UV2.y >> 8;
                ivec2 dw_light = ivec2(UV2.x, UV2.y & 255);
                """ + bias("(dw_rank > 0 ? float(dw_rank) + 0.5 : 0.0)", "ProjMat"))
                .replace(COMPOSITE_LIGHT, "minecraft_sample_lightmap(Sampler2, dw_light)");
    }

    public static boolean compositeEnabled() {
        if (!compositePatched) return false;
        if (IRIS_ACTIVE == null) return !FabricLoader.getInstance().isModLoaded("iris");
        try {
            return !(boolean) IRIS_ACTIVE.invokeExact();
        } catch (Throwable unavailable) {
            return false;
        }
    }

    private static MethodHandle irisActive() {
        if (!FabricLoader.getInstance().isModLoaded("iris")) return null;
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            return MethodHandles.publicLookup().findVirtual(api, "isShaderPackInUse", MethodType.methodType(boolean.class))
                    .bindTo(instance);
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return null;
        }
    }

    private static boolean once(String source, String marker) {
        int at = source.indexOf(marker);
        return at >= 0 && source.indexOf(marker, at + marker.length()) < 0;
    }

    private static String bias(String rank, String projection) {
        // Convert a small view-space distance into clip-space Z, retaining X, Y and W.
        return """
                float dw_priority = %s;
                if (dw_priority > 0.0 && gl_Position.w > 0.0 && gl_Position.z > -gl_Position.w) {
                    float dw_bias = abs(%s[3][2]) * dw_priority * (4.0 / 32768.0) / max(gl_Position.w, 0.5);
                    gl_Position.z = max(-gl_Position.w, gl_Position.z - dw_bias);
                }
                """.formatted(rank, projection);
    }
}
