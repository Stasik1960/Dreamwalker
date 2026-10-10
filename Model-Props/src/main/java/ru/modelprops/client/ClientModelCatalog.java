package ru.modelprops.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import ru.modelprops.ModelPropItem;
import ru.modelprops.ModelProps;
import ru.modelprops.animation.AnimationParser;
import ru.modelprops.animation.AnimationSet;
import ru.modelprops.client.render.DynamicTextureStore;
import ru.modelprops.client.render.JsonModelParser;
import ru.modelprops.client.render.RenderModel;
import ru.modelprops.client.sound.DynamicSoundPlayer;
import ru.modelprops.model.ModelTransform;
import ru.modelprops.net.ModelPropsAssetProtocol;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public final class ClientModelCatalog {
    public static final ClientModelCatalog INSTANCE = new ClientModelCatalog();
    private static final int MAX_MODELS = 512;
    private static final int MAX_TEXTURES = 8_192;
    private static final int MAX_IN_FLIGHT_TEXTURES = 64;
    private static final long MAX_SYNC_BYTES = 64L * 1024L * 1024L;

    private final DynamicTextureStore textures = new DynamicTextureStore();
    private Map<String, ClientModelEntry> entries = Map.of();
    private Map<String, ModelTransform> transforms = new LinkedHashMap<>();
    private final Map<String, ModelTransform> previews = new LinkedHashMap<>();

    private boolean syncing;
    private boolean rejectingSync;
    private int pendingGeneration;
    private int expectedModels;
    private long pendingBytes;
    private boolean pendingCanEdit;
    private Map<String, RawModel> pendingModels = new LinkedHashMap<>();
    private Map<String, byte[]> pendingTextures = new LinkedHashMap<>();
    private Map<String, String> pendingAnimations = new LinkedHashMap<>();
    private Map<String, Map<String, byte[]>> pendingSounds = new LinkedHashMap<>();
    private Map<String, ModelTransform> pendingTransforms = new LinkedHashMap<>();
    private final Map<String, TextureAssembly> assemblies = new LinkedHashMap<>();
    private final Map<String, TextureAssembly> animationAssemblies = new LinkedHashMap<>();
    private final Map<SoundKey, TextureAssembly> soundAssemblies = new LinkedHashMap<>();
    private boolean canEdit;

    private ClientModelCatalog() {
    }

    public void begin(int generation, boolean editorPermission, int modelCount) {
        pendingGeneration = generation;
        if (modelCount < 0 || modelCount > MAX_MODELS) {
            ModelProps.LOGGER.warn("Rejected Model Props sync with {} model(s)", modelCount);
            syncing = false;
            rejectingSync = true;
            pendingModels = new LinkedHashMap<>();
            pendingTextures = new LinkedHashMap<>();
            pendingAnimations = new LinkedHashMap<>();
            pendingSounds = new LinkedHashMap<>();
            pendingTransforms = new LinkedHashMap<>();
            assemblies.clear();
            animationAssemblies.clear();
            soundAssemblies.clear();
            pendingBytes = 0L;
            return;
        }
        syncing = true;
        rejectingSync = false;
        expectedModels = modelCount;
        pendingBytes = 0L;
        pendingCanEdit = editorPermission;
        pendingModels = new LinkedHashMap<>();
        pendingTextures = new LinkedHashMap<>();
        pendingAnimations = new LinkedHashMap<>();
        pendingSounds = new LinkedHashMap<>();
        pendingTransforms = new LinkedHashMap<>();
        assemblies.clear();
        animationAssemblies.clear();
        soundAssemblies.clear();
    }

    public void acceptModel(int generation, String id, String displayName, String json) {
        if (syncing && generation == pendingGeneration) {
            RawModel previous = pendingModels.get(id);
            if (previous == null && pendingModels.size() >= MAX_MODELS) {
                ModelProps.LOGGER.warn("Ignored excess synchronized model {}", id);
                return;
            }
            long bytes = utf8Length(id) + utf8Length(displayName) + utf8Length(json);
            long replacedBytes = previous == null ? 0L : previous.payloadBytes();
            if (pendingBytes - replacedBytes + bytes > MAX_SYNC_BYTES) {
                ModelProps.LOGGER.warn("Ignored synchronized model {} because the catalog exceeds 64 MiB", id);
                return;
            }
            pendingModels.put(id, new RawModel(id, displayName, json, bytes));
            pendingBytes = pendingBytes - replacedBytes + bytes;
        }
    }

    public void beginTexture(int generation, String key, int size, String sha256) {
        if (!syncing || generation != pendingGeneration || size <= 0 || size > 4 * 1024 * 1024
                || pendingTextures.containsKey(key) || assemblies.containsKey(key)
                || pendingTextures.size() + assemblies.size() >= MAX_TEXTURES
                || assemblies.size() >= MAX_IN_FLIGHT_TEXTURES
                || pendingBytes + size > MAX_SYNC_BYTES) {
            return;
        }
        assemblies.put(key, new TextureAssembly(new byte[size], sha256));
        pendingBytes += size;
    }

    public void acceptTextureChunk(int generation, String key, int offset, byte[] chunk) {
        if (!syncing || generation != pendingGeneration) {
            return;
        }
        TextureAssembly assembly = assemblies.get(key);
        if (assembly == null || offset != assembly.received || chunk.length < 1
                || offset + chunk.length > assembly.bytes.length) {
            return;
        }
        System.arraycopy(chunk, 0, assembly.bytes, offset, chunk.length);
        assembly.received += chunk.length;
    }

    public void endTexture(int generation, String key) {
        if (!syncing || generation != pendingGeneration) {
            return;
        }
        TextureAssembly assembly = assemblies.remove(key);
        if (assembly == null || assembly.received != assembly.bytes.length) {
            if (assembly != null) {
                pendingBytes -= assembly.bytes.length;
            }
            ModelProps.LOGGER.warn("Incomplete runtime texture {}", key);
            return;
        }
        String actual = sha256(assembly.bytes);
        if (!actual.equalsIgnoreCase(assembly.sha256)) {
            pendingBytes -= assembly.bytes.length;
            ModelProps.LOGGER.warn("Checksum mismatch for runtime texture {}", key);
            return;
        }
        pendingTextures.put(key, assembly.bytes);
    }

    public void beginAnimation(int generation, String modelId, int size, String sha256) {
        if (!syncing || generation != pendingGeneration || !pendingModels.containsKey(modelId)
                || size < 1 || size > ModelPropsAssetProtocol.MAX_ANIMATION_BYTES
                || pendingAnimations.containsKey(modelId) || animationAssemblies.containsKey(modelId)
                || pendingBytes + size > MAX_SYNC_BYTES) {
            return;
        }
        animationAssemblies.put(modelId, new TextureAssembly(new byte[size], sha256));
        pendingBytes += size;
    }

    public void acceptAnimationChunk(int generation, String modelId, int offset, byte[] chunk) {
        if (!syncing || generation != pendingGeneration) {
            return;
        }
        TextureAssembly assembly = animationAssemblies.get(modelId);
        if (assembly == null || offset != assembly.received || chunk.length < 1
                || offset + chunk.length > assembly.bytes.length) {
            return;
        }
        System.arraycopy(chunk, 0, assembly.bytes, offset, chunk.length);
        assembly.received += chunk.length;
    }

    public void endAnimation(int generation, String modelId) {
        if (!syncing || generation != pendingGeneration) {
            return;
        }
        TextureAssembly assembly = animationAssemblies.remove(modelId);
        if (!finishAssembly("animation for " + modelId, assembly)) {
            return;
        }
        pendingAnimations.put(modelId,
                new String(assembly.bytes, java.nio.charset.StandardCharsets.UTF_8));
    }

    public void beginSound(int generation, String modelId, String soundKey, int size, String sha256) {
        SoundKey key = new SoundKey(modelId, soundKey);
        long existing = pendingSounds.getOrDefault(modelId, Map.of()).size()
                + soundAssemblies.keySet().stream().filter(item -> item.modelId().equals(modelId)).count();
        long existingBytes = pendingSounds.getOrDefault(modelId, Map.of()).values().stream()
                .mapToLong(bytes -> bytes.length).sum()
                + soundAssemblies.entrySet().stream()
                .filter(item -> item.getKey().modelId().equals(modelId))
                .mapToLong(item -> item.getValue().bytes.length).sum();
        if (!syncing || generation != pendingGeneration || !pendingModels.containsKey(modelId)
                || !ModelPropsAssetProtocol.validSoundKey(soundKey)
                || size < 1 || size > ModelPropsAssetProtocol.MAX_SOUND_BYTES
                || existing >= ModelPropsAssetProtocol.MAX_SOUNDS_PER_MODEL
                || existingBytes + size > ModelPropsAssetProtocol.MAX_SOUND_TOTAL_BYTES
                || containsSoundKeyIgnoringCase(modelId, soundKey)
                || pendingBytes + size > MAX_SYNC_BYTES) {
            return;
        }
        soundAssemblies.put(key, new TextureAssembly(new byte[size], sha256));
        pendingBytes += size;
    }

    public void acceptSoundChunk(int generation, String modelId, String soundKey, int offset, byte[] chunk) {
        if (!syncing || generation != pendingGeneration) {
            return;
        }
        TextureAssembly assembly = soundAssemblies.get(new SoundKey(modelId, soundKey));
        if (assembly == null || offset != assembly.received || chunk.length < 1
                || offset + chunk.length > assembly.bytes.length) {
            return;
        }
        System.arraycopy(chunk, 0, assembly.bytes, offset, chunk.length);
        assembly.received += chunk.length;
    }

    public void endSound(int generation, String modelId, String soundKey) {
        if (!syncing || generation != pendingGeneration) {
            return;
        }
        TextureAssembly assembly = soundAssemblies.remove(new SoundKey(modelId, soundKey));
        if (!finishAssembly("sound " + modelId + "/" + soundKey, assembly)) {
            return;
        }
        pendingSounds.computeIfAbsent(modelId, ignored -> new LinkedHashMap<>())
                .put(soundKey, assembly.bytes);
    }

    private boolean containsSoundKeyIgnoringCase(String modelId, String soundKey) {
        String folded = soundKey.toLowerCase(Locale.ROOT);
        if (pendingSounds.getOrDefault(modelId, Map.of()).keySet().stream()
                .anyMatch(existing -> existing.toLowerCase(Locale.ROOT).equals(folded))) {
            return true;
        }
        return soundAssemblies.keySet().stream()
                .anyMatch(existing -> existing.modelId().equals(modelId)
                        && existing.soundKey().toLowerCase(Locale.ROOT).equals(folded));
    }

    private boolean finishAssembly(String label, TextureAssembly assembly) {
        if (assembly == null || assembly.received != assembly.bytes.length) {
            if (assembly != null) {
                pendingBytes -= assembly.bytes.length;
            }
            ModelProps.LOGGER.warn("Incomplete synchronized {}", label);
            return false;
        }
        String actual = sha256(assembly.bytes);
        if (!actual.equalsIgnoreCase(assembly.sha256)) {
            pendingBytes -= assembly.bytes.length;
            ModelProps.LOGGER.warn("Checksum mismatch for synchronized {}", label);
            return false;
        }
        return true;
    }

    public void acceptTransform(String id, ModelTransform transform) {
        if (rejectingSync || !transform.isValid()) {
            return;
        }
        if (syncing) {
            if (pendingModels.containsKey(id)) {
                pendingTransforms.put(id, transform);
            }
        } else if (entries.containsKey(id)) {
            transforms.put(id, transform);
            previews.remove(id);
        }
    }

    public void finish(int generation) {
        if (rejectingSync && generation == pendingGeneration) {
            rejectingSync = false;
            return;
        }
        if (!syncing || generation != pendingGeneration) {
            return;
        }
        if (pendingModels.size() != expectedModels) {
            ModelProps.LOGGER.warn("Expected {} Model Props model(s), received {}", expectedModels, pendingModels.size());
        }
        TreeMap<String, ClientModelEntry> parsed = new TreeMap<>();
        DynamicSoundPlayer.clear();
        for (RawModel raw : pendingModels.values()) {
            try {
                RenderModel model = JsonModelParser.parse(raw.id, raw.displayName, raw.json);
                AnimationSet animation = AnimationSet.EMPTY;
                String animationJson = pendingAnimations.get(raw.id);
                if (animationJson != null && !animationJson.isBlank()) {
                    try {
                        animation = AnimationParser.parse(animationJson, model.animationTargets());
                    } catch (RuntimeException exception) {
                        ModelProps.LOGGER.warn("Could not parse animation for synchronized model {}",
                                raw.id, exception);
                    }
                }
                Map<String, byte[]> sounds = pendingSounds.getOrDefault(raw.id, Map.of());
                sounds.values().forEach(DynamicSoundPlayer::preload);
                parsed.put(raw.id, new ClientModelEntry(raw.id, raw.displayName, model, animation, sounds));
            } catch (RuntimeException exception) {
                ModelProps.LOGGER.warn("Could not parse synchronized model {}", raw.id, exception);
            }
        }
        textures.replace(pendingTextures);
        entries = Collections.unmodifiableMap(new LinkedHashMap<>(parsed));
        transforms = new LinkedHashMap<>(pendingTransforms);
        previews.clear();
        canEdit = pendingCanEdit;
        syncing = false;
        assemblies.clear();
        animationAssemblies.clear();
        soundAssemblies.clear();
        pendingModels = Map.of();
        pendingTextures = Map.of();
        pendingAnimations = Map.of();
        pendingSounds = Map.of();
        pendingTransforms = Map.of();
        pendingBytes = 0L;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() != null) {
            ItemGroups.updateDisplayContext(
                    client.getNetworkHandler().getEnabledFeatures(),
                    canEdit,
                    client.getNetworkHandler().getRegistryManager()
            );
        }
        ModelProps.LOGGER.info("Received {} Model Props model(s) from the server", entries.size());
    }

    public void clear() {
        textures.replace(Map.of());
        DynamicSoundPlayer.clear();
        entries = Map.of();
        transforms.clear();
        previews.clear();
        syncing = false;
        rejectingSync = false;
        assemblies.clear();
        animationAssemblies.clear();
        soundAssemblies.clear();
        pendingModels = Map.of();
        pendingTextures = Map.of();
        pendingAnimations = Map.of();
        pendingSounds = Map.of();
        pendingTransforms = Map.of();
        pendingBytes = 0L;
        canEdit = false;
    }

    public Map<String, ClientModelEntry> entries() {
        return entries;
    }

    public ClientModelEntry get(String id) {
        return entries.get(id);
    }

    public List<ItemStack> creativeStacks() {
        List<ItemStack> result = new ArrayList<>(entries.size());
        for (ClientModelEntry entry : entries.values()) {
            result.add(ModelPropItem.createStack(entry.id(), entry.displayName(), 1));
        }
        return result;
    }

    public ModelTransform transform(String id) {
        return previews.getOrDefault(id, transforms.getOrDefault(id, ModelTransform.IDENTITY));
    }

    public ModelTransform savedTransform(String id) {
        return transforms.getOrDefault(id, ModelTransform.IDENTITY);
    }

    public void preview(String id, ModelTransform transform) {
        previews.put(id, transform);
    }

    public void clearPreview(String id) {
        previews.remove(id);
    }

    public DynamicTextureStore textures() {
        return textures;
    }

    public boolean canEdit() {
        return canEdit;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static int utf8Length(String value) {
        return value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }

    private record RawModel(String id, String displayName, String json, long payloadBytes) {
    }

    private record SoundKey(String modelId, String soundKey) {
    }

    private static final class TextureAssembly {
        private final byte[] bytes;
        private final String sha256;
        private int received;

        private TextureAssembly(byte[] bytes, String sha256) {
            this.bytes = bytes;
            this.sha256 = sha256;
        }
    }
}
