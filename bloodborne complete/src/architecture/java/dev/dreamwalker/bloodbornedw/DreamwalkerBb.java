package dev.dreamwalker.bloodbornedw;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import dev.dreamwalker.bloodbornedw.architecture.PrototypeArchitecture;
import dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallArchitecture;
import dev.dreamwalker.bloodbornedw.architecture.compat.SourceTechnicalLight;
import dev.dreamwalker.bloodbornedw.composite.CompositeArchitecture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Architecture and RP have separate source trees and exactly one entrypoint each. */
public final class DreamwalkerBb implements ModInitializer, PreLaunchEntrypoint {
    public static final String ID = "bloodborne_dw";
    public static final Logger LOG = LoggerFactory.getLogger(ID);
    private static final String STANDALONE_RP_ID = "bloodborne_rp";
    private static final int MAX_METADATA_BYTES = 4 * 1024 * 1024;
    private static final String REMOVE_STANDALONE = "dreamwalker-bb includes Bloodborne RP. Remove standalone bloodborne-rp.jar before starting.";

    @Override public void onPreLaunch() { rejectStandaloneRp(); }

    @Override public void onInitialize() {
        rejectStandaloneRp();
        LOG.info("Dreamwalker BB source-backed prototype checkpoint; final catalog IDs are not frozen");
        SourceTechnicalLight.initialize();
        PrototypeArchitecture.initialize();
        PrototypeWallArchitecture.initialize();
        CompositeArchitecture.initialize("prototype_double_door", "prototype_wood_window", "prototype_thin_window", "prototype_tree", "prototype_roof");
    }

    /** Loader may discard a real standalone candidate because this mod provides its ID. */
    private static void rejectStandaloneRp() {
        FabricLoader loader = FabricLoader.getInstance();
        if (loader.getAllMods().stream().anyMatch(mod -> mod.getMetadata().getId().equals(STANDALONE_RP_ID)))
            throw new IllegalStateException(REMOVE_STANDALONE + " Active mod ID: " + STANDALONE_RP_ID);

        // Match Fabric's configured root mods folder and discovery rules, not a guessed RP filename.
        String configured = System.getProperty("fabric.modsFolder");
        Path mods = (configured == null ? loader.getGameDir().resolve("mods") : Path.of(configured)).toAbsolutePath().normalize();
        if (Files.notExists(mods)) return; // Normal for an empty development/runtime profile.
        List<Path> candidates = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(mods)) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                if (!name.endsWith(".jar") || name.startsWith(".")) continue;
                if (Files.readAttributes(file, BasicFileAttributes.class).isRegularFile() && !Files.isHidden(file)) candidates.add(file);
            }
        } catch (IOException | DirectoryIteratorException | SecurityException failure) {
            throw new IllegalStateException("Cannot verify Bloodborne RP exclusivity in mods directory " + mods + ": " + failure.getMessage(), failure);
        }
        candidates.sort(Comparator.comparing(Path::toString));
        for (Path jar : candidates) {
            final String modId;
            try { modId = readModId(jar); }
            catch (IOException | SecurityException failure) {
                throw new IllegalStateException("Cannot verify Bloodborne RP exclusivity: unreadable or invalid fabric.mod.json in " + jar + ": " + failure.getMessage(), failure);
            }
            if (STANDALONE_RP_ID.equals(modId)) throw new IllegalStateException(REMOVE_STANDALONE + " Offending mod ID: " + modId + "; file: " + jar);
        }
    }

    private static String readModId(Path jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry metadata = zip.getEntry("fabric.mod.json");
            if (metadata == null) return null; // Libraries and non-Fabric archives are outside this guard.
            if (metadata.isDirectory() || zip.stream().filter(entry -> entry.getName().equals("fabric.mod.json")).count() != 1)
                throw new IOException("Expected one regular root fabric.mod.json entry");
            byte[] bytes;
            try (var input = zip.getInputStream(metadata)) { bytes = input.readNBytes(MAX_METADATA_BYTES + 1); }
            if (bytes.length > MAX_METADATA_BYTES) throw new IOException("fabric.mod.json exceeds the 4 MiB verification limit");
            var decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
            try (JsonReader reader = new JsonReader(new InputStreamReader(new ByteArrayInputStream(bytes), decoder))) {
                reader.setLenient(false);
                if (reader.peek() != JsonToken.BEGIN_OBJECT) throw new IOException("fabric.mod.json must be a JSON object");
                reader.beginObject(); String id = null;
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (name.equals("id")) {
                        if (id != null || reader.peek() != JsonToken.STRING) throw new IOException("Expected one string mod ID");
                        id = reader.nextString();
                    } else reader.skipValue();
                }
                reader.endObject();
                if (reader.peek() != JsonToken.END_DOCUMENT || id == null || id.isEmpty()) throw new IOException("Missing mod ID or trailing JSON content");
                return id;
            }
        }
    }
}
