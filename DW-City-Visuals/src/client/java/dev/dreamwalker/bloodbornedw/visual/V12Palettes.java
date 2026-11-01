package dev.dreamwalker.bloodbornedw.visual;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;

/** Recolour selected atlas pixels once, before mipmaps; every renderer shares the result. */
public final class V12Palettes {
    private record Colours(int[] red, int[] green, int[] blue) {
        int apply(int colour) {
            return (colour & 0xff000000) | red[colour & 255]
                | (green[(colour >>> 8) & 255] << 8) | (blue[(colour >>> 16) & 255] << 16);
        }
    }
    private record Region(int x0, int y0, int x1, int y1, Colours colours) {
        boolean contains(int x, int y) { return x >= x0 && x < x1 && y >= y0 && y < y1; }
    }
    private record Rule(Colours fallback, List<Region> regions) {
        Colours at(int x, int y) {
            for (Region region : regions) if (region.contains(x, y)) return region.colours;
            return fallback;
        }
    }
    private static final Map<Identifier, Rule> RULES = load();
    private V12Palettes() {}

    private static Map<Identifier, Rule> load() {
        String path = "/assets/bloodborne_dw/visuals/v12-palettes.json";
        try (var input = V12Palettes.class.getResourceAsStream(path)) {
            if (input == null) throw new IllegalStateException("Missing V12 palette rules");
            JsonObject root = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("schema").getAsInt() != 1) throw new IllegalArgumentException("V12 palette schema");
            Map<String, Colours> palettes = new HashMap<>();
            for (var entry : root.getAsJsonObject("palettes").entrySet()) {
                JsonObject json = entry.getValue().getAsJsonObject();
                JsonArray scale = json.getAsJsonArray("scale"), offset = json.getAsJsonArray("offset");
                int[][] channels = new int[3][256];
                if (scale.size() != 3 || offset.size() != 3) throw new IllegalArgumentException("V12 palette channels");
                for (int channel = 0; channel < 3; channel++) {
                    double multiplier = scale.get(channel).getAsDouble(), addition = offset.get(channel).getAsDouble();
                    if (!Double.isFinite(multiplier) || !Double.isFinite(addition) || multiplier < 0 || multiplier > 2 || Math.abs(addition) > 64)
                        throw new IllegalArgumentException("V12 palette range");
                    for (int value = 0; value < 256; value++) channels[channel][value] = Math.max(0, Math.min(255, (int)Math.round(value * multiplier + addition)));
                }
                palettes.put(entry.getKey(), new Colours(channels[0], channels[1], channels[2]));
            }
            Map<Identifier, Rule> rules = new HashMap<>();
            for (var raw : root.getAsJsonArray("sprites")) {
                JsonObject json = raw.getAsJsonObject();
                Colours fallback = json.has("palette") ? required(palettes, json.get("palette").getAsString()) : null;
                List<Region> regions = new ArrayList<>();
                if (json.has("regions")) for (var region : json.getAsJsonArray("regions")) {
                    JsonObject value = region.getAsJsonObject(); JsonArray box = value.getAsJsonArray("pixels");
                    if (box.size() != 4) throw new IllegalArgumentException("V12 palette rectangle");
                    int x0 = box.get(0).getAsInt(), y0 = box.get(1).getAsInt(), x1 = box.get(2).getAsInt(), y1 = box.get(3).getAsInt();
                    if (x0 < 0 || y0 < 0 || x1 <= x0 || y1 <= y0) throw new IllegalArgumentException("V12 palette rectangle bounds");
                    regions.add(new Region(x0, y0, x1, y1, required(palettes, value.get("palette").getAsString())));
                }
                if (fallback == null && regions.isEmpty()) throw new IllegalArgumentException("Empty V12 palette rule");
                if (rules.put(new Identifier(json.get("sprite").getAsString()), new Rule(fallback, List.copyOf(regions))) != null)
                    throw new IllegalArgumentException("Duplicate V12 sprite palette");
            }
            return Map.copyOf(rules);
        } catch (Exception failure) { throw new IllegalStateException("Invalid V12 palette rules", failure); }
    }
    private static Colours required(Map<String, Colours> palettes, String name) {
        Colours result = palettes.get(name);
        if (result == null) throw new IllegalArgumentException("Unknown V12 palette: " + name);
        return result;
    }
    public static void apply(Identifier id, NativeImage image) {
        Rule rule = RULES.get(id);
        if (rule == null || !NativeWallVisuals.loadingProfileIsAlt()) return;
        if (image.getFormat() != NativeImage.Format.RGBA) return;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int colour = image.getColor(x, y);
            if ((colour >>> 24) == 0) continue;
            Colours colours = rule.at(x, y);
            if (colours != null) image.setColor(x, y, colours.apply(colour));
        }
    }
}
