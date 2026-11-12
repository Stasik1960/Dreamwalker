package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Property;

/** New registry entries reuse the source specification and behavior. Old entries keep their palettes. */
public final class DistinctForms {
    private record Form(String alias, int pose) {}
    private record Selector(int pose, IntProperty bank, SlabType slab) {}

    private static final ThreadLocal<Selector> CONSTRUCTING = new ThreadLocal<>();
    private static final Map<Object, Selector> FIXED = new IdentityHashMap<>();
    private static final Map<CompositeRootBlock, Map<Integer, CompositeItem>> ITEMS = new IdentityHashMap<>();
    private static final Map<SlabBlock, Map<SlabType, SlabBlock>> SLABS = new IdentityHashMap<>();

    private DistinctForms() {}

    public static void register(Map<String, CompositeRootBlock> blocks, Map<String, CompositeItem> items) {
        var input = DistinctForms.class.getResourceAsStream("/bloodborne_dw/distinct-forms.json");
        if (input == null) return;
        try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            var families = JsonParser.parseReader(reader).getAsJsonObject();
            for (var family : families.entrySet()) {
                CompositeRootBlock source = blocks.get(family.getKey());
                if (source == null) throw new IllegalStateException("Missing original form family " + family.getKey());
                IntProperty bank = ImportedArtworkBlock.bankProperty(source.getDefaultState());
                for (var entry : family.getValue().getAsJsonArray()) {
                    int pose = entry.getAsInt();
                    if (pose >= source.spec.variants.size() || (bank == null ? pose >= 16 : !bank.getValues().contains(pose / 16))) {
                        throw new IllegalStateException("Unreachable form " + source.spec.id + " / " + pose);
                    }
                    String path = family.getKey() + "_form_" + pose;
                    registerForm(source, new Form(path, pose), bank, blocks, items);
                }
            }
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot register the distinct Bloodborne forms", failure);
        }
    }

    private static void registerForm(CompositeRootBlock source, Form form, IntProperty bank,
            Map<String, CompositeRootBlock> blocks, Map<String, CompositeItem> items) throws ReflectiveOperationException {
        var id = CompositeArchitecture.id(form.alias());
        if (Registries.BLOCK.containsId(id) || Registries.ITEM.containsId(id)) {
            throw new IllegalStateException("Duplicate form " + id);
        }
        // The published distribution also contains specialized constructors from binary overlays.
        var constructor = source.getClass().getDeclaredConstructor(CompositeSpec.class);
        constructor.setAccessible(true);
        CompositeRootBlock block;
        CONSTRUCTING.set(new Selector(form.pose(), bank, null));
        try {
            block = constructor.newInstance(source.spec);
        } finally {
            CONSTRUCTING.remove();
        }
        if (!fixed(block) || CompositeSpec.poseVariant(block.getDefaultState()) != form.pose()) {
            throw new IllegalStateException("Incorrect state for " + id);
        }
        Registry.register(Registries.BLOCK, id, block);
        CompositeItem item = block instanceof ImportedCompactBlock compact
                ? new ImportedCompactBlock.CompactItem(compact) : new CompositeItem(block);
        Registry.register(Registries.ITEM, id, item);
        blocks.put(form.alias(), block);
        items.put(form.alias(), item);
        ITEMS.computeIfAbsent(source, ignored -> new HashMap<>()).put(form.pose(), item);
    }

    public static void registerSlabs(List<SlabBlock> blocks) {
        var input = DistinctForms.class.getResourceAsStream("/bloodborne_dw/distinct-slabs.json");
        if (input == null) return;
        try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            for (var entry : JsonParser.parseReader(reader).getAsJsonArray()) {
                String path = entry.getAsString();
                SlabBlock source = (SlabBlock) Registries.BLOCK.get(CompositeArchitecture.id(path));
                var type = source.getClass();
                var constructor = type.getDeclaredConstructor(int.class, type);
                constructor.setAccessible(true);
                var forms = new EnumMap<SlabType, SlabBlock>(SlabType.class);
                for (SlabType shape : SlabType.values()) {
                    SlabBlock block;
                    CONSTRUCTING.set(new Selector(0, null, shape));
                    try {
                        block = constructor.newInstance(-1, null);
                    } finally {
                        CONSTRUCTING.remove();
                    }
                    if (!fixed(block) || block.getDefaultState().get(SlabBlock.TYPE) != shape) {
                        throw new IllegalStateException("Incorrect restored slab state for " + path);
                    }
                    var id = CompositeArchitecture.id(path + switch (shape) {
                        case BOTTOM -> "_half";
                        case TOP -> "_upper";
                        case DOUBLE -> "_full";
                    });
                    Registry.register(Registries.BLOCK, id, block);
                    Registry.register(Registries.ITEM, id, new BlockItem(block, new net.minecraft.item.Item.Settings()));
                    blocks.add(block);
                    forms.put(shape, block);
                }
                SLABS.put(source, forms);
            }
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot register distinct Bloodborne slabs", failure);
        }
    }

    public static ItemStack slabPick(BlockState state) {
        var forms = SLABS.get(state.getBlock());
        return forms == null ? ItemStack.EMPTY : new ItemStack(forms.get(state.get(SlabBlock.TYPE)));
    }

    public static void omitSelectors(Object owner, Map<String, Property<?>> properties) {
        if (!(owner instanceof CompositeRootBlock || owner instanceof SlabBlock)) return;
        Selector selector = CONSTRUCTING.get();
        if (selector == null) return;
        if (selector.slab() != null) {
            if (!(owner instanceof SlabBlock) || properties.remove("type") != SlabBlock.TYPE) {
                throw new IllegalStateException("Unexpected type property on a restored slab");
            }
            if (selector.slab() == SlabType.DOUBLE) properties.remove("waterlogged");
            FIXED.put(owner, selector);
            return;
        }
        if (properties.remove("variant") != CompositeRootBlock.VARIANT || properties.remove("art_bank") != selector.bank()) {
            throw new IllegalStateException("Unexpected model properties on a form block");
        }
        FIXED.put(owner, selector);
    }

    public static Comparable<?> selector(Object owner, Property<?> property) {
        if (!(owner instanceof CompositeRootBlock || owner instanceof SlabBlock)) return null;
        Selector selector = FIXED.get(owner);
        if (selector == null) return null;
        if (selector.slab() != null) {
            if (property == SlabBlock.TYPE) return selector.slab();
            if (property == SlabBlock.WATERLOGGED && selector.slab() == SlabType.DOUBLE) return false;
            return null;
        }
        if (property == CompositeRootBlock.VARIANT) return selector.pose() % 16;
        if (property == selector.bank()) return selector.pose() / 16;
        return null;
    }

    public static boolean fixed(Object block) {
        return FIXED.containsKey(block);
    }

    public static IntProperty artBank(Object block) {
        Selector selector = FIXED.get(block);
        return selector == null ? null : selector.bank();
    }

    public static ItemStack forArt(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem item) || !(item.getBlock() instanceof CompositeRootBlock root)) return stack;
        var forms = ITEMS.get(root);
        if (forms == null) return stack;
        NbtCompound tag = stack.getSubNbt("BlockStateTag");
        int pose = integer(tag, "variant") + 16 * integer(tag, "art_bank");
        var form = forms.get(pose);
        return form == null ? stack : replaceItem(stack, form);
    }

    public static List<ItemStack> catalogue(List<ItemStack> original) {
        List<ItemStack> result = new ArrayList<>(original.size());
        for (ItemStack stack : original) {
            if (stack.getItem() instanceof BlockItem item) {
                if (SLABS.containsKey(item.getBlock())) continue;
                var form = forArt(stack);
                if (form != stack) continue;
            }
            result.add(stack);
        }
        return List.copyOf(result);
    }

    private static ItemStack replaceItem(ItemStack stack, CompositeItem item) {
        ItemStack result = new ItemStack(item, stack.getCount());
        if (stack.hasNbt()) result.setNbt(stack.getNbt().copy());
        NbtCompound tag = result.getSubNbt("BlockStateTag");
        if (tag != null) {
            tag.remove("variant");
            tag.remove("art_bank");
            if (tag.isEmpty()) result.removeSubNbt("BlockStateTag");
        }
        return result;
    }

    private static int integer(NbtCompound tag, String name) {
        if (tag == null || !tag.contains(name)) return 0;
        try {
            return Integer.parseInt(tag.getString(name));
        } catch (NumberFormatException invalid) {
            return 0;
        }
    }
}
