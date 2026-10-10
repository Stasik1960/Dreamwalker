package ru.modelprops.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import ru.modelprops.ModelProps;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class DynamicTextureStore {
    private static final Identifier FALLBACK = new Identifier("minecraft", "textures/block/magenta_glazed_terracotta.png");

    private final Map<String, byte[]> serverTextures = new HashMap<>();
    private final Map<String, Identifier> loaded = new HashMap<>();

    public void replace(Map<String, byte[]> textures) {
        clearGpu();
        serverTextures.clear();
        serverTextures.putAll(textures);
    }

    public Identifier resolve(String reference) {
        if (reference == null || reference.isBlank() || reference.startsWith("#")) {
            return FALLBACK;
        }
        Identifier cached = loaded.get(reference);
        if (cached != null) {
            return cached;
        }
        try {
            byte[] bytes = serverTextures.get(reference);
            NativeImage image;
            if (bytes != null) {
                image = NativeImage.read(new ByteArrayInputStream(bytes));
            } else {
                Identifier source = sourceIdentifier(reference);
                Optional<Resource> resource = MinecraftClient.getInstance().getResourceManager().getResource(source);
                if (resource.isEmpty()) {
                    return FALLBACK;
                }
                try (InputStream input = resource.get().getInputStream()) {
                    image = NativeImage.read(input);
                }
            }
            Identifier runtime = ModelProps.id("runtime/" + UUID.nameUUIDFromBytes(
                    reference.getBytes(StandardCharsets.UTF_8)).toString().replace('-', '_'));
            MinecraftClient.getInstance().getTextureManager().registerTexture(runtime, new NativeImageBackedTexture(image));
            loaded.put(reference, runtime);
            return runtime;
        } catch (IOException | RuntimeException exception) {
            ModelProps.LOGGER.warn("Could not load runtime texture {}", reference, exception);
            return FALLBACK;
        }
    }

    public void clearGpu() {
        var manager = MinecraftClient.getInstance().getTextureManager();
        for (Identifier id : loaded.values()) {
            manager.destroyTexture(id);
        }
        loaded.clear();
    }

    private static Identifier sourceIdentifier(String reference) {
        Identifier id = Identifier.tryParse(reference);
        if (id == null) {
            id = new Identifier("minecraft", reference);
        }
        String path = id.getPath();
        if (path.startsWith("textures/")) {
            path = path.substring("textures/".length());
        }
        if (path.endsWith(".png")) {
            path = path.substring(0, path.length() - 4);
        }
        return new Identifier(id.getNamespace(), "textures/" + path + ".png");
    }
}
