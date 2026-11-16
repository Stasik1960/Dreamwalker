package com.yharnam.casino;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class YharnamCasino implements ModInitializer {
    public static final String ID = "yharnam_casino";
    static final int[] CHIP_VALUES = {1, 5, 10, 50, 100, 500, 1000};
    private static final Map<Integer, ChipItem> CHIPS = new LinkedHashMap<>();
    private static final Map<SlotStyle, SlotMachineBlock> CABINETS = new EnumMap<>(SlotStyle.class);
    public static BlockEntityType<SlotMachineEntity> SLOT_ENTITY;
    static Item PRIZE_TICKET;
    static Item CABINET_KEY;
    static Item GM_KEY;
    static Item CHIP_IOU;

    @Override
    public void onInitialize() {
        for (int value : CHIP_VALUES) {
            CHIPS.put(value, Registry.register(Registries.ITEM, id("chip_" + value), new ChipItem(value)));
        }
        PRIZE_TICKET = Registry.register(Registries.ITEM, id("prize_ticket"), new Item(new Item.Settings()) {
            @Override
            public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
                tooltip.add(Text.translatable("item.yharnam_casino.prize_ticket.tooltip"));
            }
        });
        CABINET_KEY = Registry.register(Registries.ITEM,id("cabinet_key"),new CabinetKeyItem());
        GM_KEY = Registry.register(Registries.ITEM,id("gm_key"),new GameMasterKeyItem());
        CHIP_IOU = Registry.register(Registries.ITEM,id("chip_iou"),new ChipIouItem());
        for (SlotStyle style : SlotStyle.values()) {
            SlotMachineBlock cabinet = new SlotMachineBlock(style, AbstractBlock.Settings.create()
                    .mapColor(MapColor.DEEPSLATE_GRAY).strength(3.0F, 1200.0F)
                    .sounds(BlockSoundGroup.METAL).nonOpaque());
            Identifier identifier = id(style.id());
            Registry.register(Registries.BLOCK, identifier, cabinet);
            Registry.register(Registries.ITEM, identifier, new BlockItem(cabinet, new Item.Settings()));
            CABINETS.put(style, cabinet);
        }
        SLOT_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, id("slot_machine"),
                FabricBlockEntityTypeBuilder.create(SlotMachineEntity::new, CABINETS.values().toArray(Block[]::new)).build());

        Registry.register(Registries.ITEM_GROUP, id("casino"), FabricItemGroup.builder()
                .displayName(Text.translatable("itemGroup.yharnam_casino.casino"))
                .icon(() -> new ItemStack(chip(100)))
                .entries((context, entries) -> {
                    CABINETS.values().forEach(entries::add);
                    CHIPS.values().forEach(entries::add);
                    entries.add(PRIZE_TICKET);
                    entries.add(CABINET_KEY);
                    entries.add(GM_KEY);
                }).build());

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            CasinoMailbox mailbox = CasinoMailbox.get(server);
            mailbox.claimArchivedProfit(handler.getPlayer().getUuid());
            mailbox.deliver(handler.getPlayer(),false);
        });
        PlayerBlockBreakEvents.BEFORE.register((world,player,pos,state,blockEntity) -> {
            SlotMachineEntity cabinet = SlotMachineBlock.findMachine(world,pos);
            if (cabinet == null || cabinet.canDismantle(player)) {
                return true;
            }
            player.sendMessage(Text.translatable("message.yharnam_casino.dismantle_denied"),true);
            return false;
        });
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("yharnamcasino")
                .then(CommandManager.literal("claim").executes(YharnamCasino::claim))
                .then(CommandManager.literal("gmkey")
                        .requires(source -> source.getEntity() instanceof ServerPlayerEntity player
                                && CasinoStaffAccess.isStaff(player))
                        .executes(YharnamCasino::giveStaffKey))
                .then(CommandManager.literal("rules").executes(context -> {
                    ServerCommandSource source = context.getSource();
                    source.sendFeedback(() -> Text.translatable("message.yharnam_casino.rules"), false);
                    for (SlotStyle style : SlotStyle.values()) {
                        source.sendFeedback(() -> styleRules(style), false);
                    }
                    return 1;
                })));
    }

    private static Text styleRules(SlotStyle style) {
        MutableText line = cabinet(style).getName().copy().append(" | ");
        for (int symbol = 0; symbol < SlotMath.SYMBOLS; symbol++) {
            if (symbol > 0) {
                line.append(" · ");
            }
            line.append(Text.translatable(style.symbolKey(symbol))).append(" ×" + SlotMath.tripleMultiplier(symbol));
        }
        return line;
    }

    private static int claim(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        CasinoMailbox mailbox = CasinoMailbox.get(context.getSource().getServer());
        mailbox.claimArchivedProfit(player.getUuid());
        return mailbox.deliver(player,true) ? 1 : 0;
    }

    private static int giveStaffKey(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        int slot = player.getInventory().getEmptySlot();
        if (slot < 0) {
            context.getSource().sendError(Text.translatable("message.yharnam_casino.gm_key_inventory_full"));
            return 0;
        }
        player.getInventory().setStack(slot,new ItemStack(GM_KEY));
        context.getSource().sendFeedback(() -> Text.translatable("message.yharnam_casino.gm_key_given"),false);
        return 1;
    }

    public static Identifier id(String path) {
        return new Identifier(ID, path);
    }

    static ChipItem chip(int value) {
        return CHIPS.get(value);
    }

    static SlotMachineBlock cabinet(SlotStyle style) {
        return CABINETS.get(style);
    }
}
