package dev.nightwatch.carriage;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NightWatchCarriage implements ModInitializer {
    public static final String ID = "nightwatchcarriage";
    public static final Logger LOGGER = LoggerFactory.getLogger(ID);
    public static final EntityType<CarriageEntity> CARRIAGE = Registry.register(Registries.ENTITY_TYPE,
        id("carriage"), FabricEntityTypeBuilder.<CarriageEntity>create(SpawnGroup.MISC, CarriageEntity::new)
            .dimensions(EntityDimensions.changing(2.24f, 2.77f)).trackRangeChunks(10).trackedUpdateRate(1).build());
    public static final Item CARRIAGE_ITEM = Registry.register(Registries.ITEM, id("carriage"),
        new CarriageItem(new Item.Settings().maxCount(1)));

    public static Identifier id(String path) {
        return new Identifier(ID, path);
    }

    @Override
    public void onInitialize() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(CARRIAGE_ITEM));
        CarriagePackets.register();
    }
}
