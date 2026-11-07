package dev.dreamwalker.bloodbornedw.architecture;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockSetType;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** The 90960 leaf in a vanilla two-cell wooden door. */
public final class PlainWoodDoor extends DoorBlock {
    private PlainWoodDoor() {
        super(AbstractBlock.Settings.copy(Blocks.OAK_DOOR), BlockSetType.OAK);
    }

    public static final class Registration implements ModInitializer {
        @Override
        public void onInitialize() {
            Identifier id = new Identifier("bloodborne_dw", "door_90960_plain");
            DoorBlock door = Registry.register(Registries.BLOCK, id, new PlainWoodDoor());
            Item item = Registry.register(Registries.ITEM, id, new BlockItem(door, new Item.Settings()));
            ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(entries -> entries.add(item));
        }
    }
}
