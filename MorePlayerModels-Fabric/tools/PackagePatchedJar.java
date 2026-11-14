import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

class PackagePatchedJar {
    private static final String METADATA = "fabric.mod.json";
    private static final LocalDateTime ENTRY_TIME = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final List<String> REPLACEMENT_CLASSES = List.of(
        "noppes/mpm/packets/Packets.class",
        "noppes/mpm/packets/client/ClientPackets.class"
    );

    public static void main(String[] args) throws IOException {
        if (args.length != 5) {
            throw new IllegalArgumentException("Expected input.jar classes-directory output.jar input-version output-version");
        }
        Path input = Path.of(args[0]);
        Path classes = Path.of(args[1]);
        Path output = Path.of(args[2]);
        String oldVersion = args[3];
        String newVersion = args[4];

        Map<String, Path> replacements = compiledClasses(classes);
        for (String required : REPLACEMENT_CLASSES) {
            if (!replacements.containsKey(required)) {
                throw new IOException("Missing compiled class " + required);
            }
        }

        try (ZipFile base = new ZipFile(input.toFile());
             ZipOutputStream target = new ZipOutputStream(Files.newOutputStream(output))) {
            Set<String> seen = new HashSet<>();
            for (ZipEntry entry : base.stream().toList()) {
                String name = entry.getName();
                if (!seen.add(name)) {
                    throw new IOException("Duplicate JAR entry " + name);
                }
                if (isSignature(name)) {
                    throw new IOException("Cannot rewrite signed JAR entry " + name);
                }
                if (isOriginalNestedClass(name) && !replacements.containsKey(name)) {
                    throw new IOException("Missing compiled replacement for " + name);
                }
                addEntry(target, name);
                Path replacement = replacements.remove(name);
                if (replacement != null) {
                    Files.copy(replacement, target);
                } else if (name.equals(METADATA)) {
                    try (InputStream metadata = base.getInputStream(entry)) {
                        target.write(updateVersion(metadata.readAllBytes(), oldVersion, newVersion));
                    }
                } else if (!entry.isDirectory()) {
                    try (InputStream resource = base.getInputStream(entry)) {
                        resource.transferTo(target);
                    }
                }
                target.closeEntry();
            }
            if (!seen.contains(METADATA) || !seen.containsAll(REPLACEMENT_CLASSES)) {
                throw new IOException("Input JAR does not contain the required mod metadata and classes");
            }
            for (var replacement : replacements.entrySet()) {
                addEntry(target, replacement.getKey());
                Files.copy(replacement.getValue(), target);
                target.closeEntry();
            }
        }
    }

    private static Map<String, Path> compiledClasses(Path classes) throws IOException {
        Map<String, Path> replacements = new TreeMap<>();
        try (var paths = Files.walk(classes)) {
            for (Path file : paths.filter(Files::isRegularFile).toList()) {
                String name = classes.relativize(file).toString().replace('\\', '/');
                if (!name.endsWith(".class") || !name.startsWith("noppes/mpm/")) {
                    throw new IOException("Unexpected compiler output " + name);
                }
                replacements.put(name, file);
            }
        }
        return replacements;
    }

    private static boolean isOriginalNestedClass(String name) {
        return REPLACEMENT_CLASSES.stream()
            .map(original -> original.substring(0, original.length() - ".class".length()))
            .anyMatch(original -> name.startsWith(original + "$") && name.endsWith(".class"));
    }

    private static boolean isSignature(String name) {
        return name.matches("(?i)META-INF/(SIG-[^/]+|[^/]+\\.(SF|RSA|DSA|EC))");
    }

    private static void addEntry(ZipOutputStream target, String name) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTimeLocal(ENTRY_TIME);
        target.putNextEntry(entry);
    }

    private static byte[] updateVersion(byte[] bytes, String previous, String next) throws IOException {
        String json = new String(bytes, StandardCharsets.UTF_8);
        Pattern versionEntry = Pattern.compile("(?m)^(\\h*\"version\"\\h*:\\h*\")" + Pattern.quote(previous) + "(\")");
        Matcher version = versionEntry.matcher(json);
        if (!version.find()) {
            throw new IOException("Missing base version " + previous + " in " + METADATA);
        }
        StringBuilder updated = new StringBuilder();
        version.appendReplacement(updated, Matcher.quoteReplacement(version.group(1) + next + version.group(2)));
        if (version.find()) {
            throw new IOException("Multiple matches for the base version in " + METADATA);
        }
        version.appendTail(updated);
        return updated.toString().getBytes(StandardCharsets.UTF_8);
    }
}
