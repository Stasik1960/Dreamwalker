import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;

/**
 * Test-only verifier for the exact 1.20.1 singleplayer route:
 * LevelStorage.create(saves).getLevelList(), then loadSummaries(levelList).
 * A summary is present only after vanilla has accepted its summary metadata.
 */
public final class LevelMetadataCheck {
    private LevelMetadataCheck() { }

    public static void main(String[] arguments) throws Exception {
        Map<String, String> options = parse(arguments);
        Path saves = requiredPath(options, "--saves");
        Path report = requiredPath(options, "--report").toAbsolutePath();
        Set<String> expectedValid = names(options.get("--expected-valid"));
        Set<String> expectedInvalid = names(options.get("--expected-invalid"));
        Set<String> expectedStable = names(options.get("--expected-stable"));
        if (!Files.isDirectory(saves)) throw new IllegalArgumentException("--saves is not a directory: " + saves);
        initializeVanillaVersion();

        List<String> lines = new ArrayList<>();
        lines.add("official route: LevelStorage.create(saves).getLevelList() -> loadSummaries(levelList)");
        lines.add("saves: " + saves.toAbsolutePath());
        Map<String, Object> summaries = loadSummaries(saves);
        lines.add("summaries: " + summaries.keySet());
        for (Path child : children(saves)) lines.add(describe(child, summaries.containsKey(child.getFileName().toString())));

        check(expectedValid, summaries, true);
        check(expectedInvalid, summaries, false);
        checkStable(expectedStable, summaries);
        Files.createDirectories(report.toAbsolutePath().getParent());
        Files.write(report, lines);
        System.out.println(String.join(System.lineSeparator(), lines));
    }

    private static Map<String, Object> loadSummaries(Path saves) throws Exception {
        Class<?> storageType = Class.forName("net.minecraft.world.level.storage.LevelStorage");
        Object storage = storageType.getMethod("create", Path.class).invoke(null, saves);
        try {
            Object levelList = storageType.getMethod("getLevelList").invoke(storage);
            Object future = storageType.getMethod("loadSummaries", levelList.getClass()).invoke(storage, levelList);
            Collection<?> list = (Collection<?>) future.getClass().getMethod("get").invoke(future);
            Map<String, Object> result = new LinkedHashMap<>();
            for (Object summary : list) result.put(String.valueOf(summary.getClass().getMethod("getName").invoke(summary)), summary);
            return result;
        } catch (InvocationTargetException exception) {
            throw rethrow("Vanilla LevelStorage.getLevelList failed", exception);
        } finally {
            if (storage instanceof AutoCloseable closeable) closeable.close();
        }
    }

    private static void initializeVanillaVersion() throws ReflectiveOperationException {
        Class.forName("net.minecraft.SharedConstants").getMethod("createGameVersion").invoke(null);
    }

    private static String describe(Path world, boolean listed) {
        Path levelDat = world.resolve("level.dat");
        return world.getFileName() + ": level.dat=" + Files.exists(levelDat) + metadata(levelDat) + ", vanilla-summary=" + listed
            + (listed ? " (accepted by vanilla summary loader)" : " (omitted by vanilla loader)");
    }

    private static String metadata(Path levelDat) {
        if (!Files.isRegularFile(levelDat)) return "";
        try {
            NbtCompound data = NbtIo.readCompressed(levelDat.toFile()).getCompound("Data");
            NbtCompound version = data.getCompound("Version");
            return ", Data.version=" + (data.contains("version", NbtElement.INT_TYPE) ? data.getInt("version") : "<missing>")
                + ", DataVersion=" + (data.contains("DataVersion", NbtElement.INT_TYPE) ? data.getInt("DataVersion") : "<missing>")
                + ", Version.Id=" + (version.contains("Id", NbtElement.INT_TYPE) ? version.getInt("Id") : "<missing>");
        } catch (IOException exception) {
            return ", metadata-read-error=" + exception.getClass().getSimpleName();
        }
    }

    private static void check(Set<String> expected, Map<String, Object> actual, boolean present) throws Exception {
        for (String name : expected) {
            Object summary = actual.get(name);
            boolean found = summary != null;
            if (found != present) throw new AssertionError(name + (present ? " was omitted by vanilla" : " was accepted by vanilla"));
            if (found && present) {
                Class<?> type = summary.getClass();
                boolean unavailable = (boolean) type.getMethod("isUnavailable").invoke(summary);
                boolean versionAvailable = (boolean) type.getMethod("isVersionAvailable").invoke(summary);
                if (unavailable || !versionAvailable) throw new AssertionError(name + " was listed but unavailable: unavailable="
                    + unavailable + ", versionAvailable=" + versionAvailable);
            }
        }
    }

    private static void checkStable(Set<String> expected, Map<String, Object> actual) throws Exception {
        for (String name : expected) {
            Object summary = actual.get(name);
            if (summary == null) throw new AssertionError(name + " was omitted by vanilla");
            boolean experimental = (boolean) summary.getClass().getMethod("isExperimental").invoke(summary);
            if (experimental) throw new AssertionError(name + " was accepted but marked experimental by vanilla");
        }
    }

    private static List<Path> children(Path root) throws IOException {
        try (var paths = Files.list(root)) { return paths.filter(Files::isDirectory).sorted().toList(); }
    }

    private static Set<String> names(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        return new LinkedHashSet<>(Arrays.asList(csv.split(",")));
    }

    private static Path requiredPath(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + name);
        return Path.of(value);
    }

    private static Map<String, String> parse(String[] arguments) {
        if ((arguments.length & 1) != 0) throw new IllegalArgumentException("Options must be --name value pairs");
        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index < arguments.length; index += 2) values.put(arguments[index], arguments[index + 1]);
        return values;
    }

    private static Exception rethrow(String message, InvocationTargetException exception) throws Exception {
        Throwable cause = exception.getCause();
        if (cause instanceof Exception checked) return checked;
        if (cause instanceof Error error) throw error;
        return new IllegalStateException(message, cause);
    }
}
