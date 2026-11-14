package dev.yharnamdice;

import dev.yharnamdice.block.DiceTableBlock;
import dev.yharnamdice.block.DiceTableEntity;
import dev.yharnamdice.game.TableSessions;
import dev.yharnamdice.item.DicePouchItem;
import dev.yharnamdice.item.RuleDiskItem;
import dev.yharnamdice.item.PouchMenu;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.block.AbstractBlock;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public final class DiceMod implements ModInitializer {
    public static final String ID = "yharnam_dice";
    public static final String[] SKINS = {
        "chapel_ivory", "black_obsidian_silver", "red_garnet_bone", "blackened_brass",
        "blood_walnut", "moon_marble", "blue_stained_glass_silver", "verdigris_copper_ivory",
        "violet_vespers", "ash_iron", "ancient_antler_soot", "weathered_granite_white",
        "rose_of_the_cathedral", "moon_in_eclipse", "hunters_briar", "choir_reliquary",
        "bell_for_the_dead", "gutter_scrap_die"
    };
    public static final DiceTableBlock TABLE = Registry.register(Registries.BLOCK, id("dice_table"),
            new DiceTableBlock(AbstractBlock.Settings.create().strength(2.5f).sounds(BlockSoundGroup.WOOD)
                    .nonOpaque().pistonBehavior(PistonBehavior.BLOCK)));
    public static final BlockEntityType<DiceTableEntity> TABLE_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE,
            id("dice_table"), FabricBlockEntityTypeBuilder.create(DiceTableEntity::new, TABLE).build());
    public static final EntityType<TableSeat> SEAT = Registry.register(Registries.ENTITY_TYPE, id("table_seat"),
            FabricEntityTypeBuilder.<TableSeat>create(SpawnGroup.MISC, TableSeat::new)
                    .dimensions(EntityDimensions.fixed(.01f, .01f)).trackRangeBlocks(32).trackedUpdateRate(2).disableSaving().build());
    public static final Item TABLE_ITEM = item("dice_table", new BlockItem(TABLE, new Item.Settings()));
    public static final DicePouchItem POUCH = item("dice_pouch", new DicePouchItem(new Item.Settings().maxCount(1)));
    public static final RuleDiskItem DISK = item("rule_disk", new RuleDiskItem(new Item.Settings().maxCount(1)));
    public static final ScreenHandlerType<PouchMenu> POUCH_MENU = Registry.register(Registries.SCREEN_HANDLER, id("pouch"),
            new ExtendedScreenHandlerType<>(PouchMenu::new));
    public static final List<Item> DICE = new ArrayList<>();
    public static final SoundEvent ROLL = sound("roll"), FELT = sound("felt"), RIM = sound("rim"),
            CLACK = sound("dice"), SIT = sound("seat");

    @Override
    public void onInitialize() {
        for (String skin : SKINS) DICE.add(item(skin, new Item(new Item.Settings())));
        Registry.register(Registries.ITEM_GROUP, id("dice"), FabricItemGroup.builder()
                .icon(() -> new ItemStack(POUCH)).displayName(Text.translatable("itemGroup.yharnam_dice"))
                .entries((context, entries) -> {
                    entries.add(TABLE_ITEM);
                    entries.add(POUCH);
                    entries.add(DISK);
                    for (String builtin : new String[]{"witcher_poker", "kcd_farkle", "twenty_one"}) {
                        entries.add(DISK.builtin(builtin));
                    }
                    DICE.forEach(entries::add);
                }).build());
        Protocol.registerServer();
        ServerTickEvents.END_WORLD_TICK.register(TableSessions::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> TableSessions.disconnect(handler.player));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(TableSessions::clear);
    }

    public static Identifier id(String path) { return new Identifier(ID, path); }

    public static int skin(ItemStack stack) { return DICE.indexOf(stack.getItem()); }

    private static SoundEvent sound(String path) {
        Identifier id = id(path);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    private static <T extends Item> T item(String path, T item) { return Registry.register(Registries.ITEM, id(path), item); }
}
