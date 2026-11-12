package dev.nightwatch.carriage.client;

import dev.nightwatch.carriage.CarriageEntity;
import dev.nightwatch.carriage.NightWatchCarriage;
import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.entity.EntityType;

import java.lang.reflect.Proxy;

/** Loaded by the optional LambDynamicLights entrypoint, so the mod can run without its API. */
public final class LambLightsAdapter implements LanguageAdapter {
    @Override
    public <T> T create(ModContainer mod, String value, Class<T> type) throws LanguageAdapterException {
        if (!type.getName().equals("dev.lambdaurora.lambdynlights.api.DynamicLightsInitializer")) {
            throw new LanguageAdapterException("Unexpected dynamic lighting initializer: " + type);
        }
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (self, method, args) -> {
            return switch (method.getName()) {
                case "onInitializeDynamicLights" -> { register(type.getClassLoader()); yield null; }
                case "toString" -> "Night Watch Carriage dynamic lighting";
                case "hashCode" -> System.identityHashCode(self);
                case "equals" -> self == args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }));
    }

    private static void register(ClassLoader loader) {
        String api = "dev.lambdaurora.lambdynlights.api.";
        try {
            Class<?> handler = Class.forName(api + "DynamicLightHandler", true, loader);
            Class<?> handlers = Class.forName(api + "DynamicLightHandlers", true, loader);
            Object light = Proxy.newProxyInstance(loader, new Class<?>[]{handler}, (self, method, args) -> {
                return switch (method.getName()) {
                    case "getLuminance" -> args[0] instanceof CarriageEntity carriage && carriage.lanternsOn() ? 12 : 0;
                    case "isWaterSensitive" -> false;
                    case "toString" -> "Night Watch Carriage lamps";
                    case "hashCode" -> System.identityHashCode(self);
                    case "equals" -> self == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                };
            });
            handlers.getMethod("registerDynamicLightHandler", EntityType.class, handler)
                .invoke(null, NightWatchCarriage.CARRIAGE, light);
        } catch (ReflectiveOperationException | LinkageError ex) {
            NightWatchCarriage.LOGGER.warn("LambDynamicLights could not register carriage lighting", ex);
        }
    }
}
