package dev.dreamwalker.bloodbornedw.composite;

import java.util.Map;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/** Old isolated post and side items become the existing unified fence item. */
public final class FenceCatalogue {
    private record Target(String id, int variant) {}
    private static final Map<String, Target> PARTS = Map.ofEntries(
        Map.entry("bloodborne_dw:prototype_wall_skin_1", new Target("bloodborne_dw:prototype_wall", 0)),
        part("stone_brick_wall_post", "stone_brick_wall", 0), part("stone_brick_wall_side", "stone_brick_wall", 0), part("stone_brick_wall_side_tall", "stone_brick_wall", 0),
        part("nether_brick_fence_post", "nether_brick_fence", 0), part("nether_brick_fence_side", "nether_brick_fence", 0),
        part("brick_wall_post", "brick_wall", 0), part("brick_wall_side", "brick_wall", 0), part("brick_wall_side_tall", "brick_wall", 0),
        part("cobblestone_wall_post", "cobblestone_wall", 0), part("cobblestone_wall_side", "cobblestone_wall", 0), part("cobblestone_wall_side_tall", "cobblestone_wall", 0),
        part("mossy_cobblestone_wall_post", "mossy_cobblestone_wall", 0), part("mossy_cobblestone_wall_side", "mossy_cobblestone_wall", 0), part("mossy_cobblestone_wall_side_tall", "mossy_cobblestone_wall", 0),
        part("prismarine_wall_post", "prismarine_wall", 0), part("prismarine_wall_side", "prismarine_wall", 0), part("prismarine_wall_side_tall", "prismarine_wall", 0),
        part("deepslate_brick_wall_post", "deepslate_brick_wall", 0), part("deepslate_brick_wall_side", "deepslate_brick_wall", 0), part("deepslate_brick_wall_side_tall", "deepslate_brick_wall", 0),
        part("granite_wall_post", "deepslate_brick_wall", 12), part("granite_wall_side", "deepslate_brick_wall", 12), part("granite_wall_side_tall", "deepslate_brick_wall", 12),
        part("brown_stained_glass_pane_post", "brown_stained_glass_pane", 0),
        part("brown_stained_glass_pane_side", "brown_stained_glass_pane", 0),
        part("brown_stained_glass_pane_side_alt", "brown_stained_glass_pane", 0),
        part("brown_stained_glass_pane_noside", "brown_stained_glass_pane", 0),
        part("brown_stained_glass_pane_noside_alt", "brown_stained_glass_pane", 0),
        part("black_stained_glass_pane_post", "black_stained_glass_pane", 0),
        part("black_stained_glass_pane_side", "black_stained_glass_pane", 0),
        part("black_stained_glass_pane_side_alt", "black_stained_glass_pane", 0),
        part("black_stained_glass_pane_noside", "black_stained_glass_pane", 0),
        part("black_stained_glass_pane_noside_alt", "black_stained_glass_pane", 0),
        part("green_stained_glass_pane_post", "green_stained_glass_pane", 0),
        part("green_stained_glass_pane_side", "green_stained_glass_pane", 0),
        part("green_stained_glass_pane_side_alt", "green_stained_glass_pane", 0),
        part("green_stained_glass_pane_noside", "green_stained_glass_pane", 0),
        part("green_stained_glass_pane_noside_alt", "green_stained_glass_pane", 0),
        part("orange_stained_glass_pane_post", "orange_stained_glass_pane", 0),
        part("orange_stained_glass_pane_side", "orange_stained_glass_pane", 0),
        part("orange_stained_glass_pane_side_alt", "orange_stained_glass_pane", 0),
        part("orange_stained_glass_pane_noside", "orange_stained_glass_pane", 0),
        part("orange_stained_glass_pane_noside_alt", "orange_stained_glass_pane", 0),
        part("white_stained_glass_pane_post", "orange_stained_glass_pane", 14),
        part("white_stained_glass_pane_side", "orange_stained_glass_pane", 14),
        part("white_stained_glass_pane_side_alt", "orange_stained_glass_pane", 14),
        part("white_stained_glass_pane_noside", "orange_stained_glass_pane", 14),
        part("white_stained_glass_pane_noside_alt", "orange_stained_glass_pane", 14),
        part("red_stained_glass_pane_post", "orange_stained_glass_pane", 7),
        part("red_stained_glass_pane_side", "orange_stained_glass_pane", 7),
        part("red_stained_glass_pane_side_alt", "orange_stained_glass_pane", 7),
        part("red_stained_glass_pane_noside", "orange_stained_glass_pane", 7),
        part("red_stained_glass_pane_noside_alt", "orange_stained_glass_pane", 7),
        part("spruce_fence", "spruce_fence", 0),
        part("iron_bars_post", "iron_bars", 0), part("iron_bars_post_ends", "iron_bars", 0), part("iron_bars_cap", "iron_bars", 0), part("iron_bars_cap_alt", "iron_bars", 0), part("iron_bars_side", "iron_bars", 0), part("iron_bars_side_alt", "iron_bars", 0), part("iron_bars_side_alt_1", "iron_bars", 7), part("iron_bars_side_alt_2", "iron_bars", 11), part("iron_bars_side_alt_3", "iron_bars", 15),
        direct("cobbled_deepslate_wall_post", "source_andesite_slab_top", 4),
        direct("cobbled_deepslate_wall_side", "source_andesite_slab_top", 4),
        direct("cobbled_deepslate_wall_side_tall", "source_andesite_slab_top", 4),
        direct("end_stone_brick_wall_post", "source_cut_copper", 1),
        direct("end_stone_brick_wall_side", "source_cut_copper", 1),
        direct("end_stone_brick_wall_side_tall", "source_cut_copper", 1)
    );
    private static Map.Entry<String, Target> part(String source, String target, int pose) {
        return Map.entry("bloodborne_dw:source_" + source, new Target("bloodborne_dw:source_assembly_" + target, pose));
    }
    private static Map.Entry<String, Target> direct(String source, String target, int pose) {
        return Map.entry("bloodborne_dw:source_" + source, new Target("bloodborne_dw:" + target, pose));
    }
    private static Target target(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem item)) return null;
        if (item.getBlock() instanceof SourceFallbackCarrierV1271) {
            var state = SourceFallbackCarrierV1271.itemState(stack);
            if (state != null) {
                int source = state.get(SourceFallbackCarrierV1271.SOURCE) + 128 * state.get(SourceFallbackCarrierV1271.SOURCE_BANK);
                if (java.util.Set.of(88, 108, 128, 129, 130, 144, 149).contains(source))
                    return new Target("bloodborne_dw:source_assembly_spruce_fence", 0);
            }
        }
        return PARTS.get(Registries.BLOCK.getId(item.getBlock()).toString());
    }
    public static boolean hidden(ItemStack stack) { return target(stack) != null; }
    public static ItemStack canonical(ItemStack old) {
        Target target = target(old); if (target == null) return old;
        Identifier id = new Identifier(target.id());
        if (!Registries.ITEM.containsId(id) || !(Registries.ITEM.get(id) instanceof BlockItem)) return old;
        ItemStack next = new ItemStack(Registries.ITEM.get(id), old.getCount());
        if (old.hasNbt()) next.setNbt(old.getNbt().copy());
        var art = next.getOrCreateSubNbt("BlockStateTag");
        if (target.id().equals("bloodborne_dw:prototype_wall")) {
            for (String key : java.util.List.of("material", "variant", "compact", "open", "rotation", "connections", "north", "east", "south", "west", "up")) art.remove(key);
            return next;
        }
        art.putString("variant", Integer.toString(target.variant() % 16));
        if (target.variant() >= 16) art.putString("art_bank", Integer.toString(target.variant() / 16));
        else art.remove("art_bank");
        art.remove("rotation"); art.remove("source"); art.remove("source_bank"); art.putString("open", "false"); art.putString("compact", "true");
        next.getOrCreateNbt().remove(ImportedTypeAliases.SHIFT_ART_KEY);
        next.getOrCreateNbt().putInt(ImportedTypeAliases.VERSION_KEY, ImportedTypeAliases.VERSION);
        return next;
    }
    /** Inspect the selected art state: some canonical source IDs also hold non-fence art. */
    public static ItemStack compact(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem item) || !(item.getBlock() instanceof ImportedCompactBlock block)) return stack;
        var state = block.getDefaultState(); var art = stack.getSubNbt("BlockStateTag");
        try {
            if (art != null && art.contains("variant", 8)) state = state.with(CompositeRootBlock.VARIANT, Integer.parseInt(art.getString("variant")));
            var bank = ImportedArtworkBlock.bankProperty(state);
            if (bank != null && art != null && art.contains("art_bank", 8)) state = state.with(bank, Integer.parseInt(art.getString("art_bank")));
        } catch (IllegalArgumentException invalid) { return stack; }
        if (!AutoStoneFence.matches(state)) return stack;
        if (art != null && "true".equals(art.getString("compact"))) return stack;
        ItemStack copy = stack.copy(); copy.getOrCreateSubNbt("BlockStateTag").putString("compact", "true");
        return copy;
    }
    public static java.util.List<ItemStack> specialItems() {
        java.util.List<ItemStack> result = new java.util.ArrayList<>();
        for (Target target : java.util.List.of(new Target("bloodborne_dw:source_andesite_slab_top", 4),
                new Target("bloodborne_dw:source_cut_copper", 1),
                new Target("bloodborne_dw:source_assembly_deepslate_brick_wall", 12),
                new Target("bloodborne_dw:source_assembly_orange_stained_glass_pane", 14),
                new Target("bloodborne_dw:source_assembly_orange_stained_glass_pane", 7))) {
            Identifier id = new Identifier(target.id());
            if (!Registries.ITEM.containsId(id) || !(Registries.ITEM.get(id) instanceof BlockItem)) continue;
            ItemStack stack = new ItemStack(Registries.ITEM.get(id));
            stack.getOrCreateSubNbt("BlockStateTag").putString("variant", Integer.toString(target.variant()));
            stack.getOrCreateNbt().putInt(ImportedTypeAliases.VERSION_KEY, ImportedTypeAliases.VERSION);
            result.add(compact(stack));
        }
        return java.util.List.copyOf(result);
    }
    private FenceCatalogue() {}
}
