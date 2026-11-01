package dev.dreamwalker.bloodbornedw.visual;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class CityVisualResources {
    private static final class Profiles {
        static final JsonObject DATA = load();
        private static JsonObject load() {
            try (InputStream stream = CityVisualResources.class.getResourceAsStream("/assets/bloodborne_dw/visuals/profile-fingerprints.json")) {
                if (stream == null) throw new IllegalStateException("Missing city visual fingerprints");
                JsonObject data = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                if (data.get("schema").getAsInt() != 1) throw new IllegalStateException("Invalid city visual fingerprint schema");
                for (String key : new String[]{"original", "alt"}) {
                    if (!data.get(key).getAsString().matches("[0-9a-f]{64}")) throw new IllegalStateException("Invalid city visual fingerprint");
                }
                return data;
            } catch (Exception e) { throw new IllegalStateException("Cannot load city visual fingerprints", e); }
        }
    }
    public static String fingerprint(boolean alt) { return Profiles.DATA.get(alt ? "alt" : "original").getAsString(); }
    private CityVisualResources() {}
}
