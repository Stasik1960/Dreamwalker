package dev.dreamwalker.bloodbornedw.composite;

import java.util.Map;
import net.minecraft.block.BlockState;
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
    /** Some canonical source IDs also hold unrelated artwork. */
    private static BlockState selectedArt(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem item) || !(item.getBlock() instanceof ImportedCompactBlock block)) return null;
        var state = block.getDefaultState();
        var art = stack.getSubNbt("BlockStateTag");
        if (art == null) return state;
        try {
            if (art.contains("variant", 8)) state = state.with(CompositeRootBlock.VARIANT, Integer.parseInt(art.getString("variant")));
            var bank = ImportedArtworkBlock.bankProperty(state);
            if (bank != null && art.contains("art_bank", 8)) state = state.with(bank, Integer.parseInt(art.getString("art_bank")));
            return state;
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    public static boolean automaticWallItem(ItemStack stack) {
        var art = stack.getSubNbt("BlockStateTag");
        if (art != null && "false".equals(art.getString("compact"))) return false;
        var state = selectedArt(stack);
        return state != null && AutoStoneFence.wallLike(state);
    }

    public static ItemStack compact(ItemStack stack) {
        var state = selectedArt(stack);
        if (state == null || !AutoStoneFence.matches(state)) return stack;
        var art = stack.getSubNbt("BlockStateTag");
        if (art != null && "true".equals(art.getString("compact"))) return stack;
        ItemStack copy = stack.copy();
        copy.getOrCreateSubNbt("BlockStateTag").putString("compact", "true");
        return copy;
    }
    public static java.util.List<ItemStack> specialItems() {
        java.util.List<ItemStack> result = new java.util.ArrayList<>();
        int index = 0;
        String[] names = {"Ограда из булыжного глубинного сланца", "Ограда из эндернякового кирпича",
            "Гранитная ограда", "Белая стеклянная панель", "Красная стеклянная панель"};
        for (Target target : java.util.List.of(new Target("bloodborne_dw:source_andesite_slab_top", 4),
                new Target("bloodborne_dw:source_cut_copper", 1),
                new Target("bloodborne_dw:source_assembly_deepslate_brick_wall", 12),
                new Target("bloodborne_dw:source_assembly_orange_stained_glass_pane", 14),
                new Target("bloodborne_dw:source_assembly_orange_stained_glass_pane", 7))) {
            Identifier id = new Identifier(target.id());
            String name = names[index++];
            if (!Registries.ITEM.containsId(id) || !(Registries.ITEM.get(id) instanceof BlockItem)) continue;
            ItemStack stack = new ItemStack(Registries.ITEM.get(id));
            stack.setCustomName(net.minecraft.text.Text.literal(name));
            stack.getOrCreateSubNbt("BlockStateTag").putString("variant", Integer.toString(target.variant()));
            stack.getOrCreateNbt().putInt(ImportedTypeAliases.VERSION_KEY, ImportedTypeAliases.VERSION);
            result.add(compact(stack));
        }
        return java.util.List.copyOf(result);
    }
    /** Internal wall connection poses are not extra catalogue items. */
    public static boolean generatedWallPose(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem item)) return false;
        var id = Registries.BLOCK.getId(item.getBlock());
        if (!id.getNamespace().equals("bloodborne_dw")) return false;
        var art = stack.getSubNbt("BlockStateTag");
        if (art == null) return false;
        try {
            int pose = art.contains("variant", 8) ? Integer.parseInt(art.getString("variant")) : 0;
            int bank = art.contains("art_bank", 8) ? Integer.parseInt(art.getString("art_bank")) : 0;
            return generatedWallPose(id.getPath(), pose + 16 * bank);
        } catch (NumberFormatException ignored) { return false; }
    }
    public static boolean generatedWallPose(String path, int pose) {
        int first = switch (path) {
            case "source_assembly_brick_wall" -> 471;
            case "source_assembly_cobblestone_wall" -> 71;
            case "source_assembly_deepslate_brick_wall" -> 146;
            case "source_assembly_mossy_cobblestone_wall" -> 94;
            case "source_assembly_prismarine_wall" -> 92;
            case "source_assembly_stone_brick_wall" -> 127;
            case "source_assembly_cobbled_deepslate_wall", "source_assembly_granite_wall", "source_assembly_end_stone_brick_wall" -> 12;
            case "source_andesite_slab_top" -> 92;
            case "source_cut_copper" -> 83;
            default -> Integer.MAX_VALUE;
        };
        return pose >= first;
    }
    private FenceCatalogue() {}
}
