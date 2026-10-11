package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;

/** Reviewed NEW-only type/state equivalences. Original V12 IDs never enter this map. */
public final class ImportedTypeAliases {
    public static final int VERSION = 1;
    public static final String VERSION_KEY = "ImportedTypeVersion";
    public static final String ROTATION_ART_KEY = "imported_rotation_delta";
    public static final String SHIFT_ART_KEY = "ImportedArtShift";
    public record Mapping(String oldId, String oldRegistryId, int oldVariant,
            String newId, String newRegistryId, int newVariant, int rotationDelta,
            CompositeSourceShift.Shift sourceShift, Map<String, String> properties) {
        public Mapping { properties = Collections.unmodifiableMap(new TreeMap<>(properties)); }
    }
    private record Key(String registry, int variant) {}
    private record Catalogue(Map<String, String> canonical, Map<Key, Mapping> mappings,
            Set<String> members, Set<String> oneItem) {}
    private static final Catalogue CATALOGUE = load();
    private ImportedTypeAliases() {}

    private static boolean imported(String registry) {
        return registry != null && registry.startsWith("bloodborne_dw:source_");
    }
    private static String checkedRegistry(String registry) {
        if (!imported(registry) || Identifier.tryParse(registry) == null)
            throw new IllegalArgumentException("Type equivalence cannot change V12/foreign registry: " + registry);
        return registry;
    }
    private static String checkedNumber(String number) {
        if (!number.matches("[0-9]{5}")) throw new IllegalArgumentException("Invalid reserved type number: " + number);
        return number;
    }
    private static Catalogue load() {
        var input = ImportedTypeAliases.class.getResourceAsStream("/bloodborne_dw/imported-type-aliases.json");
        // Source edits can precede generation. Release validation requires the manifest.
        if (input == null) return new Catalogue(Map.of(), Map.of(), Set.of(), Set.of());
        try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.get("schemaVersion").getAsInt() != 1) throw new IllegalArgumentException("Unsupported imported type equivalence schema");
            Map<String, String> canonical = new LinkedHashMap<>();
            Map<Key, Mapping> mappings = new LinkedHashMap<>();
            Set<String> members = new LinkedHashSet<>(), oneItem = new LinkedHashSet<>();
            for (JsonElement element : root.getAsJsonArray("groups")) {
                JsonObject group = element.getAsJsonObject();
                checkedNumber(group.get("primaryId").getAsString());
                String primary = checkedRegistry(group.get("primaryRegistryId").getAsString());
                boolean single = group.has("oneItem") && group.get("oneItem").getAsBoolean();
                boolean foundPrimary = false;
                for (JsonElement raw : group.getAsJsonArray("members")) {
                    String member = checkedRegistry(raw.getAsString());
                    if (canonical.putIfAbsent(member, primary) != null) throw new IllegalArgumentException("Repeated imported group member: " + member);
                    members.add(member); if (single) oneItem.add(member);
                    foundPrimary |= member.equals(primary);
                }
                if (!foundPrimary) throw new IllegalArgumentException("Primary is absent from imported group: " + primary);
            }
            for (JsonElement element : root.getAsJsonArray("mappings")) {
                JsonObject row = element.getAsJsonObject();
                String oldId = checkedNumber(row.get("oldId").getAsString()), newId = checkedNumber(row.get("newId").getAsString());
                String oldRegistry = checkedRegistry(row.get("oldRegistryId").getAsString()), nextRegistry = checkedRegistry(row.get("newRegistryId").getAsString());
                int oldVariant = row.get("oldVariant").getAsInt(), nextVariant = row.get("newVariant").getAsInt(), delta = row.get("rotationDelta").getAsInt();
                if (oldVariant < 0 || oldVariant > 15 || nextVariant < 0 || nextVariant > 15 || delta < 0 || delta > 7)
                    throw new IllegalArgumentException("Imported state equivalence out of range: " + oldRegistry);
                if (!nextRegistry.equals(canonical.get(oldRegistry))) throw new IllegalArgumentException("State mapping disagrees with group primary: " + oldRegistry);
                JsonArray shift = row.getAsJsonArray("sourceShift");
                if (shift.size() != 3) throw new IllegalArgumentException("Three canonical source-shift coordinates required");
                CompositeSourceShift.Shift vector = new CompositeSourceShift.Shift(shift.get(0).getAsDouble(), shift.get(1).getAsDouble(), shift.get(2).getAsDouble());
                Map<String, String> properties = new TreeMap<>();
                if (row.has("properties")) for (var entry : row.getAsJsonObject("properties").entrySet()) properties.put(entry.getKey(), entry.getValue().getAsString());
                Mapping mapping = new Mapping(oldId, oldRegistry, oldVariant, newId, nextRegistry, nextVariant, delta, vector, properties);
                if (mappings.putIfAbsent(new Key(oldRegistry, oldVariant), mapping) != null) throw new IllegalArgumentException("Repeated exact imported state mapping: " + oldRegistry + "/" + oldVariant);
            }
            for (var entry : canonical.entrySet()) if (!entry.getValue().equals(canonical.get(entry.getValue())))
                throw new IllegalArgumentException("Imported primary cannot itself be another group alias: " + entry.getValue());
            return new Catalogue(Collections.unmodifiableMap(canonical), Collections.unmodifiableMap(mappings), Set.copyOf(members), Set.copyOf(oneItem));
        } catch (Exception failure) { throw new IllegalStateException("Invalid imported type equivalence manifest", failure); }
    }

    public static String canonicalRegistry(String registry) {
        return imported(registry) ? CATALOGUE.canonical.getOrDefault(registry, registry) : registry;
    }
    public static boolean equivalent(String first, String second) {
        return Objects.equals(first, second) || imported(first) && imported(second)
                && canonicalRegistry(first).equals(canonicalRegistry(second));
    }
    public static boolean merged(String registry) { return imported(registry) && CATALOGUE.members.contains(registry); }
    public static boolean singleItem(String registry) { return imported(registry) && CATALOGUE.oneItem.contains(registry); }
    public static Optional<Mapping> mapping(String registry, int variant) { return Optional.ofNullable(CATALOGUE.mappings.get(new Key(registry, variant))); }
    public static List<Mapping> mappings() { return List.copyOf(CATALOGUE.mappings.values()); }
    public static Optional<Mapping> mapping(BlockState state) {
        if (!(state.getBlock() instanceof CompositeRootBlock block) || !imported(block.spec.id.toString())) return Optional.empty();
        return mapping(block.spec.id.toString(), state.get(CompositeRootBlock.VARIANT));
    }
    public static boolean needsMigration(BlockState state, NbtCompound payload) {
        Optional<Mapping> mapped = mapping(state);
        return mapped.isPresent() && (!mapped.get().oldRegistryId.equals(mapped.get().newRegistryId)
                || payload.getInt(VERSION_KEY) < VERSION);
    }
    public static NbtCompound markPayload(NbtCompound supplied) {
        NbtCompound copy = supplied == null ? new NbtCompound() : supplied.copy(); copy.putInt(VERSION_KEY, VERSION); return copy;
    }
    public static BlockState target(BlockState old) {
        Mapping mapping = mapping(old).orElse(null); if (mapping == null) return old;
        Identifier id = new Identifier(mapping.newRegistryId);
        if (!Registries.BLOCK.containsId(id) || !(Registries.BLOCK.get(id) instanceof CompositeRootBlock))
            throw new IllegalStateException("Missing canonical imported block: " + id);
        BlockState next = Registries.BLOCK.get(id).getDefaultState();
        for (var entry : old.getEntries().entrySet()) {
            Property<?> property = next.getBlock().getStateManager().getProperty(entry.getKey().getName());
            if (property != null) next = parsed(next, property, propertyName(entry.getKey(), entry.getValue()), false);
        }
        next = next.with(CompositeRootBlock.VARIANT, mapping.newVariant)
                .with(CompositeRootBlock.ROTATION, (old.get(CompositeRootBlock.ROTATION) + mapping.rotationDelta) & 7);
        for (var entry : mapping.properties.entrySet()) {
            Property<?> property = next.getBlock().getStateManager().getProperty(entry.getKey());
            if (property == null) throw new IllegalStateException("Missing canonical imported state property: " + id + "/" + entry.getKey());
            next = parsed(next, property, entry.getValue(), true);
        }
        return ImportedStairShapes.normalize(next);
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String propertyName(Property property, Comparable value) { return property.name(value); }
    private static <T extends Comparable<T>> BlockState parsed(BlockState state, Property<T> property, String value, boolean required) {
        Optional<T> parsed = property.parse(value);
        if (parsed.isPresent()) return state.with(property, parsed.get());
        if (required) throw new IllegalStateException("Invalid canonical imported state property: " + property.getName() + "=" + value);
        return state;
    }
    /** Manifest vector is already canonical-local; rotate the prior local shift back once. */
    public static NbtCompound transformPayload(BlockState old, NbtCompound supplied) {
        Mapping mapping = mapping(old).orElse(null); NbtCompound copy = supplied == null ? new NbtCompound() : supplied.copy();
        if (mapping == null) return copy;
        CompositeSourceShift.Shift prior = CompositeSourceShift.local(copy).rotate(-mapping.rotationDelta);
        CompositeSourceShift.Shift shift = mapping.sourceShift;
        writeShift(copy, "SourceShift", new CompositeSourceShift.Shift(prior.x() + shift.x(), prior.y() + shift.y(), prior.z() + shift.z()));
        copy.putInt(VERSION_KEY, VERSION); return copy;
    }
    public static ItemStack canonicalStack(ItemStack old) {
        if (old.isEmpty() || !(old.getItem() instanceof BlockItem item)) return old;
        String registry = Registries.BLOCK.getId(item.getBlock()).toString();
        if (!merged(registry)) return old;
        boolean alias = !canonicalRegistry(registry).equals(registry);
        if (!alias && old.hasNbt() && old.getNbt().getInt(VERSION_KEY) >= VERSION) return old;
        NbtCompound art = old.getSubNbt("BlockStateTag"); int variant = integer(art, "variant", 0);
        Mapping mapping = mapping(registry, variant).orElse(null);
        if (mapping == null) {
            if (!alias) return old; // Newly introduced canonical banks have no historical source state.
            throw new IllegalStateException("Unmapped legacy imported item: " + registry + "/" + variant);
        }
        Identifier canonical = new Identifier(mapping.newRegistryId);
        if (!Registries.ITEM.containsId(canonical) || !(Registries.ITEM.get(canonical) instanceof BlockItem))
            throw new IllegalStateException("Missing canonical imported item: " + canonical);
        ItemStack next = new ItemStack(Registries.ITEM.get(canonical), old.getCount());
        if (old.hasNbt()) next.setNbt(old.getNbt().copy());
        NbtCompound tag = next.getOrCreateSubNbt("BlockStateTag");
        tag.putString("variant", Integer.toString(mapping.newVariant)); mapping.properties.forEach(tag::putString);
        int delta = (integer(art, ROTATION_ART_KEY, 0) + mapping.rotationDelta) & 7;
        tag.putString(ROTATION_ART_KEY, Integer.toString(delta));
        if (tag.contains("rotation", NbtElement.STRING_TYPE)) tag.putString("rotation", Integer.toString((integer(art, "rotation", 0) + mapping.rotationDelta) & 7));
        CompositeSourceShift.Shift previous = readArtShift(next.getNbt()).rotate(-mapping.rotationDelta);
        CompositeSourceShift.Shift shift = mapping.sourceShift;
        writeShift(next.getOrCreateNbt(), SHIFT_ART_KEY, new CompositeSourceShift.Shift(previous.x() + shift.x(), previous.y() + shift.y(), previous.z() + shift.z()));
        next.getOrCreateNbt().putInt(VERSION_KEY, VERSION);
        return ItemStack.areEqual(old, next) ? old : next;
    }
    private static int integer(NbtCompound tag, String key, int fallback) {
        if (tag == null || !tag.contains(key, NbtElement.STRING_TYPE)) return fallback;
        try { return Integer.parseInt(tag.getString(key)); } catch (NumberFormatException ignored) { return fallback; }
    }
    public static CompositeSourceShift.Shift readArtShift(NbtCompound tag) {
        if (tag == null || !tag.contains(SHIFT_ART_KEY)) return new CompositeSourceShift.Shift(0, 0, 0);
        NbtList values = tag.getList(SHIFT_ART_KEY, NbtElement.DOUBLE_TYPE);
        if (values.size() != 3) throw new IllegalArgumentException("Three imported item art-shift doubles required");
        return new CompositeSourceShift.Shift(values.getDouble(0), values.getDouble(1), values.getDouble(2));
    }
    private static void writeShift(NbtCompound payload, String key, CompositeSourceShift.Shift shift) {
        if (shift.x() == 0 && shift.y() == 0 && shift.z() == 0) { payload.remove(key); return; }
        NbtList values = new NbtList(); values.add(NbtDouble.of(shift.x())); values.add(NbtDouble.of(shift.y())); values.add(NbtDouble.of(shift.z())); payload.put(key, values);
    }
}
