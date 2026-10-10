package ru.modelprops.client.sound;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.Sound;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.WeightedSoundSet;
import net.minecraft.entity.Entity;
import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.metadata.ResourceMetadataReader;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.floatprovider.ConstantFloatProvider;
import ru.modelprops.ModelProps;
import ru.modelprops.server.upload.OggVorbisValidator;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Plays validated Ogg/Vorbis bytes without requiring a generated resource pack reload.
 *
 * <p>Calls are safe from networking threads: actual SoundManager access is dispatched
 * to the Minecraft client thread. Sounds use the PLAYERS volume category and normal
 * positional attenuation.</p>
 */
public final class DynamicSoundPlayer {
    private static final int MAX_CACHE_ENTRIES = 256;
    private static final long MAX_CACHE_BYTES = 64L * 1024L * 1024L;
    private static final Object CACHE_LOCK = new Object();
    private static final Map<String, RuntimeSound> CACHE = new LinkedHashMap<>(32, 0.75F, true);
    private static final ResourcePack RUNTIME_PACK = new RuntimeResourcePack();

    private static long cachedBytes;
    private static volatile Field soundResourcesField;
    private static volatile boolean reflectionFailureLogged;

    private DynamicSoundPlayer() {
    }

    /** Validates and primes a sound so its first playback does not need to build metadata. */
    public static boolean preload(byte[] oggBytes) {
        RuntimeSound runtimeSound = prepare(oggBytes);
        if (runtimeSound == null) {
            return false;
        }
        runOnClientThread(() -> registerResource(runtimeSound));
        return true;
    }

