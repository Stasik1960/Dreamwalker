package dev.dreamwalker.bloodbornedw.visual;

import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

/** Resource-generated exact sprite mapping; no material inference on the render thread. */
final class ManualStonePalette {
    private static final Identifier RESOURCE = new Identifier("bloodborne_dw", "visuals/editor2-palette.json");
    private static volatile Map<Identifier, Identifier[]> sprites;
    private ManualStonePalette() {}
    // Resource reload runs before world rendering: the first painted chunk only does a map lookup.
    static void reload() { sprites = load(); }
    static Identifier target(Identifier source, int role) {
        if (role < 1 || role > 4) return null;
        Map<Identifier, Identifier[]> current = sprites;
        if (current == null) synchronized (ManualStonePalette.class) {
            if (sprites == null) sprites = load();
            current = sprites;
        }
        Identifier[] variants = current.get(source);
        return variants == null ? null : variants[role - 1];
    }
    private static Map<Identifier, Identifier[]> load() {
        var resource = MinecraftClient.getInstance().getResourceManager().getResource(RESOURCE);
        if (resource.isEmpty()) return Map.of();
        try (Reader reader = resource.get().getReader()) {
            var json = JsonParser.parseReader(reader).getAsJsonObject();
            if (json.get("schema").getAsInt() != 1) throw new IllegalArgumentException("Unknown palette schema");
            Map<Identifier, Identifier[]> result = new HashMap<>();
            for (var entry : json.getAsJsonObject("sprites").entrySet()) {
                var values = entry.getValue().getAsJsonArray();
                if (values.size() != 4) throw new IllegalArgumentException("Expected four variants");
                Identifier[] ids = new Identifier[4];
                for (int i = 0; i < 4; i++) ids[i] = new Identifier(values.get(i).getAsString());
                result.put(new Identifier(entry.getKey()), ids);
            }
            return Map.copyOf(result);
        } catch (Exception ex) { throw new IllegalStateException("Cannot load " + RESOURCE, ex); }
    }
}
