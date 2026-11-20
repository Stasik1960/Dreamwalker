package com.yharnam.casino;

import java.util.List;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** Empty shells can be packed with an off-hand stack. Each sealed shell represents exactly one prize. */
public final class CapsuleItem extends Item {
    private static final String SEALED = "GachaSealed";
    private static final String CONTENT = "GachaContents";
    private final GachaRarity rarity;

    public CapsuleItem(GachaRarity rarity) {
        super(new Settings().maxCount(64));
        this.rarity = rarity;
    }

    public GachaRarity rarity() { return rarity; }

    public static boolean sealed(ItemStack stack) {
        return stack.getNbt() != null && stack.getNbt().getBoolean(SEALED);
    }

    static void seal(ItemStack shell, ItemStack contents) {
        shell.getOrCreateNbt().putBoolean(SEALED, true);
        if (!contents.isEmpty()) shell.setSubNbt(CONTENT, contents.writeNbt(new NbtCompound()));
    }

    public static ItemStack contents(ItemStack shell) {
        NbtCompound nbt = shell.getSubNbt(CONTENT);
        return nbt == null ? ItemStack.EMPTY : ItemStack.fromNbt(nbt);
    }

    @Override public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (world.isClient) return TypedActionResult.success(stack, true);
        if (!(player instanceof ServerPlayerEntity server)) return TypedActionResult.pass(stack);
        if (sealed(stack)) {
            ItemStack prize = contents(stack);
            boolean emptyPrize=prize.isEmpty();
            stack.decrement(1);
            if (!prize.isEmpty()) {
                if (stack.isEmpty()) player.setStackInHand(hand, prize);
                else player.getInventory().offerOrDrop(prize);
            }
            player.sendMessage(Text.translatable(emptyPrize ? "gacha.empty_open" : "gacha.opened"), true);
            world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, .8f, 1.3f);
        } else if (player.isSneaking() && hand == Hand.MAIN_HAND && !player.getOffHandStack().isEmpty()
                && !(player.getOffHandStack().getItem() instanceof CapsuleItem)) {
            ItemStack one = stack.split(1);
            seal(one, player.getOffHandStack());
            player.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
            if (stack.isEmpty()) player.setStackInHand(hand, one);
            else player.getInventory().offerOrDrop(one);
            player.sendMessage(Text.translatable("gacha.packed"), true);
        } else {
            player.sendMessage(Text.translatable("gacha.pack_help"), true);
        }
        server.getInventory().markDirty();
        return TypedActionResult.success(player.getStackInHand(hand), false);
    }

    @Override public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> lines, TooltipContext context) {
        lines.add(Text.translatable("gacha.odds", rarity.weight()));
        lines.add(Text.translatable(sealed(stack) ? "gacha.open_help" : "gacha.pack_help"));
    }
}
