package dev.dreamwalker.bloodbornedw.editor.catalogue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import dev.dreamwalker.bloodbornedw.architecture.CatalogueMigration;
import dev.dreamwalker.bloodbornedw.architecture.RemainingArchitecture;
import dev.dreamwalker.bloodbornedw.composite.FenceCatalogue;
import dev.dreamwalker.bloodbornedw.composite.InvisibleSourceCarrierV127;
import dev.dreamwalker.bloodbornedw.composite.NativeGridBridge;
import dev.dreamwalker.bloodbornedw.composite.SourceFallbackCarrierV1271;
import dev.dreamwalker.bloodbornedw.composite.NativeGridRetainedWholeItems;
import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureCatalog;
import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureBlock;
import dev.dreamwalker.bloodbornedw.composite.MaterialSlabs;
import dev.dreamwalker.bloodbornedw.composite.StaticGrassMasonryBlock;
import dev.dreamwalker.bloodbornedw.composite.NativeGridBlock;
import dev.dreamwalker.bloodbornedw.composite.NativePhysicsBlock;
import dev.dreamwalker.bloodbornedw.composite.CompositeCellBlock;
import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import dev.dreamwalker.bloodbornedw.composite.ImportedArtworkBlock;
import dev.dreamwalker.bloodbornedw.composite.ImportedStairBlock;
import dev.dreamwalker.bloodbornedw.composite.ThinWindowRootBlock;
import dev.dreamwalker.bloodbornedw.composite.ImportedAssemblyV126;
import dev.dreamwalker.bloodbornedw.composite.ImportedTypeAliases;
import dev.dreamwalker.bloodbornedw.composite.ImportedStyleRetirements;
import dev.dreamwalker.bloodbornedw.debug.UnifiedCreativeCatalogue;
import dev.dreamwalker.bloodbornedw.editor.CityEditorServer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/** Adds registered, placeable canonical items and audits the resulting catalogue. */
public final class EditorCatalogue {
    private EditorCatalogue() {}

