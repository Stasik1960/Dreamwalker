import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Scratch checks against the final ordinary JAR, without starting Minecraft or changing source. */
public final class GuardMetadataChecks {
    private static Method read;
    private static int checked;
    public static void main(String[] args) throws Exception {
        read = Class.forName("dev.dreamwalker.bloodbornedw.DreamwalkerBb").getDeclaredMethod("readModId", Path.class);
        read.setAccessible(true);
        Path directory = Path.of(args[0]); Files.createDirectories(directory);
        valid(directory, "renamed-ordinary-mod.jar", "{\"id\":\"bloodborne_rp\",\"custom\":[1,{\"nested\":true}]}", "bloodborne_rp");
        valid(directory, "bloodborne-rp.jar", "{\"id\":\"unrelated\",\"custom\":{\"id\":\"bloodborne_rp\"}}", "unrelated");
        invalid(directory, "numeric-id.jar", "{\"id\":123}".getBytes(StandardCharsets.UTF_8));
        invalid(directory, "duplicate-id.jar", "{\"id\":\"unrelated\",\"id\":\"bloodborne_rp\"}".getBytes(StandardCharsets.UTF_8));
        invalid(directory, "missing-id.jar", "{\"name\":\"no id\"}".getBytes(StandardCharsets.UTF_8));
        invalid(directory, "malformed-json.jar", "{\"id\":\"bloodborne_rp\",bad}".getBytes(StandardCharsets.UTF_8));
        invalid(directory, "malformed-utf8.jar", new byte[] {123, 34, 105, 100, 34, 58, 34, (byte) 255, 34, 125});
        invalid(directory, "oversized-metadata.jar", ("{\"id\":\"unrelated\",\"custom\":\"" + "x".repeat(4 * 1024 * 1024) + "\"}").getBytes(StandardCharsets.UTF_8));
        Path library = directory.resolve("library.jar");
        try (var zip = new ZipOutputStream(Files.newOutputStream(library))) { zip.putNextEntry(new ZipEntry("library.txt")); zip.write(1); zip.closeEntry(); }
        if (invoke(library) != null) throw new AssertionError("non-Fabric libraries must remain outside the guard");
        checked++; System.out.println("PASS non-Fabric library ignored");
        System.out.println("PASS " + checked + " final-JAR metadata guard checks");
    }
    private static void valid(Path directory, String name, String json, String expected) throws Exception {
        Path jar = directory.resolve(name); write(jar, json.getBytes(StandardCharsets.UTF_8));
        if (!expected.equals(invoke(jar))) throw new AssertionError("exact metadata ID mismatch for " + name);
        checked++; System.out.println("PASS exact metadata ID: " + name);
    }
    private static void invalid(Path directory, String name, byte[] json) throws Exception {
        Path jar = directory.resolve(name); write(jar, json);
        try { invoke(jar); throw new AssertionError("metadata violation was swallowed: " + name); }
        catch (IOException expected) { checked++; System.out.println("PASS explicit metadata rejection: " + name); }
    }
    private static String invoke(Path jar) throws Exception {
        try { return (String) read.invoke(null, jar); }
        catch (InvocationTargetException wrapper) {
            if (wrapper.getCause() instanceof Exception exception) throw exception;
            throw wrapper;
        }
    }
    private static void write(Path jar, byte[] json) throws IOException {
        try (var zip = new ZipOutputStream(Files.newOutputStream(jar))) { zip.putNextEntry(new ZipEntry("fabric.mod.json")); zip.write(json); zip.closeEntry(); }
    }
}
