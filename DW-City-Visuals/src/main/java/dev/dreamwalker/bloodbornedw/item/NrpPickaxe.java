package dev.dreamwalker.bloodbornedw.item;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** Registration and creative-tab entry for the NRP pickaxe. */
public final class NrpPickaxe implements ModInitializer {
    public static final Identifier ID = new Identifier("bloodborne_dw", "nrp_pickaxe");
    public static final Item ITEM = Registry.register(Registries.ITEM, ID, new NrpPickaxeItem());

    @Override
    public void onInitialize() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(ITEM));
    }
}
