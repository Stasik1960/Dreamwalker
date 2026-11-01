package dev.dreamwalker.bloodbornedw.visual;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

/** Startup hint only. The server still checks the actual installed resource version. */
final class CityVisualPreferences {
    private static Boolean savedAlt;
    private static String savedFingerprint;
    private static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("dw-city-visuals.json"); }
    static boolean lastAlt() {
        try {
            Path path = path();
            if (!Files.isRegularFile(path) || Files.size(path) > 1024) return false;
            JsonObject data = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            if (data.get("schema").getAsInt() != 1 || !data.get("fingerprint").getAsString().matches("[0-9a-f]{64}")) return false;
            savedAlt = data.get("alt").getAsBoolean(); savedFingerprint = data.get("fingerprint").getAsString();
            return savedAlt;
        } catch (Exception ignored) { return false; }
    }
    static void save(boolean alt) {
        String fingerprint = CityVisualResources.fingerprint(alt);
        if (savedAlt != null && savedAlt == alt && fingerprint.equals(savedFingerprint)) return;
        Path target = path(), temp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            JsonObject data = new JsonObject();
            data.addProperty("schema", 1); data.addProperty("alt", alt);
            data.addProperty("fingerprint", fingerprint);
            Files.createDirectories(target.getParent());
            Files.writeString(temp, data.toString(), StandardCharsets.UTF_8);
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
            savedAlt = alt; savedFingerprint = fingerprint;
        } catch (Exception failure) {
            LoggerFactory.getLogger("dw-city-visuals").warn("Cannot save city visual startup hint", failure);
            try { Files.deleteIfExists(temp); } catch (Exception ignored) { }
        }
    }
    private CityVisualPreferences() {}
}
