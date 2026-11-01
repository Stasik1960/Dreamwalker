package dev.dreamwalker.bloodbornedw.visual;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.resource.AbstractFileResourcePack;
import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

public final class CityVisualPack extends AbstractFileResourcePack {
    private final String mode;
    private final Set<String> paths = new HashSet<>();
    public CityVisualPack(String name, boolean alt) {
        super(name, true); mode = alt ? "alt" : "original";
        try (InputStream stream = CityVisualPack.class.getResourceAsStream("/assets/bloodborne_dw/visuals/resource-index.json")) {
            if (stream == null) throw new IOException("Missing city visuals resource index");
            JsonObject index = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            index.getAsJsonArray(mode).forEach(e -> {
                String path = e.getAsString();
                if (!path.startsWith("assets/") || path.contains("..") || path.contains("\\")) throw new IllegalArgumentException("Invalid visual resource path");
                paths.add(path);
            });
        } catch (Exception ex) { throw new IllegalStateException("Cannot read city visuals resource index", ex); }
    }
    private InputSupplier<InputStream> supplier(String path) {
        if (!paths.contains(path)) return null;
        return () -> {
            InputStream stream = CityVisualPack.class.getResourceAsStream("/dw_city_visuals/" + mode + "/" + path);
            if (stream == null) throw new IOException("Missing city visuals resource: " + path);
            return stream;
        };
    }
    @Override public InputSupplier<InputStream> openRoot(String... segments) {
        if (segments.length == 1 && segments[0].equals("pack.mcmeta")) return () -> new java.io.ByteArrayInputStream("{\"pack\":{\"pack_format\":15,\"description\":\"City visuals\"}}".getBytes(StandardCharsets.UTF_8));
        return null;
    }
    @Override public InputSupplier<InputStream> open(ResourceType type, Identifier id) {
        return type == ResourceType.CLIENT_RESOURCES ? supplier("assets/" + id.getNamespace() + "/" + id.getPath()) : null;
    }
    @Override public void findResources(ResourceType type, String namespace, String prefix, ResourcePack.ResultConsumer consumer) {
        if (type != ResourceType.CLIENT_RESOURCES) return;
        String base = "assets/" + namespace + "/";
        for (String path : paths) if (path.startsWith(base) && path.substring(base.length()).startsWith(prefix)) {
            Identifier id = Identifier.tryParse(namespace + ":" + path.substring(base.length()));
            if (id != null) consumer.accept(id, supplier(path));
        }
    }
    @Override public Set<String> getNamespaces(ResourceType type) {
        Set<String> result = new HashSet<>();
        if (type == ResourceType.CLIENT_RESOURCES) for (String path : paths) { int end = path.indexOf('/', 7); if (end > 7) result.add(path.substring(7, end)); }
        return result;
    }
    @Override public void close() { }
}
