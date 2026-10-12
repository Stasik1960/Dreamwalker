package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import dev.dreamwalker.bloodbornedw.debug.DebugCatalogue;
import net.minecraft.util.Identifier;

/** NEW-only catalogue retirement. Registrations, saved instances and reserved IDs remain intact. */
public final class ImportedStyleRetirements {
    private static final Set<String> RETIRED = load();
    private ImportedStyleRetirements() {}

    private static String checkedImported(String value) {
        if (!value.startsWith("bloodborne_dw:source_") || Identifier.tryParse(value) == null)
            throw new IllegalArgumentException("Style retirement cannot change V12/foreign content: " + value);
        return value;
    }
    private static Set<String> load() {
        var input = ImportedStyleRetirements.class.getResourceAsStream("/bloodborne_dw/imported-style-retirements.json");
        if (input == null) throw new IllegalStateException("Missing reviewed NEW-only style retirement metadata");
        try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            JsonObject document = JsonParser.parseReader(reader).getAsJsonObject();
            if (document.get("schemaVersion").getAsInt() != 1
                    || !document.get("scope").getAsString().equals("POST_ORIGINAL_V12_ONLY"))
                throw new IllegalArgumentException("Unsupported style retirement metadata");
            Map<String, DebugCatalogue.Entry> known = new HashMap<>();
            for (var entry : DebugCatalogue.entries()) known.put(entry.registryId().toString(), entry);
            Set<String> protectedOriginals = new HashSet<>();
            for (JsonElement element : document.getAsJsonArray("protectedOriginalRegistryIds")) {
                String id = element.getAsString();
                if (!known.containsKey(id) || id.startsWith("bloodborne_dw:source_") || !protectedOriginals.add(id))
                    throw new IllegalArgumentException("Invalid protected original V12 registry: " + id);
            }
            if (protectedOriginals.size() != 116) throw new IllegalArgumentException("Original V12 catalogue protection is incomplete");
            Set<String> result = new LinkedHashSet<>();
            for (JsonElement element : document.getAsJsonArray("retirements")) {
                JsonObject row = element.getAsJsonObject();
                String registry = checkedImported(row.get("registryId").getAsString());
                String number = row.get("numericId").getAsString();
                var entry = known.get(registry);
                if (!number.matches("[0-9]{5}") || entry == null || !entry.temporaryId().equals(number)
                        || protectedOriginals.contains(registry)
                        || !ImportedTypeAliases.canonicalRegistry(registry).equals(registry)
                        || !result.add(registry) || row.get("reason").getAsString().isBlank())
                    throw new IllegalArgumentException("Invalid/repeated NEW style retirement: " + registry);
                if (row.has("aliases")) for (JsonElement alias : row.getAsJsonArray("aliases")) {
                    String old = checkedImported(alias.getAsString());
                    if (!known.containsKey(old) || protectedOriginals.contains(old)
                            || !ImportedTypeAliases.canonicalRegistry(old).equals(registry))
                        throw new IllegalArgumentException("Style alias disagrees with preserved type mapping: " + old);
                }
            }
            return Set.copyOf(result);
        } catch (Exception failure) {
            throw new IllegalStateException("Invalid reviewed NEW-only style retirements", failure);
        }
    }
    public static boolean retired(Identifier registry) { return retired(registry.toString()); }
    public static boolean retired(String registry) {
        return registry != null && registry.startsWith("bloodborne_dw:source_")
                && RETIRED.contains(ImportedTypeAliases.canonicalRegistry(registry));
    }
    public static Set<String> primaryRegistries() { return RETIRED; }
}
