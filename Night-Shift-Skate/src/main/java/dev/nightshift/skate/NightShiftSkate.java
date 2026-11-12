package dev.nightshift.skate;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class NightShiftSkate implements ModInitializer {
    public static final String ID = "night_shift_skate";
    public static final Identifier CONTROLS = id("controls");
    public static final Identifier DISMOUNT = id("dismount");
    public static final Identifier SCORE = id("score");

    public static final EntityType<SkateboardEntity> SKATEBOARD_ENTITY = Registry.register(
            Registries.ENTITY_TYPE, id("skateboard"), EntityType.Builder
                    .<SkateboardEntity>create(SkateboardEntity::new, SpawnGroup.MISC)
                    .setDimensions(0.52f, 0.18f)
                    .maxTrackingRange(8)
                    .trackingTickInterval(1)
                    .build(ID + ":skateboard"));

    public static final Item SKATEBOARD = registerBoard(BoardStyle.NIGHT_SHIFT);
    public static final Item IRON_VESPERS = registerBoard(BoardStyle.IRON_VESPERS);
    public static final Item GRAVE_PROCESSION = registerBoard(BoardStyle.GRAVE_PROCESSION);

    private static Item registerBoard(BoardStyle style) {
        return Registry.register(Registries.ITEM, id(style.itemId()), new SkateboardItem(new Item.Settings().maxCount(1), style));
    }

    public static Identifier id(String path) {
        return new Identifier(ID, path);
    }

    @Override
    public void onInitialize() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
            entries.add(SKATEBOARD);
            entries.add(IRON_VESPERS);
            entries.add(GRAVE_PROCESSION);
        });
        ServerPlayNetworking.registerGlobalReceiver(CONTROLS, (server, player, handler, buf, responseSender) -> {
            if (buf.readableBytes() != 1) {
                return;
            }
            int input = buf.readUnsignedByte();
            server.execute(() -> {
                if (player.getVehicle() instanceof SkateboardEntity board && board.getFirstPassenger() == player) {
                    board.acceptControls(input);
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(DISMOUNT, (server, player, handler, buf, responseSender) -> {
            if (buf.isReadable()) {
                return;
            }
            server.execute(() -> {
                if (player.getVehicle() instanceof SkateboardEntity board && board.getFirstPassenger() == player) {
                    player.stopRiding();
                }
            });
        });
    }
}
