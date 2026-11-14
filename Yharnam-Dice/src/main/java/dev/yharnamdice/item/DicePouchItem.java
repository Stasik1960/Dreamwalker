package dev.yharnamdice.item;

import dev.yharnamdice.DiceMod;
import dev.yharnamdice.game.TableSessions;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;
import java.util.UUID;

public final class DicePouchItem extends Item {
    public static final int SIZE = 20;
    private static final String CONTENTS = "Dice";
    private static final String KEY = "PouchId";

    public DicePouchItem(Settings settings) { super(settings); }

    public static UUID key(ItemStack pouch) {
        NbtCompound tag = pouch.getNbt();
        return tag == null || !tag.containsUuid(KEY) ? null : tag.getUuid(KEY);
    }

    public static UUID identify(ItemStack pouch) {
        UUID id = key(pouch);
        if (id == null) {
            id = UUID.randomUUID();
            pouch.getOrCreateNbt().putUuid(KEY, id);
        }
        return id;
    }

    public static ItemStack[] contents(ItemStack pouch) {
        ItemStack[] dice = new ItemStack[SIZE];
        java.util.Arrays.fill(dice, ItemStack.EMPTY);
        NbtCompound tag = pouch.getNbt();
        if (tag == null) return dice;
        NbtList entries = tag.getList(CONTENTS, NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < Math.min(SIZE, entries.size()); i++) {
            NbtCompound entry = entries.getCompound(i);
            int index = entry.getByte("Slot") & 255;
            if (index >= SIZE) continue;
            ItemStack die = ItemStack.fromNbt(entry);
            if (DiceMod.skin(die) >= 0) dice[index] = die.copyWithCount(1);
        }
        return dice;
    }

    static void store(ItemStack pouch, net.minecraft.inventory.Inventory inventory) {
        NbtList list = new NbtList();
        for (int i = 0; i < SIZE; i++) {
            ItemStack stack = inventory.getStack(i);
            if (DiceMod.skin(stack) < 0) continue;
            NbtCompound tag = stack.copyWithCount(1).writeNbt(new NbtCompound());
            tag.putByte("Slot", (byte) i);
            list.add(tag);
        }
        pouch.getOrCreateNbt().put(CONTENTS, list);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack pouch = player.getStackInHand(hand);
        if (player instanceof ServerPlayerEntity server) {
            if (TableSessions.isPlaying(server)) return TypedActionResult.fail(pouch);
            identify(pouch);
            int index = hand == Hand.OFF_HAND ? 40 : player.getInventory().selectedSlot;
            server.openHandledScreen(new ExtendedScreenHandlerFactory() {
                @Override public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) { buf.writeVarInt(index); }
                @Override public Text getDisplayName() { return pouch.getName(); }
                @Override public ScreenHandler createMenu(int sync, PlayerInventory inventory, PlayerEntity owner) {
                    return new PouchMenu(sync, inventory, index, pouch);
                }
            });
        }
        return TypedActionResult.success(pouch, world.isClient);
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        long count = java.util.Arrays.stream(contents(stack)).filter(die -> !die.isEmpty()).count();
        tooltip.add(Text.translatable("yharnam_dice.pouch.count", count, SIZE));
    }
}
