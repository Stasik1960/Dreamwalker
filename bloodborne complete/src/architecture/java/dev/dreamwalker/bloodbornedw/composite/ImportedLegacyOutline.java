package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Footprint;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

/** Exact pre-simplification aiming for NEW objects, independent of their physics.
 * Original box lists are packaged data, not inferred from artwork. Hidden specs
 * are never registered and deliberately use the unchanged legacy distribution
 * and SourceShift paths. No World/BE access is needed by shape workers.
 */
public final class ImportedLegacyOutline {
    private static final Map<String, JsonObject> ORIGINALS = originals();
    private static final Map<CompositeSpec, CompositeSpec> SPECS = new IdentityHashMap<>();
    private ImportedLegacyOutline() {}

    private static Map<String, JsonObject> originals() {
        try (InputStream input = ImportedLegacyOutline.class.getResourceAsStream("/bloodborne_dw/imported-legacy-outlines.json.gz")) {
            if (input == null) throw new IllegalStateException("Missing original imported aiming data");
            JsonObject document = JsonParser.parseReader(new InputStreamReader(new GZIPInputStream(input), StandardCharsets.UTF_8)).getAsJsonObject();
            if (document.get("schemaVersion").getAsInt() != 1 || document.get("units").getAsInt() != 16) throw new IllegalArgumentException("Unsupported original imported aiming schema");
            Map<String, JsonObject> result = new HashMap<>();
            for (JsonElement value : document.getAsJsonArray("objects")) {
                JsonObject row = value.getAsJsonObject(); String id = row.get("id").getAsString();
                if (!id.startsWith("bloodborne_dw:source_") || result.put(id, row) != null) throw new IllegalArgumentException("Invalid original imported aiming ID " + id);
            }
            return Map.copyOf(result);
        } catch (IOException failure) { throw new IllegalStateException("Cannot load original imported aiming data", failure); }
    }
    private static synchronized CompositeSpec outline(CompositeSpec actual) {
        CompositeSpec known = SPECS.get(actual); if (known != null) return known;
        if (!actual.simplePhysicalCollision) throw new IllegalArgumentException("Original aiming is restricted to newly imported objects");
        JsonObject original = ORIGINALS.get(actual.id.toString());
        if (original == null) throw new IllegalStateException("Missing original aiming for " + actual.id);
        JsonArray originalVariants = original.getAsJsonArray("variants");
        if (originalVariants.size() != actual.variants.size()) throw new IllegalArgumentException("Original aiming variant mismatch: " + actual.id);
        JsonObject document = new JsonObject();
        document.addProperty("schemaVersion", 1); document.addProperty("units", 16); document.addProperty("id", actual.id.toString());
        JsonArray variants = new JsonArray();
        for (int index = 0; index < originalVariants.size(); index++) {
            JsonObject row = new JsonObject(), poses = new JsonObject();
            for (var entry : originalVariants.get(index).getAsJsonObject().getAsJsonObject("poses").entrySet()) {
                CompositeSpec.Pose current = entry.getKey().equals("open") ? actual.variants.get(index).open() : actual.variants.get(index).closed();
                JsonObject pose = new JsonObject(); pose.add("parts", new JsonArray());
                pose.add("collision", entry.getValue().deepCopy());
                JsonArray selection = new JsonArray();
                for (CompositeSpec.Bounds bounds : current.selection()) {
                    JsonObject box = new JsonObject(); box.add("from", vector(bounds.from())); box.add("to", vector(bounds.to())); selection.add(box);
                }
                pose.add("selection", selection); poses.add(entry.getKey(), pose);
            }
            row.add("poses", poses); variants.add(row);
        }
        document.add("variants", variants);
        CompositeSpec result = CompositeSpec.legacyOutlineSpec(document);
        SPECS.put(actual, result); return result;
    }
    private static JsonArray vector(CompositeSpec.Vec value) {
        JsonArray result = new JsonArray(); result.add(value.x()); result.add(value.y()); result.add(value.z()); return result;
    }
    /** Present even when empty: an old empty aiming cell must stay empty. */
    public static Optional<List<Box>> selection(CompositeData.Contribution entry, BlockPos cell) {
        if (!ImportedPhysicalShapes.isImported(entry.owner())) return Optional.empty();
        BlockState state = CompositeData.state(entry.rootData());
        if (!(state.getBlock() instanceof CompositeRootBlock block) || !block.spec.id.toString().equals(entry.owner().registryId())) throw new IllegalStateException("Original aiming owner/state mismatch");
        CompositeSpec old = outline(block.spec);
        NbtCompound payload = CompositeData.nbt(entry.rootData().blockEntityNbt());
        Map<Cell, Footprint> footprint = CompositeSourceShift.footprint(old, state, payload);
        Footprint part = footprint.get(CompositeData.cell(cell).subtract(entry.owner().root()));
        if (part == null) return Optional.of(List.of());
        boolean physical = old.footprint(state, 0).values().stream().anyMatch(value -> !value.collision().isEmpty());
        return Optional.of(physical ? part.collision() : part.selection());
    }
    /** Preserve the prior unbound root fallback, which chooses its local shape. */
    public static List<Box> fallback(CompositeRootBlock block, BlockState state) {
        Footprint part = outline(block.spec).footprint(state, 0).get(Cell.ORIGIN);
        return part.collision().isEmpty() ? part.selection() : part.collision();
    }
}
