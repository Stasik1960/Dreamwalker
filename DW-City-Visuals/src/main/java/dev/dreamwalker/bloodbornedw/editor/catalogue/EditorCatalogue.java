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
import dev.dreamwalker.bloodbornedw.repair.CityRepair;
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
    public static void fill(List<ItemStack> items) {
        java.util.Map<String, String> original = new java.util.LinkedHashMap<>();
        Set<String> ids = new HashSet<>();
        for (var it = items.listIterator(); it.hasNext();) {
            ItemStack stack = creativeStack(it.next());
            String id = Registries.ITEM.getId(stack.getItem()).toString();
            if (stack.isEmpty() || technicalStack(stack) || handledByPinnedRepair(stack, id) || !ids.add(id)) it.remove();
            else { it.set(stack); original.put(id, key(stack)); }
        }
        String brush = Registries.ITEM.getId(CityEditorServer.BRUSH).toString();
        if (ids.add(brush)) items.add(new ItemStack(CityEditorServer.BRUSH));
        original.remove(brush);
        lastOriginal = java.util.Map.copyOf(original);
    }
    public static JsonObject audit() {
        List<ItemStack> actual = UnifiedCreativeCatalogue.canonicalItems();
        Set<String> ids = new HashSet<>(); JsonArray duplicates = new JsonArray(), technical = new JsonArray();
        JsonArray originals = new JsonArray(), changed = new JsonArray(); int brushes = 0;
        for (ItemStack stack : actual) {
            String id = Registries.ITEM.getId(stack.getItem()).toString();
            if (!ids.add(id)) duplicates.add(id);
            if (technicalStack(stack)) technical.add(id);
            if (stack.isOf(CityEditorServer.BRUSH)) brushes++;
            String expected = lastOriginal.get(id);
            if (expected != null && !expected.equals(key(stack))) changed.add(id);
        }
        lastOriginal.keySet().stream().sorted().forEach(originals::add);
        for (String id : lastOriginal.keySet()) if (!ids.contains(id)) changed.add(id);
        JsonObject out = new JsonObject();
        out.addProperty("catalogueItems", actual.size()); out.add("originalItemIds", originals);
        out.add("duplicateItemIds", duplicates); out.add("technical", technical);
        out.add("changedInstallationItems", changed); out.addProperty("brushCount", brushes);
        out.addProperty("pass", duplicates.size() == 0 && technical.size() == 0 && changed.size() == 0 && brushes == 1);
        return out;
    }
}

