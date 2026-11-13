package dev.dreamwalker.bloodbornedw.building;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import dev.dreamwalker.bloodbornedw.composite.CompositeSpec;
import dev.dreamwalker.bloodbornedw.composite.ImportedTypeAliases;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.ModInitializer;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;

public final class ConnectedFences implements ModInitializer {
    private static final Map<String, BlockItem> REPLACED = new HashMap<>();
    private static final Map<String, Map<Integer, BlockItem>> REPLACED_POSES = new HashMap<>();
    private static final Map<String, BlockItem> ITEMS = new HashMap<>();

    @Override public void onInitialize() {
        var input = ConnectedFences.class.getResourceAsStream("/bloodborne_dw/unified-fences.json");
        if (input == null) throw new IllegalStateException("Missing connected fence definitions");
        try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            for (JsonElement raw : JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("fences")) {
                var row = raw.getAsJsonObject();
                String path = row.get("id").getAsString();
                var id = new Identifier("bloodborne_dw", path);
                var settings = AbstractBlock.Settings.create().strength(2.0f, 6.0f).nonOpaque().dynamicBounds();
                boolean column = row.get("post").getAsBoolean();
                var block = column ? new ConnectedFenceColumnBlock(settings.pistonBehavior(PistonBehavior.BLOCK))
                        : new ConnectedFenceBlock(settings, !path.equals("connected_fence_93185_post"));
                Registry.register(Registries.BLOCK, id, block);
                var item = Registry.register(Registries.ITEM, id, new BlockItem(block, new Item.Settings()));
                ITEMS.put(path, item);
                if (row.has("sourcePoses")) {
                    for (var entry : row.getAsJsonObject("sourcePoses").entrySet()) {
                        var variants = REPLACED_POSES.computeIfAbsent(entry.getKey(), ignored -> new HashMap<>());
                        for (JsonElement pose : entry.getValue().getAsJsonArray()) {
                            if (variants.putIfAbsent(pose.getAsInt(), item) != null) {
                                throw new IllegalStateException("Duplicate fence source pose " + entry.getKey() + " / " + pose);
                            }
                        }
                    }
                } else {
                    for (JsonElement source : row.getAsJsonArray("sources")) {
                        if (REPLACED.putIfAbsent(source.getAsString(), item) != null) {
                            throw new IllegalStateException("Duplicate fence source " + source);
                        }
                    }
                }
            }
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot register DreamWalker connected fences", failure);
        }
    }

    static Iterable<BlockItem> items() {
        return ITEMS.values();
    }

    private static boolean isSource(String path) {
        return REPLACED.containsKey(path) || REPLACED_POSES.containsKey(path);
    }

    private static String canonicalPath(CompositeRootBlock root) {
        return new Identifier(ImportedTypeAliases.canonicalRegistry(root.spec.id.toString())).getPath();
    }

    private static BlockItem replacement(String path, int pose) {
        var variants = REPLACED_POSES.get(path);
        return variants == null ? REPLACED.get(path) : variants.get(pose);
    }

    public static List<ItemStack> creative(List<ItemStack> items) {
        return items.stream().filter(stack -> replacement(stack) == null && !retiredFenceItem(stack)).toList();
    }

    private static boolean retiredFenceItem(ItemStack stack) {
        var id = Registries.ITEM.getId(stack.getItem());
        if (!id.getNamespace().equals("bloodborne_dw")) return false;
        String path = id.getPath();
        int suffix = path.lastIndexOf("_form_");
        String source = suffix < 0 ? path : path.substring(0, suffix);
        return REPLACED.containsKey(source) || source.equals("source_assembly_cyan_stained_glass_pane");
    }

    public static ItemStack forState(BlockState state) {
        if (!(state.getBlock() instanceof CompositeRootBlock root)) return ItemStack.EMPTY;
        String path = root.spec.id.getPath();
        if (!isSource(path)) {
            if (!isSource(canonicalPath(root))) return ItemStack.EMPTY;
            state = ImportedTypeAliases.target(state);
            path = ((CompositeRootBlock) state.getBlock()).spec.id.getPath();
        }
        var item = replacement(path, CompositeSpec.poseVariant(state));
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    public static ActionResult placeLegacy(ItemUsageContext context) {
        ItemStack stack = context.getStack();
        var target = replacement(stack);
        if (target == null) return ActionResult.PASS;
        var converted = new ItemStack(target, stack.getCount());
        var placement = new ItemPlacementContext(context) {
            @Override public ItemStack getStack() { return converted; }
        };
        ActionResult result = target.place(placement);
        if (result.isAccepted()) stack.decrement(stack.getCount() - converted.getCount());
        return result;
    }

    private static BlockItem replacement(ItemStack stack) {
        var id = Registries.ITEM.getId(stack.getItem());
        String registry = id.getPath();
        int form = registry.lastIndexOf("_form_");
        if (id.getNamespace().equals("bloodborne_dw") && form >= 0) {
            String source = registry.substring(0, form);
            if (isSource(source)) {
                return replacement(source, Integer.parseInt(registry.substring(form + 6)));
            }
        }
        if (!(stack.getItem() instanceof BlockItem item) || !(item.getBlock() instanceof CompositeRootBlock root)
                || !isSource(root.spec.id.getPath()) && !isSource(canonicalPath(root))) return null;
        ItemStack source = isSource(root.spec.id.getPath()) ? stack : ImportedTypeAliases.canonicalStack(stack);
        root = (CompositeRootBlock) ((BlockItem) source.getItem()).getBlock();
        int pose = CompositeSpec.poseVariant(root.getDefaultState());
        String path = root.spec.id.getPath();
        NbtCompound state = source.getSubNbt("BlockStateTag");
        if (state != null && REPLACED_POSES.containsKey(path) && Registries.BLOCK.getId(root).getPath().equals(path)) {
            pose = property(state, "variant", pose % 16) + 16 * property(state, "art_bank", pose / 16);
        }
        return replacement(path, pose);
    }

    private static int property(NbtCompound state, String key, int fallback) {
        if (!state.contains(key, 8)) return fallback;
        try {
            return Integer.parseInt(state.getString(key));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
