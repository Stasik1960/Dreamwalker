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
import dev.dreamwalker.bloodbornedw.debug.DebugCatalogue;
import dev.dreamwalker.bloodbornedw.editor.CityEditorServer;
import dev.dreamwalker.bloodbornedw.repair.CityRepair;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.WallBlock;
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
    private static volatile java.util.Map<String, String> lastOriginal = java.util.Map.of();
    private static boolean technicalStack(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem item)) return false;
        String path = Registries.ITEM.getId(stack.getItem()).getPath();
        return technical(item.getBlock()) != null || path.startsWith("restored_slab_overlay_") || path.equals("source_hunter_lamp_light") || path.equals("source_ladder_backing") || item.getBlock() instanceof StaticGrassMasonryBlock;
    }
    private static final Set<String> RETIRED_LANTERNS = retiredLanterns();
    private static Set<String> retiredLanterns() {
        try (var input = EditorCatalogue.class.getResourceAsStream("/bloodborne_dw/repair/retired-lanterns.json")) {
            Set<String> ids = new HashSet<>();
            for (var entry : JsonParser.parseReader(new InputStreamReader(java.util.Objects.requireNonNull(input), StandardCharsets.UTF_8)).getAsJsonArray()) ids.add(entry.getAsString());
            return Set.copyOf(ids);
        } catch (java.io.IOException error) { throw new IllegalStateException("Original lantern catalogue data", error); }
    }
    private static boolean handledByPinnedRepair(ItemStack stack, String id) {
        // The unchanged base-mod RETURN hook removes retired lamps and appends these two items.
        if (RETIRED_LANTERNS.contains(id) || CityRepair.LANTERN != null && stack.isOf(CityRepair.LANTERN.asItem())
                || CityRepair.DARKNESS != null && stack.isOf(CityRepair.DARKNESS.asItem())) return true;
        var state = stack.getSubNbt("BlockStateTag");
        if (!id.equals("bloodborne_dw:source_street_lamp_post") || state == null) return false;
        String variant = state.getString("variant");
        return !variant.isEmpty() && !variant.equals("0");
    }
    // Explicitly reviewed art only: distinct closed-model geometry, excluding
    // rotations, shifted city poses, connection parts and technical carriers.
    private record AuthoredFamily(String path, String label, List<Integer> poses) {}
    private static final List<AuthoredFamily> AUTHORED_FAMILIES = List.of(
        new AuthoredFamily("source_addon_bag_0", "Широкий мешок", List.of(2)),
        new AuthoredFamily("source_addon_barrel_0", "Большая бочка", List.of(6)),
        new AuthoredFamily("source_addon_new_statue_1", "Статуя", List.of(1, 2)),
        new AuthoredFamily("source_hold_statue", "Статуя, серия 2", List.of(1, 2, 4)));

    private static int artIndex(ItemStack stack) {
        var state = stack.getSubNbt("BlockStateTag");
        if (state == null) return 0;
        try {
            int variant = state.contains("variant", 8) ? Integer.parseInt(state.getString("variant")) : 0;
            int bank = state.contains("art_bank", 8) ? Integer.parseInt(state.getString("art_bank")) : 0;
            return bank * 16 + variant;
        } catch (NumberFormatException invalid) { return -1; }
    }
    private static void addAuthoredFamilies(List<ItemStack> items, Set<String> keys) {
        for (AuthoredFamily family : AUTHORED_FAMILIES) {
            Identifier id = new Identifier("bloodborne_dw", family.path());
            if (!Registries.ITEM.containsId(id)) continue;
            Item item = Registries.ITEM.get(id);
            if (!(item instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof CompositeRootBlock root)
                    || !root.getDefaultState().contains(CompositeRootBlock.VARIANT)) continue;
            ItemStack template = items.stream().filter(stack -> stack.isOf(item)).findFirst().orElse(null);
            if (template == null) continue;
            for (int pose : family.poses()) {
                if (pose >= root.spec.variants.size()) continue;
                final int selectedPose = pose;
                if (items.stream().anyMatch(stack -> stack.isOf(item) && artIndex(stack) == selectedPose)) continue;
                ItemStack stack = template.copy();
                var state = stack.getOrCreateSubNbt("BlockStateTag");
                state.putString("variant", Integer.toString(pose % 16));
                if (pose >= 16) state.putString("art_bank", Integer.toString(pose / 16));
                else state.remove("art_bank");
                stack.getOrCreateNbt().putInt(ImportedTypeAliases.VERSION_KEY, ImportedTypeAliases.VERSION);
                stack.setCustomName(net.minecraft.text.Text.literal(family.label() + " — форма " + (pose + 1)));
                if (keys.add(key(stack))) items.add(stack);
            }
        }
    }
    public static void fill(List<ItemStack> items) {
        java.util.Map<String, String> original = new java.util.LinkedHashMap<>();
        Set<String> ids = new HashSet<>();
        Set<String> variants = new HashSet<>();
        for (var it = items.listIterator(); it.hasNext();) {
            ItemStack stack = creativeStack(it.next());
            String id = Registries.ITEM.getId(stack.getItem()).toString();
            if (stack.isEmpty() || technicalStack(stack) || handledByPinnedRepair(stack, id) || !variants.add(key(stack))) it.remove();
            else { ids.add(id); it.set(stack); original.putIfAbsent(id, key(stack)); }
        }
        // The debug catalogue identifies independent authored types. Their old
        // registered items can map to a shared current block and art state.
        for (var mapping : ImportedTypeAliases.mappings()) {
            if (mapping.oldVariant() != 0 || mapping.oldRegistryId().equals(mapping.newRegistryId())) continue;
            Identifier sourceId = new Identifier(mapping.oldRegistryId());
            var entry = DebugCatalogue.entry(sourceId);
            String sourcePath = sourceId.getPath();
            // Open gate/door/trapdoor models describe interaction states, not
            // independently placeable designs.
            if (sourcePath.contains("fence_gate_open") || sourcePath.contains("trapdoor_open")
                    || sourcePath.contains("door_open")) continue;
            if (entry == null || !entry.registryId().equals(sourceId) || !entry.kind().equals("architecture")
                    || ImportedAssemblyV126.hidden(sourceId.getPath()) || !Registries.ITEM.containsId(sourceId)) continue;
            Item source = Registries.ITEM.get(sourceId);
            if (!(source instanceof BlockItem) || FenceCatalogue.hidden(new ItemStack(source))) continue;
            ItemStack old = new ItemStack(source);
            old.getOrCreateSubNbt("BlockStateTag").putString("variant", "0");
            ItemStack stack = FenceCatalogue.compact(FenceCatalogue.canonical(CatalogueMigration.canonicalStack(
                ImportedAssemblyV126.canonicalStack(ImportedTypeAliases.canonicalStack(old)))));
            if (!stack.isEmpty() && !technicalStack(stack) && !handledByPinnedRepair(stack, Registries.ITEM.getId(stack.getItem()).toString())
                    && variants.add(key(stack))) items.add(stack);
        }
        addAuthoredFamilies(items, variants);
        for (ItemStack stack : FenceCatalogue.specialItems()) {
            if (variants.add(key(stack))) items.add(stack);
        }
        String brush = Registries.ITEM.getId(CityEditorServer.BRUSH).toString();
        if (ids.add(brush)) items.add(new ItemStack(CityEditorServer.BRUSH));
        original.remove(brush);
        lastOriginal = java.util.Map.copyOf(original);
    }
    /** Each item belongs to exactly one tab; all tabs draw from the same canonical list. */
    public enum Category { BUILDING, OPENINGS, STAIRS, BOUNDARIES, DECOR, ITEMS, TOOLS }
    private static final List<String> DECOR_NAMES = List.of(
        "statue", "tombstone", "gargoyle", "lantern", "spirelamp", "street_lamp", "hunter_lamp",
        "candel", "candle", "books", "barrel", "bag_", "cases_", "chain", "bloodviles",
        "prototype_tree", "source_addon_grass", "source_addon_leaves");

    public static Category category(ItemStack stack) {
        Identifier id = Registries.ITEM.getId(stack.getItem());
        String path = id.getPath();
        if (stack.isOf(CityEditorServer.BRUSH) || path.equals("nrp_pickaxe")
                || path.equals("bb_tool") || path.equals("bb_block_tool") || path.equals("bb_npc_tool")) return Category.TOOLS;
        if (id.getNamespace().equals("bloodborne_rp")) {
            if (!path.endsWith("_placer")) return Category.ITEMS;
            if (path.contains("door") || path.contains("gate") || path.contains("window") || path.contains("curtain"))
                return Category.OPENINGS;
            if (path.contains("stairs") || path.contains("ladder")) return Category.STAIRS;
            return Category.DECOR;
        }
        if (!(stack.getItem() instanceof BlockItem item)) return Category.ITEMS;
        Block block = item.getBlock();
        if (AUTHORED_FAMILIES.stream().anyMatch(family -> family.path().equals(path))
                || DECOR_NAMES.stream().anyMatch(path::contains)) return Category.DECOR;
        if (block instanceof DoorBlock || block instanceof TrapdoorBlock || block instanceof FenceGateBlock
                || block instanceof CompositeRootBlock root && root.spec.openable
                || path.contains("window") || path.contains("glass_pane") || block instanceof PaneBlock)
            return Category.OPENINGS;
        if (block instanceof WallBlock || block instanceof FenceBlock
                || path.startsWith("source_assembly_") && (path.endsWith("_wall") || path.endsWith("_fence")))
            return Category.BOUNDARIES;
        if (block instanceof ImportedStairBlock || block instanceof StairsBlock
                || path.contains("stairs") || path.contains("roof") || path.contains("ladder"))
            return Category.STAIRS;
        return Category.BUILDING;
    }
    public static List<ItemStack> categoryItems(Category category) {
        return UnifiedCreativeCatalogue.canonicalItems().stream().filter(stack -> category(stack) == category).toList();
    }
    private static boolean tabsRegistered;
    public static void registerTabs() {
        if (tabsRegistered) return;
        tabsRegistered = true;
        registerTab("catalogue_openings", "DreamWalker — двери и окна", Items.OAK_DOOR, Category.OPENINGS);
        registerTab("catalogue_stairs", "DreamWalker — лестницы и крыши", Items.STONE_BRICK_STAIRS, Category.STAIRS);
        registerTab("catalogue_boundaries", "DreamWalker — стены и ограды", Items.STONE_BRICK_WALL, Category.BOUNDARIES);
        registerTab("catalogue_decor", "DreamWalker — декор", Items.PAINTING, Category.DECOR);
        registerTab("catalogue_items", "DreamWalker — предметы", Items.BOOK, Category.ITEMS);
        registerTab("catalogue_tools", "DreamWalker — инструменты", Items.IRON_PICKAXE, Category.TOOLS);
    }
    private static void registerTab(String path, String title, Item icon, Category category) {
        net.minecraft.registry.Registry.register(Registries.ITEM_GROUP, new Identifier("bloodborne_dw", path),
            net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup.builder()
                .displayName(net.minecraft.text.Text.literal(title)).icon(() -> new ItemStack(icon))
                .entries((context, entries) -> categoryItems(category).forEach(entries::add)).build());
    }
    public static JsonObject audit() {
        List<ItemStack> actual = UnifiedCreativeCatalogue.canonicalItems();
        Set<String> ids = new HashSet<>(); Set<String> variantKeys = new HashSet<>(); JsonArray duplicates = new JsonArray(), technical = new JsonArray();
        JsonArray originals = new JsonArray(), changed = new JsonArray(); Set<String> foundOriginals = new HashSet<>(); int brushes = 0;
        java.util.EnumMap<Category, Integer> counts = new java.util.EnumMap<>(Category.class);
        for (ItemStack stack : actual) {
            counts.merge(category(stack), 1, Integer::sum);
            String id = Registries.ITEM.getId(stack.getItem()).toString();
            ids.add(id);
            if (!variantKeys.add(key(stack))) duplicates.add(id);
            if (technicalStack(stack)) technical.add(id);
            if (stack.isOf(CityEditorServer.BRUSH)) brushes++;
            String expected = lastOriginal.get(id);
            if (expected != null && expected.equals(key(stack))) foundOriginals.add(id);
        }
        lastOriginal.keySet().stream().sorted().forEach(originals::add);
        for (String id : lastOriginal.keySet()) if (!foundOriginals.contains(id)) changed.add(id);
        JsonObject out = new JsonObject();
        JsonArray authored = new JsonArray();
        for (AuthoredFamily family : AUTHORED_FAMILIES) {
            Identifier id = new Identifier("bloodborne_dw", family.path());
            Item item = Registries.ITEM.get(id);
            JsonObject detail = new JsonObject();
            detail.addProperty("id", id.toString());
            if (item instanceof BlockItem blockItem) {
                Block block = blockItem.getBlock();
                detail.addProperty("blockClass", block.getClass().getName());
                detail.addProperty("variantProperty", block.getDefaultState().contains(CompositeRootBlock.VARIANT));
                if (block instanceof CompositeRootBlock root) detail.addProperty("specVariants", root.spec.variants.size());
            }
            JsonArray missing = new JsonArray();
            for (int pose : family.poses())
                if (actual.stream().noneMatch(stack -> stack.isOf(item) && artIndex(stack) == pose)) missing.add(pose);
            detail.add("missingPoses", missing);
            authored.add(detail);
        }
        out.add("authoredFamilies", authored);
        JsonObject categoryCounts = new JsonObject();
        for (Category category : Category.values()) categoryCounts.addProperty(category.name(), counts.getOrDefault(category, 0));
        out.add("categories", categoryCounts); out.addProperty("uniqueRegistryIds", ids.size());
        out.addProperty("catalogueItems", actual.size()); out.add("originalItemIds", originals);
        out.add("duplicateItemStacks", duplicates); out.add("technical", technical);
        out.add("changedInstallationItems", changed); out.addProperty("brushCount", brushes);
        out.addProperty("pass", duplicates.size() == 0 && technical.size() == 0 && changed.size() == 0 && brushes == 1);
        return out;
    }
}