    private static boolean wetCompactWall(ItemStack stack) {
        if (!Registries.ITEM.getId(stack.getItem()).equals(new Identifier("bloodborne_dw", "source_assembly_brick_wall"))) return false;
        var tag = stack.getSubNbt("BlockStateTag");
        return tag != null && "true".equals(tag.getString("compact"))
            && "29".equals(tag.getString("art_bank")) && "6".equals(tag.getString("variant"));
    }
    private static boolean shiftedStatueInstallation(ItemStack stack) {
        if (!Registries.ITEM.getId(stack.getItem()).equals(new Identifier("bloodborne_dw", "source_hold_statue"))) return false;
        var root = stack.getNbt(); var art = stack.getSubNbt("BlockStateTag");
        if (root == null || art == null || !root.contains("ImportedTypeVersion", 3)
                || root.getInt("ImportedTypeVersion") != 1 || !root.contains("ImportedArtShift", 9)
                || !"base".equals(art.getString("profile")) || !"false".equals(art.getString("open"))) return false;
        int variant;
        try { variant = Integer.parseInt(art.getString("variant")); } catch (NumberFormatException invalid) { return false; }
        if (variant < 0 || variant > 3) return false;
        var shift = root.getList("ImportedArtShift", 6);
        double y = variant == 0 ? -1.0 : -0.982370625;
        return shift.size() == 3 && shift.getDouble(0) == 0.0 && shift.getDouble(1) == y && shift.getDouble(2) == 0.0;
    }
    /** Creative-only: ordinary dry placement uses the same artwork and shape.
     * Saved waterlogged source states retain their distinct fluid and optics. */
    private static ItemStack creativeStack(ItemStack stack) {
        if (shiftedStatueInstallation(stack)) {
            ItemStack installation = stack.copy();
            installation.getNbt().remove("ImportedArtShift");
            return installation;
        }
        if (!wetCompactWall(stack)) return stack;
        ItemStack dry = stack.copy();
        var tag = dry.getOrCreateSubNbt("BlockStateTag");
        tag.putString("art_bank", "11"); tag.putString("variant", "1");
        return dry;
    }
    private static String key(ItemStack stack) {
        stack = creativeStack(stack);
        return Registries.ITEM.getId(stack.getItem()) + "|" + (stack.hasNbt() ? stack.getNbt().toString() : "");
    }
    private static void add(List<ItemStack> result, Set<String> keys, ItemStack stack) {
        stack = creativeStack(stack);
        if (!stack.isEmpty() && keys.add(key(stack))) result.add(stack);
    }
    private static String technical(Block block) {
        if (block instanceof MaterialSlabs.Slab && MaterialSlabs.hasOverlay(block.getDefaultState())) return "combined slab and static city recipe storage; separately offered components audited";
        if (block instanceof StaticArchitectureBlock) return "finite static architecture recipe bank; source poses are audited separately";
        if (block instanceof NativeGridBlock || block instanceof NativePhysicsBlock || block instanceof CompositeCellBlock) return "internal physical or composite cell storage";
        if (block instanceof InvisibleSourceCarrierV127) return "invisible source storage carrier";
        if (block instanceof SourceFallbackCarrierV1271) return "physical source storage bank; individual source states need catalogue adapters";
        return null;
    }
    private static ItemStack canonical(ItemStack stack) {
        return FenceCatalogue.canonical(CatalogueMigration.canonicalStack(stack));
    }
    private record SourcePalette(List<String> states, List<String> targets) {}
    private static SourcePalette sourcePalette() {
        try (var stream = EditorCatalogue.class.getResourceAsStream("/bloodborne_dw/source-fallback-carriers-v1271.json")) {
            if (stream == null) throw new IllegalStateException("Missing source carrier palette");
            JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            List<String> states = new ArrayList<>(), targets = new ArrayList<>();
            json.getAsJsonArray("sourceStates").forEach(e -> states.add(e.getAsString()));
            json.getAsJsonArray("targetRegistryIds").forEach(e -> targets.add(e.getAsString()));
            if (states.size() != targets.size() || states.size() != SourceFallbackCarrierV1271.sourceStates().size())
                throw new IllegalStateException("Source carrier palette size mismatch");
            return new SourcePalette(List.copyOf(states), List.copyOf(targets));
        } catch (java.io.IOException e) { throw new IllegalStateException("Cannot read source carrier palette", e); }
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String propertyValue(Property property, Comparable value) { return property.name(value); }
    private static String sourceIdentity(BlockState state) {
        String id = Registries.BLOCK.getId(state.getBlock()).toString();
        if (state.getEntries().isEmpty()) return id;
        List<String> properties = new ArrayList<>();
        state.getEntries().forEach((property, value) -> properties.add(property.getName() + "=" + propertyValue(property, value)));
        java.util.Collections.sort(properties);
        return id + "[" + String.join(",", properties) + "]";
    }
    private static ItemStack sourceItem(int index, SourcePalette palette) {
        BlockState source = SourceFallbackCarrierV1271.sourceStates().get(index);
        if (!sourceIdentity(source).equals(palette.states().get(index)))
            throw new IllegalStateException("Source carrier palette identity mismatch at " + index);
        Identifier id = new Identifier(palette.targets().get(index));
        Block block = Registries.BLOCK.get(id);
        if (!(block instanceof SourceFallbackCarrierV1271) || !(block.asItem() instanceof BlockItem)) return ItemStack.EMPTY;
        BlockState state = block.getDefaultState().with(SourceFallbackCarrierV1271.SOURCE, index % 128)
            .with(SourceFallbackCarrierV1271.SOURCE_BANK, index / 128);
        ItemStack stack = block.getPickStack(null, BlockPos.ORIGIN, state);
        if (!state.equals(SourceFallbackCarrierV1271.itemState(stack))) throw new IllegalStateException("Invalid source carrier item " + index);
        return canonical(stack);
    }
    private static volatile List<ItemStack> templates;

    private static List<ItemStack> variants(ItemStack template) {
        if (!(template.getItem() instanceof BlockItem item) || !(item.getBlock() instanceof CompositeRootBlock root)
                || root instanceof ThinWindowRootBlock || root.spec.id.getPath().equals("prototype_tree")) return List.of(template);
        if (root instanceof ImportedStairBlock) {
            if (!root.getDefaultState().contains(ImportedStairBlock.TRIM)) return List.of(template);
            ItemStack plain = template.copy(), trimmed = template.copy();
            plain.getOrCreateSubNbt("BlockStateTag").putString("trim", "false");
            trimmed.getOrCreateSubNbt("BlockStateTag").putString("trim", "true");
            return List.of(plain, trimmed);
        }
        List<Integer> active = ImportedAssemblyV126.activePoses(root.spec.id.toString());
        List<ItemStack> result = new ArrayList<>();
        for (int pose = 0; pose < root.spec.variants.size(); pose++) {
            if (!active.isEmpty() && !active.contains(pose)) continue;
            if (pose >= 16 && !(root instanceof ImportedArtworkBlock)) continue;
            ItemStack stack = template.copy();
            var art = stack.getOrCreateSubNbt("BlockStateTag");
            art.putString("variant", Integer.toString(pose % 16));
            if (root instanceof ImportedArtworkBlock) art.putString("art_bank", Integer.toString(pose / 16));
            result.add(stack);
        }
        return result.isEmpty() ? List.of(template) : result;
    }

    private static List<ItemStack> expected() {
        List<ItemStack> cached = templates;
        if (cached != null) return cached;
        List<ItemStack> built = buildExpected();
        // Registry initialization can request a catalogue before the brush is registered.
        if (Registries.ITEM.get(new Identifier("bloodborne_dw", "editor_brush")) == CityEditorServer.BRUSH) {
            templates = List.copyOf(built);
            return templates;
        }
        return built;
    }

    private static List<ItemStack> authoredVariants(ItemStack stack) {
        ItemStack marked = stack.copy();
        marked.getOrCreateNbt().putInt(ImportedTypeAliases.VERSION_KEY, ImportedTypeAliases.VERSION);
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack variant : variants(marked)) result.add(FenceCatalogue.compact(canonical(variant)));
        return result;
    }
    private static ItemStack aliasVariant(ImportedTypeAliases.Mapping mapping) {
        Identifier id = new Identifier(mapping.oldRegistryId());
        Item item = Registries.ITEM.get(id);
        if (!(item instanceof BlockItem)) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item);
        stack.getOrCreateSubNbt("BlockStateTag").putString("variant", Integer.toString(mapping.oldVariant()));
        return FenceCatalogue.compact(canonical(ImportedAssemblyV126.canonicalStack(ImportedTypeAliases.canonicalStack(stack))));
    }
    private static List<ItemStack> buildExpected() {
        List<ItemStack> result = new ArrayList<>(); Set<String> keys = new HashSet<>();
        List<Identifier> ids = Registries.ITEM.getIds().stream().filter(id -> id.getNamespace().equals("bloodborne_dw")).sorted().toList();
        for (Identifier id : ids) {
            Item item = Registries.ITEM.get(id);
            if (!(item instanceof BlockItem blockItem) || technical(blockItem.getBlock()) != null) continue;
            ItemStack stack = canonical(new ItemStack(item));
            List<ItemStack> creative = RemainingArchitecture.creativeVariants(stack);
            for (ItemStack variant : creative)
                for (ItemStack appearance : variants(variant)) add(result, keys, FenceCatalogue.compact(canonical(appearance)));
            Identifier mappedId = Registries.ITEM.getId(stack.getItem());
            if (creative.isEmpty() && !ImportedAssemblyV126.hidden(mappedId.getPath())
                    && (ImportedStyleRetirements.retired(mappedId) || conversionOnly(mappedId)))
                for (ItemStack appearance : authoredVariants(stack)) add(result, keys, appearance);
        }
        // Historical aliases carry reviewed pose, local shift and yaw transforms.
        // Offer the resulting canonical stack rather than an unplaceable raw alias.
        for (var mapping : ImportedTypeAliases.mappings())
            if (!mapping.oldRegistryId().equals(mapping.newRegistryId())) add(result, keys, aliasVariant(mapping));
        SourcePalette palette = sourcePalette();
        for (int i = 0; i < palette.states().size(); i++) add(result, keys, sourceItem(i, palette));
        // Some retained source types were hidden by the older style retirement or
        // conversion-only catalogue rules. Their registered CompositeItem still
        // places the source art and preserves ImportedArtShift. Offer those poses
        // when no ordinary canonical item of that type is otherwise available.
        Set<Item> ordinaryTypes = new HashSet<>();
        for (ItemStack stack : result) ordinaryTypes.add(stack.getItem());
        for (var pose : StaticArchitectureCatalog.poses()) {
            ItemStack mapped = canonical(pose.block().art(pose.state(), pose.payload()));
            if (!mapped.isEmpty() && !ordinaryTypes.contains(mapped.getItem())) add(result, keys, mapped);
            if (NativeGridRetainedWholeItems.allowed(pose)) add(result, keys, NativeGridRetainedWholeItems.item(pose));
        }
        for (ItemStack stack : FenceCatalogue.specialItems()) add(result, keys, stack);
        if (Registries.ITEM.get(new Identifier("bloodborne_dw", "editor_brush")) == CityEditorServer.BRUSH)
            add(result, keys, new ItemStack(CityEditorServer.BRUSH));
        return result;
    }
    /** Called from the final catalogue return hook. The passed list is mutated. */
    public static void fill(List<ItemStack> items) {
        items.removeIf(stack -> stack.getItem() instanceof BlockItem item && item.getBlock() instanceof MaterialSlabs.Slab && MaterialSlabs.hasOverlay(item.getBlock().getDefaultState()));
        Set<String> keys = new HashSet<>();
        for (var iterator = items.listIterator(); iterator.hasNext();) {
            ItemStack stack = creativeStack(iterator.next());
            if (stack.isEmpty() || !keys.add(key(stack))) iterator.remove();
            else iterator.set(stack);
        }
        for (ItemStack item : expected()) add(items, keys, item.copy());
    }
    private static boolean conversionOnly(Identifier id) {
        try (var stream = EditorCatalogue.class.getResourceAsStream("/bloodborne_dw/remaining-catalogue.json")) {
            if (stream == null) throw new IllegalStateException("Missing remaining architecture catalogue");
            JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var entry : json.getAsJsonArray("objects")) {
                var row = entry.getAsJsonObject();
                if (row.get("path").getAsString().equals(id.getPath()))
                    return row.has("conversionOnly") && row.get("conversionOnly").getAsBoolean();
            }
            return false;
        } catch (java.io.IOException e) { throw new IllegalStateException("Cannot read remaining architecture catalogue", e); }
    }

    /** Reports actual catalogue coverage, exclusions, and blocks requiring an item adapter. */
    public static JsonObject audit() {
        List<ItemStack> actual = UnifiedCreativeCatalogue.canonicalItems();
        Set<String> present = new HashSet<>();
        for (ItemStack stack : actual) present.add(key(stack));
        JsonArray missing = new JsonArray(), aliases = new JsonArray(), technical = new JsonArray(), adapters = new JsonArray(), hidden = new JsonArray(), exclusions = new JsonArray();
        List<ItemStack> expected = expected();
        for (ItemStack stack : expected) if (!present.contains(key(stack))) missing.add(key(stack));
        if (Registries.ITEM.get(new Identifier("bloodborne_dw", "editor_brush")) != CityEditorServer.BRUSH || !present.contains(key(new ItemStack(CityEditorServer.BRUSH)))) missing.add("bloodborne_dw:editor_brush: not registered or absent");
        for (Identifier id : Registries.BLOCK.getIds().stream().filter(i -> i.getNamespace().equals("bloodborne_dw")).sorted().toList()) {
            Block block = Registries.BLOCK.get(id); String reason = technical(block);
            if (block instanceof StaticGrassMasonryBlock) { technical.add(id + ": static storage for bloodborne_dw:" + StaticGrassMasonryBlock.LEGACY_PATH); continue; }
            if (id.toString().equals("bloodborne_dw:source_hunter_lamp_light")) { technical.add(id + ": invisible migration light without item"); continue; }
            if (id.toString().equals("bloodborne_dw:source_ladder_backing")) { technical.add(id + ": owned source ladder backing; canonical item bloodborne_dw:prototype_ladder"); continue; }
            if (reason != null) { technical.add(id + ": " + reason); continue; }
            Item item = block.asItem();
            if (item == Items.AIR || !(item instanceof BlockItem)) {
                if (NativeGridBridge.carrier(block.getDefaultState())) technical.add(id + ": native physical grid carrier without item");
                else adapters.add(id + ": no registered BlockItem; needs placement adapter if usable");
                continue;
            }
            ItemStack original = new ItemStack(item), mapped = canonical(original);
            if (!ItemStack.areEqual(original, mapped)) aliases.add(id + " -> " + key(mapped));
            if (RemainingArchitecture.creativeVariants(mapped).isEmpty()) {
                String canonicalId = ImportedTypeAliases.canonicalRegistry(id.toString());
                if (!canonicalId.equals(id.toString())) exclusions.add(id + ": alias of " + canonicalId);
                else if (ImportedAssemblyV126.hidden(id.getPath())) exclusions.add(id + ": assembly component redirected to its completed item");
                else if (ImportedStyleRetirements.retired(id)) exclusions.add(id + ": former style retirement; authored variants restored and audited");
                else if (conversionOnly(id)) exclusions.add(id + ": former conversion-only artwork; authored variants restored and audited");
                else hidden.add(id + ": no canonical creative variant");
            }
        }
        JsonObject out = new JsonObject();
        out.addProperty("catalogueItems", actual.size()); out.addProperty("expectedVariants", expected.size());
        JsonObject dryPolicy = new JsonObject();
        dryPolicy.addProperty("wetCreativePose", "source_assembly_brick_wall compact=true art_bank=29 variant=6 (470)");
        dryPolicy.addProperty("dryCreativePose", "source_assembly_brick_wall compact=true art_bank=11 variant=1 (177)");
        dryPolicy.addProperty("evidence", "Pinned source_assembly_brick_wall poses and imported-compact-v127 shapes match for all eight rotations; source wall states differ in waterlogged property.");
        dryPolicy.addProperty("scope", "Creative items only; stored city states and wet fluid/optics remain distinct and unchanged.");
        long remainingWet = actual.stream().filter(EditorCatalogue::wetCompactWall).count();
        long dryForms = actual.stream().filter(stack -> {
            if (!Registries.ITEM.getId(stack.getItem()).equals(new Identifier("bloodborne_dw", "source_assembly_brick_wall"))) return false;
            var tag = stack.getSubNbt("BlockStateTag");
            return tag != null && "true".equals(tag.getString("compact")) && "11".equals(tag.getString("art_bank")) && "1".equals(tag.getString("variant"));
        }).count();
        dryPolicy.addProperty("remainingWetItems", remainingWet); dryPolicy.addProperty("dryItems", dryForms);
        out.add("creativeDryWallPolicy", dryPolicy);
        JsonObject statuePolicy = new JsonObject(); JsonArray statueMappings = new JsonArray(), statueFailures = new JsonArray();
        statuePolicy.addProperty("scope", "Creative installation anchors only; saved city shifts, including fractional offsets, remain unchanged. Fractional anchors are not exact world-pose equivalents.");
        Item statueItem = Registries.ITEM.get(new Identifier("bloodborne_dw", "source_hold_statue"));
        for (int variant = 0; variant < 4; variant++) {
            ItemStack raw = new ItemStack(statueItem);
            var art = raw.getOrCreateSubNbt("BlockStateTag");
            art.putString("variant", Integer.toString(variant)); art.putString("profile", "base"); art.putString("open", "false");
            var root = raw.getOrCreateNbt(); root.putInt("ImportedTypeVersion", 1);
            var shift = new net.minecraft.nbt.NbtList(); shift.add(net.minecraft.nbt.NbtDouble.of(0));
            shift.add(net.minecraft.nbt.NbtDouble.of(variant == 0 ? -1.0 : -0.982370625)); shift.add(net.minecraft.nbt.NbtDouble.of(0));
            root.put("ImportedArtShift", shift);
            ItemStack original = raw.copy(), normalized = creativeStack(raw);
            if (!ItemStack.areEqual(raw, original) || !ItemStack.areEqual(normalized, creativeStack(normalized))
                    || normalized.getNbt() == null || normalized.getNbt().contains("ImportedArtShift"))
                statueFailures.add("normalization mutation/idempotence failed for variant " + variant);
            ItemStack tagged = original.copy(); tagged.getOrCreateNbt().putString("dwAuditSentinel", "preserved");
            ItemStack expectedTagged = tagged.copy(); expectedTagged.getNbt().remove("ImportedArtShift");
            if (!ItemStack.areEqual(creativeStack(tagged), expectedTagged)) statueFailures.add("unrelated NBT changed for variant " + variant);
            ItemStack unknown = original.copy();
            var unknownShift = new net.minecraft.nbt.NbtList(); unknownShift.add(net.minecraft.nbt.NbtDouble.of(0));
            unknownShift.add(net.minecraft.nbt.NbtDouble.of(-0.5)); unknownShift.add(net.minecraft.nbt.NbtDouble.of(0));
            unknown.getNbt().put("ImportedArtShift", unknownShift);
            if (!ItemStack.areEqual(unknown, creativeStack(unknown))) statueFailures.add("unknown shift normalized for variant " + variant);
            if (!present.contains(key(normalized))) statueFailures.add("ordinary installation item absent for variant " + variant);
            JsonObject mapping = new JsonObject(); mapping.addProperty("variant", variant);
            mapping.addProperty("rawShiftY", variant == 0 ? -1.0 : -0.982370625);
            mapping.addProperty("ordinaryKey", key(normalized)); statueMappings.add(mapping);
        }
        long remainingShiftedStatues = actual.stream().filter(EditorCatalogue::shiftedStatueInstallation).count();
        if (remainingShiftedStatues != 0) statueFailures.add("legacy translated creative items remain: " + remainingShiftedStatues);
        statuePolicy.add("mappings", statueMappings); statuePolicy.add("failures", statueFailures);
        statuePolicy.addProperty("remainingLegacyItems", remainingShiftedStatues);
        out.add("creativeInstallationAnchorPolicy", statuePolicy);
        JsonArray unsupportedSources = new JsonArray();
        SourcePalette palette = sourcePalette();
        for (int i = 0; i < palette.states().size(); i++)
            if (sourceItem(i, palette).isEmpty()) unsupportedSources.add(palette.states().get(i) + ": carrier item unavailable");
        out.addProperty("sourceStates", SourceFallbackCarrierV1271.sourceStates().size());
        out.addProperty("retainedWholeWhitelist", NativeGridRetainedWholeItems.count());
        JsonArray missingAliasVariants = new JsonArray(), missingAuthoredVariants = new JsonArray();
        int aliasMappings = 0, authoredTypes = 0;
        for (var mapping : ImportedTypeAliases.mappings()) {
            if (mapping.oldRegistryId().equals(mapping.newRegistryId())) continue;
            aliasMappings++;
            ItemStack mapped = aliasVariant(mapping);
            if (mapped.isEmpty() || !present.contains(key(mapped)))
                missingAliasVariants.add(mapping.oldRegistryId() + "/" + mapping.oldVariant() + " -> " + key(mapped));
        }
        for (Identifier id : Registries.ITEM.getIds().stream().filter(i -> i.getNamespace().equals("bloodborne_dw")).sorted().toList()) {
            Item item = Registries.ITEM.get(id);
            if (!(item instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof CompositeRootBlock)) continue;
            ItemStack mapped = canonical(new ItemStack(item));
            Identifier canonicalId = Registries.ITEM.getId(mapped.getItem());
            if (!id.equals(canonicalId) || ImportedAssemblyV126.hidden(id.getPath())
                    || !(ImportedStyleRetirements.retired(id) || conversionOnly(id))) continue;
            authoredTypes++;
            for (ItemStack variant : authoredVariants(mapped))
                if (variant.isEmpty() || !present.contains(key(variant))) missingAuthoredVariants.add(id + " -> " + key(variant));
        }
        out.addProperty("reviewedAliasMappings", aliasMappings);
        out.addProperty("retiredOrConversionAuthoredTypes", authoredTypes);
        out.add("missingAliasVariants", missingAliasVariants);
        out.add("missingRetiredOrConversionVariants", missingAuthoredVariants);
        JsonArray missingStaticSourceItems = new JsonArray();
        Set<Item> actualItems = new HashSet<>();
        for (ItemStack stack : actual) actualItems.add(stack.getItem());
        JsonArray overlayMappings = new JsonArray(), missingOverlayComponents = new JsonArray();
        for (var slab : MaterialSlabs.blocks()) {
            BlockState state = slab.getDefaultState();
            if (!MaterialSlabs.hasOverlay(state)) continue;
            BlockState base = MaterialSlabs.withRecipe(state, -1);
            int recipe = MaterialSlabs.recipeIndex(state);
            String id = Registries.BLOCK.getId(slab).toString();
            ItemStack baseItem = canonical(new ItemStack(base.getBlock().asItem()));
            if (baseItem.isEmpty() || !actualItems.contains(baseItem.getItem())) missingOverlayComponents.add(id + ": base item " + key(baseItem));
            JsonObject mapping = new JsonObject(); mapping.addProperty("storage", id);
            mapping.addProperty("base", Registries.BLOCK.getId(base.getBlock()).toString());
            mapping.addProperty("recipe", recipe);
            JsonArray fragments = new JsonArray();
            for (var fragment : StaticArchitectureCatalog.recipes().get(recipe).fragments()) {
                var pose = fragment.pose();
                ItemStack art = canonical(pose.block().art(pose.state(), pose.payload()));
                fragments.add(pose.key() + " -> " + key(art));
                if (art.isEmpty() || !actualItems.contains(art.getItem())) missingOverlayComponents.add(id + ": fragment " + pose.key() + " -> " + key(art));
            }
            mapping.add("fragments", fragments); overlayMappings.add(mapping);
        }
        out.add("slabOverlayStorageMappings", overlayMappings);
        out.add("missingSlabOverlayComponentItemTypes", missingOverlayComponents);
        Set<String> missingStaticSources = new java.util.TreeSet<>();
        for (var pose : StaticArchitectureCatalog.poses()) {
            ItemStack mapped = canonical(pose.block().art(pose.state(), pose.payload()));
            if (mapped.isEmpty() || !actualItems.contains(mapped.getItem())) missingStaticSources.add(pose.key() + ": source=" + sourceIdentity(pose.state()) + "; item=" + key(mapped) + "; payload=" + pose.payload());
        }
        for (String path : List.of(StaticGrassMasonryBlock.LEGACY_PATH, "prototype_ladder")) {
            Block source = Registries.BLOCK.get(new Identifier("bloodborne_dw", path));
            ItemStack mapped = canonical(new ItemStack(source.asItem()));
            if (mapped.isEmpty() || !actualItems.contains(mapped.getItem())) missingStaticSources.add("canonical source item missing: " + path);
        }
        missingStaticSources.forEach(missingStaticSourceItems::add);
        out.addProperty("staticPoses", StaticArchitectureCatalog.poses().size());
        out.add("staticPosesWithoutCanonicalItemType", missingStaticSourceItems);
        out.add("unsupportedSourceStates", unsupportedSources);
        out.add("missingCanonicalVariants", missing); out.add("aliases", aliases); out.add("technical", technical);
        out.add("noItemAdapters", adapters); out.add("hiddenWithoutVariant", hidden); out.add("intentionalExclusions", exclusions);
        out.addProperty("pass", missing.size() == 0 && adapters.size() == 0 && hidden.size() == 0 && unsupportedSources.size() == 0 && missingStaticSourceItems.size() == 0 && missingAliasVariants.size() == 0 && missingAuthoredVariants.size() == 0 && missingOverlayComponents.size() == 0 && remainingWet == 0 && dryForms > 0 && statueFailures.size() == 0);
        return out;
    }
}