    /** Plays a positional action sound. Volume and pitch are clamped to safe engine ranges. */
    public static boolean play(byte[] oggBytes, double x, double y, double z, float volume, float pitch) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(volume) || !Float.isFinite(pitch) || volume <= 0.0F) {
            return false;
        }
        RuntimeSound runtimeSound = prepare(oggBytes);
        if (runtimeSound == null) {
            return false;
        }
        float safeVolume = Math.min(volume, 4.0F);
        float safePitch = Math.max(0.25F, Math.min(pitch, 4.0F));
        runOnClientThread(() -> {
            if (registerResource(runtimeSound)) {
                MinecraftClient.getInstance().getSoundManager().play(new RuntimeSoundInstance(
                        runtimeSound.id(), runtimeSound.sound(), runtimeSound.soundSet(),
                        x, y, z, safeVolume, safePitch
                ));
            }
        });
        return true;
    }

    /** Convenience overload that plays from the current center of an entity. */
    public static boolean play(byte[] oggBytes, Entity source, float volume, float pitch) {
        if (source == null) {
            return false;
        }
        return play(oggBytes, source.getX(), source.getBodyY(0.5D), source.getZ(), volume, pitch);
    }

    /** Removes retained byte arrays and unregisters their synthetic resources. */
    public static void clear() {
        List<Identifier> locations;
        synchronized (CACHE_LOCK) {
            locations = CACHE.values().stream().map(RuntimeSound::location).toList();
            CACHE.clear();
            cachedBytes = 0L;
        }
        if (!locations.isEmpty()) {
            runOnClientThread(() -> removeResources(locations));
        }
    }

    private static RuntimeSound prepare(byte[] source) {
        if (source == null) {
            return null;
        }
        String digest = sha256(source);
        synchronized (CACHE_LOCK) {
            RuntimeSound cached = CACHE.get(digest);
            if (cached != null) {
                return cached;
            }
        }

        byte[] bytes = source.clone();
        try {
            OggVorbisValidator.validate(bytes);
        } catch (IOException exception) {
            ModelProps.LOGGER.warn("Rejected invalid runtime OGG sound: {}", exception.getMessage());
            return null;
        }
        digest = sha256(bytes);

        List<Identifier> expired = List.of();
        RuntimeSound result;
        synchronized (CACHE_LOCK) {
            RuntimeSound cached = CACHE.get(digest);
            if (cached != null) {
                return cached;
            }
            if (CACHE.size() >= MAX_CACHE_ENTRIES || cachedBytes + bytes.length > MAX_CACHE_BYTES) {
                expired = CACHE.values().stream().map(RuntimeSound::location).toList();
                CACHE.clear();
                cachedBytes = 0L;
            }

            Identifier id = ModelProps.id("runtime_sound/" + digest);
            Sound sound = new Sound(
                    id.toString(),
                    ConstantFloatProvider.create(1.0F),
                    ConstantFloatProvider.create(1.0F),
                    1,
                    Sound.RegistrationType.FILE,
                    true,
                    false,
                    16
            );
            WeightedSoundSet soundSet = new WeightedSoundSet(id, null);
            soundSet.add(sound);
            result = new RuntimeSound(id, sound.getLocation(), sound, soundSet, bytes);
            CACHE.put(digest, result);
            cachedBytes += bytes.length;
        }
        if (!expired.isEmpty()) {
            List<Identifier> removedLocations = expired;
            runOnClientThread(() -> removeResources(removedLocations));
        }
        return result;
    }

    private static boolean registerResource(RuntimeSound runtimeSound) {
        Map<Identifier, Resource> resources = getSoundResources();
        if (resources == null) {
            return false;
        }
        resources.put(runtimeSound.location(), new Resource(
                RUNTIME_PACK,
                () -> new ByteArrayInputStream(runtimeSound.bytes())
        ));
        return true;
    }

    private static void removeResources(List<Identifier> locations) {
        Map<Identifier, Resource> resources = getSoundResources();
        if (resources != null) {
            locations.forEach(resources::remove);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<Identifier, Resource> getSoundResources() {
        try {
            Field field = soundResourcesField;
            if (field == null) {
                synchronized (DynamicSoundPlayer.class) {
                    field = soundResourcesField;
                    if (field == null) {
                        MappingResolver resolver = FabricLoader.getInstance().getMappingResolver();
                        String runtimeName = resolver.mapFieldName(
                                "intermediary",
                                "net.minecraft.class_1144",
                                "field_40576",
                                "Ljava/util/Map;"
                        );
                        field = SoundManager.class.getDeclaredField(runtimeName);
                        if (!field.trySetAccessible()) {
                            throw new IllegalAccessException("SoundManager sound resource map is inaccessible");
                        }
                        soundResourcesField = field;
                    }
                }
            }
            return (Map<Identifier, Resource>) field.get(MinecraftClient.getInstance().getSoundManager());
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (!reflectionFailureLogged) {
                reflectionFailureLogged = true;
                ModelProps.LOGGER.error("Could not access Minecraft's runtime sound resource map", exception);
            }
            return null;
        }
    }

    private static void runOnClientThread(Runnable action) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.isOnThread()) {
            action.run();
        } else {
            client.execute(action);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(Character.forDigit((value >>> 4) & 0x0F, 16));
                result.append(Character.forDigit(value & 0x0F, 16));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record RuntimeSound(Identifier id, Identifier location, Sound sound,
                                WeightedSoundSet soundSet, byte[] bytes) {
    }

    private static final class RuntimeResourcePack implements ResourcePack {
        @Override
        public InputSupplier<InputStream> openRoot(String... segments) {
            return null;
        }

        @Override
        public InputSupplier<InputStream> open(ResourceType type, Identifier id) {
            return null;
        }

        @Override
        public void findResources(ResourceType type, String namespace, String prefix, ResultConsumer consumer) {
        }

        @Override
        public Set<String> getNamespaces(ResourceType type) {
            return Collections.emptySet();
        }

        @Override
        public <T> T parseMetadata(ResourceMetadataReader<T> metaReader) {
            return null;
        }

        @Override
        public String getName() {
            return "Model Props runtime sounds";
        }

        @Override
        public boolean isAlwaysStable() {
            return true;
        }

        @Override
        public void close() {
        }
    }
}
