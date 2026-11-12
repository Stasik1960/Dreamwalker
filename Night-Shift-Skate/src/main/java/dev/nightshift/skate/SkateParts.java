package dev.nightshift.skate;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.text.Text;

public final class SkateParts {
    private static final List<SkatePart> PARTS = Stream.of(
            Arrays.stream(SkatePart.Deck.values()), Arrays.stream(SkatePart.Trucks.values()),
            Arrays.stream(SkatePart.Wheels.values())).flatMap(stream -> stream).map(SkatePart.class::cast).toList();
    private static final Map<SkatePart, Item> ITEMS = new LinkedHashMap<>();

    public static final Block WORKBENCH = Registry.register(Registries.BLOCK, NightShiftSkate.id("skate_workbench"),
            new SkateWorkbenchBlock(AbstractBlock.Settings.copy(Blocks.CRAFTING_TABLE).nonOpaque()));
    public static final ScreenHandlerType<SkateWorkbenchScreenHandler> SCREEN = Registry.register(
            Registries.SCREEN_HANDLER, NightShiftSkate.id("skate_workbench"),
            new ScreenHandlerType<>(SkateWorkbenchScreenHandler::new, FeatureFlags.VANILLA_FEATURES));

    private SkateParts() {}

    static void register() {
        Registry.register(Registries.ITEM, NightShiftSkate.id("skate_workbench"),
                new BlockItem(WORKBENCH, new Item.Settings()));
        for (SkatePart part : PARTS) {
            ITEMS.put(part, Registry.register(Registries.ITEM, NightShiftSkate.id(part.itemId()),
                    new SkatePartItem(part, new Item.Settings())));
        }
        Registry.register(Registries.ITEM_GROUP, NightShiftSkate.id("skate_parts"), FabricItemGroup.builder()
                .displayName(Text.translatable("itemGroup.night_shift_skate.skate_parts"))
                .icon(() -> new ItemStack(WORKBENCH))
                .entries((context, entries) -> {
                    entries.add(WORKBENCH);
                    entries.add(NightShiftSkate.SKATEBOARD);
                    entries.add(NightShiftSkate.IRON_VESPERS);
                    entries.add(NightShiftSkate.GRAVE_PROCESSION);
                    ITEMS.values().forEach(entries::add);
                }).build());
    }

    public static List<SkatePart> all() {
        return PARTS;
    }

    public static Item item(SkatePart part) {
        return ITEMS.get(part);
    }

    static SkatePart fromStack(ItemStack stack) {
        return stack.getItem() instanceof SkatePartItem item ? item.part() : null;
    }
}
