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
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundEvent;
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
    static final SoundEvent LEVER_PULL = SoundEvent.of(id("lever_pull"));
    static final SoundEvent JACKPOT = SoundEvent.of(id("jackpot"));
    private static final Map<Integer, ChipItem> CHIPS = new LinkedHashMap<>();
    private static final Map<SlotStyle, SlotMachineBlock> CABINETS = new EnumMap<>(SlotStyle.class);
    private static final Map<SlotStyle, BrokenSlotMachineBlock> BROKEN_CABINETS = new EnumMap<>(SlotStyle.class);
    public static BlockEntityType<SlotMachineEntity> SLOT_ENTITY;
    public static BlockEntityType<ChipCabinetEntity> CHIP_CABINET_ENTITY;
    public static ScreenHandlerType<DenominationMenu> DENOMINATION_MENU;
    static ChipCabinetBlock CHIP_CABINET;
    public static GamingFurnitureBlock POKER_TABLE, BLACK_JACK_TABLE, FORTUNE_WHEEL;
    public static BlockEntityType<CardTableEntity> CARD_TABLE_ENTITY;
    public static BlockEntityType<FortuneWheelEntity> WHEEL_ENTITY;
    public static CardDeckItem CARD_DECK;
    static Item PRIZE_TICKET;
    static Item CABINET_KEY;
    static Item GM_KEY;
    static Item CHIP_IOU;

    @Override
    public void onInitialize() {
        Registry.register(Registries.SOUND_EVENT, id("lever_pull"), LEVER_PULL);
        Registry.register(Registries.SOUND_EVENT, id("jackpot"), JACKPOT);
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
            BrokenSlotMachineBlock broken = new BrokenSlotMachineBlock(style,AbstractBlock.Settings.create()
                    .mapColor(MapColor.DEEPSLATE_GRAY).strength(3.0F,1200.0F)
                    .sounds(BlockSoundGroup.METAL).nonOpaque());
            Identifier brokenId = id(style.id()+"_broken");
            Registry.register(Registries.BLOCK,brokenId,broken);
            Registry.register(Registries.ITEM,brokenId,new BlockItem(broken,new Item.Settings()));
            BROKEN_CABINETS.put(style,broken);
        }
        SLOT_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, id("slot_machine"),
                FabricBlockEntityTypeBuilder.create(SlotMachineEntity::new, CABINETS.values().toArray(Block[]::new)).build());
        CHIP_CABINET = Registry.register(Registries.BLOCK,id("chip_cabinet"),new ChipCabinetBlock(
                AbstractBlock.Settings.create().mapColor(MapColor.BROWN)
                        .strength(2.5F).sounds(BlockSoundGroup.WOOD).nonOpaque()));
        Registry.register(Registries.ITEM,id("chip_cabinet"),new BlockItem(CHIP_CABINET,new Item.Settings()));
        CHIP_CABINET_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE,id("chip_cabinet"),
                FabricBlockEntityTypeBuilder.create(ChipCabinetEntity::new,CHIP_CABINET).build());
        POKER_TABLE = furniture("poker_table", false, false);
        BLACK_JACK_TABLE = furniture("black_jack_table", false, true);
        FORTUNE_WHEEL = furniture("fortune_wheel", true, false);
        CARD_DECK = Registry.register(Registries.ITEM, id("card_deck"),
                new CardDeckItem(id("textures/entity/cards/classic.png")));
        CARD_TABLE_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, id("card_table"),
                FabricBlockEntityTypeBuilder.create(CardTableEntity::new, POKER_TABLE, BLACK_JACK_TABLE).build());
        WHEEL_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, id("fortune_wheel"),
                FabricBlockEntityTypeBuilder.create(FortuneWheelEntity::new, FORTUNE_WHEEL).build());
        DENOMINATION_MENU = Registry.register(Registries.SCREEN_HANDLER,id("denominations"),
                new ScreenHandlerType<>(DenominationMenu::new,FeatureFlags.VANILLA_FEATURES));

        Registry.register(Registries.ITEM_GROUP, id("casino"), FabricItemGroup.builder()
                .displayName(Text.translatable("itemGroup.yharnam_casino.casino"))
                .icon(() -> new ItemStack(chip(100)))
                .entries((context, entries) -> {
                    CABINETS.values().forEach(entries::add);
                    BROKEN_CABINETS.values().forEach(entries::add);
                    entries.add(CHIP_CABINET);
                    entries.add(POKER_TABLE);
                    entries.add(BLACK_JACK_TABLE);
                    entries.add(FORTUNE_WHEEL);
                    entries.add(CARD_DECK);
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
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof FortuneWheelEntity wheel && wheel.active()) wheel.advanceClock(world);
        });
        // Vanilla bypasses Block.onUse when the player sneaks while holding an item.
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            BlockState state = world.getBlockState(hit.getBlockPos());
            if (player.isSneaking() && !player.isSpectator() && hand == net.minecraft.util.Hand.MAIN_HAND
                    && state.getBlock() instanceof GamingFurnitureBlock furniture
                    && world.canPlayerModifyAt(player, hit.getBlockPos())) {
                return furniture.onUse(state, world, hit.getBlockPos(), player, hand, hit);
            }
            return net.minecraft.util.ActionResult.PASS;
        });
        PlayerBlockBreakEvents.BEFORE.register((world,player,pos,state,blockEntity) -> {
            if (state.getBlock() instanceof GamingFurnitureBlock furniture
                    && world.getBlockEntity(furniture.anchor(pos, state)) instanceof CardTableEntity table
                    && table.playing() && !(player instanceof ServerPlayerEntity staff && CasinoStaffAccess.isStaff(staff))) {
                player.sendMessage(Text.literal("Дождитесь конца раздачи"), true);
                return false;
            }
            SlotMachineEntity cabinet = SlotMachineBlock.findMachine(world,pos);
            if (cabinet == null || cabinet.canDismantle(player)) {
                return true;
            }
            player.sendMessage(Text.translatable("message.yharnam_casino.dismantle_denied"),true);
            return false;
        });
    }

    private static GamingFurnitureBlock furniture(String name, boolean wheel, boolean blackjack) {
        var settings = AbstractBlock.Settings.create().mapColor(MapColor.BROWN).strength(2.5F)
                .pistonBehavior(net.minecraft.block.piston.PistonBehavior.BLOCK).sounds(BlockSoundGroup.WOOD).nonOpaque();
        var block = Registry.register(Registries.BLOCK, id(name), new GamingFurnitureBlock(settings, wheel, blackjack));
        Registry.register(Registries.ITEM, id(name), new BlockItem(block, new Item.Settings()));
        return block;
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
