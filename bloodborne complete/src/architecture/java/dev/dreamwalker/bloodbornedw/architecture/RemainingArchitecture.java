package dev.dreamwalker.bloodbornedw.architecture;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/** Explicit artworks imported after V12. Existing prototypes retain their own data. */
public final class RemainingArchitecture {
    private record Artwork(String path, List<String> names) {}
    private static final Map<String, Artwork> ARTWORKS = load();
    private RemainingArchitecture() {}

    private static Map<String, Artwork> load() {
        var input = RemainingArchitecture.class.getResourceAsStream("/bloodborne_dw/remaining-catalogue.json");
        if (input == null) throw new IllegalStateException("Missing remaining architecture catalogue");
        try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            JsonObject document = JsonParser.parseReader(reader).getAsJsonObject();
            if (document.get("schemaVersion").getAsInt() != 1) throw new IllegalStateException("Unsupported remaining catalogue");
            Map<String, Artwork> result = new LinkedHashMap<>();
            for (JsonElement element : document.getAsJsonArray("objects")) {
                JsonObject row = element.getAsJsonObject();
                String path = row.get("path").getAsString();
                if (!path.matches("source_[a-z0-9_]+")) throw new IllegalStateException("Invalid new artwork ID " + path);
                List<String> names = new ArrayList<>();
                for (JsonElement value : row.getAsJsonArray("variants")) {
                    JsonObject variant = value.getAsJsonObject();
                    if (variant.get("variant").getAsInt() != names.size()) throw new IllegalStateException("Non-contiguous artwork variants " + path);
                    String name = variant.get("name").getAsString();
                    if (name.isBlank()) throw new IllegalStateException("Empty artwork name " + path);
                    names.add(name);
                }
                if (names.isEmpty() || names.size() > 16 || result.putIfAbsent(path, new Artwork(path, List.copyOf(names))) != null)
                    throw new IllegalStateException("Invalid/repeated artwork " + path);
            }
            return Collections.unmodifiableMap(result);
        } catch (Exception failure) { throw new IllegalStateException("Invalid remaining architecture catalogue", failure); }
    }

    public static String[] paths(String... existing) {
        List<String> paths = new ArrayList<>(Arrays.asList(existing));
        paths.addAll(ARTWORKS.keySet());
        return paths.toArray(String[]::new);
    }

    public static String variantName(String path, int variant) {
        Artwork artwork = ARTWORKS.get(path);
        return artwork == null ? null : artwork.names.get(variant >= 0 && variant < artwork.names.size() ? variant : 0);
    }

    public static boolean opens(dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock block) {
        return ARTWORKS.containsKey(block.spec.id.getPath()) && block.spec.openable;
    }

    /** All complete imported variants are offered, including those absent from the map. */
    public static List<ItemStack> creativeVariants(ItemStack original) {
        Identifier id = Registries.ITEM.getId(original.getItem());
        Artwork artwork = id.getNamespace().equals("bloodborne_dw") ? ARTWORKS.get(id.getPath()) : null;
        if (artwork == null) return List.of(original.copy());
        List<ItemStack> result = new ArrayList<>();
        for (int variant = 0; variant < artwork.names.size(); variant++) {
            ItemStack stack = original.copy();
            stack.getOrCreateSubNbt("BlockStateTag").putString("variant", Integer.toString(variant));
            result.add(stack);
        }
        return List.copyOf(result);
    }

    public static List<ItemStack> stacks() {
        List<ItemStack> result = new ArrayList<>();
        for (String path : ARTWORKS.keySet()) result.addAll(creativeVariants(new ItemStack(Registries.ITEM.get(new Identifier("bloodborne_dw", path)))));
        return List.copyOf(result);
    }
}
