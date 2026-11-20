package dev.yharnamdice.item;

import dev.yharnamdice.Protocol;
import dev.yharnamdice.game.TableSessions;
import dev.yharnamdice.rules.RulesConfig;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class RuleDiskItem extends Item {
    private static final String JSON = "RuleJSON";

    public RuleDiskItem(Settings settings) { super(settings); }

    public static String json(ItemStack stack) {
        return stack.hasNbt() ? stack.getNbt().getString(JSON) : "";
    }

    public static RulesConfig rules(ItemStack stack) {
        String input = json(stack);
        if (input.isEmpty()) return null;
        try { return RulesConfig.fromJson(input); }
        catch (IllegalArgumentException invalid) { return null; }
    }

    public static void write(ItemStack stack, String input) {
        if (input.isBlank()) {
            stack.removeSubNbt(JSON);
            return;
        }
        stack.getOrCreateNbt().putString(JSON, RulesConfig.fromJson(input).toJson());
    }

    public ItemStack builtin(String id) {
        try (InputStream stream = RuleDiskItem.class.getResourceAsStream("/default_rules/" + id + ".json")) {
            if (stream == null) throw new IllegalStateException("Missing built-in rules: " + id);
            ItemStack disk = new ItemStack(this);
            write(disk, new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            return disk;
        } catch (IOException | IllegalArgumentException invalid) {
            throw new IllegalStateException("Invalid built-in rules: " + id, invalid);
        }
    }

    @Override public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (player instanceof ServerPlayerEntity server && !TableSessions.isPlaying(server)) {
            Protocol.openDisk(server, hand == Hand.OFF_HAND ? 40 : player.getInventory().selectedSlot, json(stack));
        }
        return TypedActionResult.success(stack, world.isClient);
    }

    @Override public Text getName(ItemStack stack) {
        RulesConfig rules = rules(stack);
        return rules == null ? super.getName(stack) : Text.translatable("yharnam_dice.disk.named", rules.name);
    }

    @Override public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable(json(stack).isEmpty() ? "yharnam_dice.disk.empty" : "yharnam_dice.disk.insert"));
    }
}
