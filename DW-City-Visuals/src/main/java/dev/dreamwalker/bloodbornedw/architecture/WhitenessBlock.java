package dev.dreamwalker.bloodbornedw.architecture;

import dev.dreamwalker.bloodbornedw.architecture.mixin.WhitenessBlockEntityTypeMixin;
import dev.dreamwalker.bloodbornedw.composite.CompositeArchitecture;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.Block;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/** A white visual variant of the existing placeable darkness marker. */
public final class WhitenessBlock implements ModInitializer {
    public static final Identifier ID = new Identifier("bloodborne_dw", "dw_whiteness");
    public static final DwUtilityBlocks.MarkerBlock BLOCK = Registry.register(
            Registries.BLOCK, ID, new DwUtilityBlocks.MarkerBlock(DwUtilityBlocks.Kind.DARKNESS));
    public static final Item ITEM = Registry.register(Registries.ITEM, ID, new WhitenessItem(BLOCK));

    @Override
    public void onInitialize() {
        var type = (WhitenessBlockEntityTypeMixin) (Object) CompositeArchitecture.CELL_ENTITY;
        Set<Block> supported = new HashSet<>(type.dw$whitenessGetSupportedBlocks());
        supported.add(BLOCK);
        type.dw$whitenessSetSupportedBlocks(Set.copyOf(supported));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(entries -> entries.add(ITEM));
    }

    /** Keeps the darkness marker's custom placement and ledger behavior under the new block ID. */
    private static final class WhitenessItem extends DwUtilityBlocks.MarkerItem {
        WhitenessItem(DwUtilityBlocks.MarkerBlock block) {
            super(block);
        }

        @Override
        public Text getName(ItemStack stack) {
            return Text.translatable("item.bloodborne_dw.dw_whiteness");
        }

        @Override
        public void appendTooltip(ItemStack stack, World world, List<Text> lines,
                                  TooltipContext context) {
            // The inherited marker tooltip describes darkness, which is misleading for this white surface.
        }
    }
}
