package ru.modelprops;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import ru.modelprops.net.ModelActionNetworking;

import java.util.List;

public final class ModelPropItem extends Item {
    public static final String MODEL_ID_KEY = "ModelPropsId";
    public static final String MODEL_NAME_KEY = "ModelPropsName";

    public ModelPropItem(Settings settings) {
        super(settings);
    }

    public static ItemStack createStack(String id, String displayName, int count) {
        ItemStack stack = new ItemStack(ModelProps.MODEL_PROP, count);
        stack.getOrCreateNbt().putString(MODEL_ID_KEY, id);
        stack.getOrCreateNbt().putString(MODEL_NAME_KEY, displayName);
        return stack;
    }

    public static String getModelId(ItemStack stack) {
        return stack.hasNbt() ? stack.getNbt().getString(MODEL_ID_KEY) : "";
    }

    @Override
    public Text getName(ItemStack stack) {
        if (stack.hasNbt() && stack.getNbt().contains(MODEL_NAME_KEY)) {
            String name = stack.getNbt().getString(MODEL_NAME_KEY);
            if (!name.isBlank()) {
                return Text.literal(name);
            }
        }
        return super.getName(stack);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient && user instanceof ServerPlayerEntity player) {
            ModelActionNetworking.playHeldAction(player, hand, "use");
        }
        return TypedActionResult.success(stack, world.isClient);
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        String id = getModelId(stack);
        if (!id.isBlank()) {
            tooltip.add(Text.translatable("tooltip.modelprops.id", id).formatted(Formatting.DARK_GRAY));
        }
        tooltip.add(Text.translatable("tooltip.modelprops.debug").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.modelprops.actions").formatted(Formatting.GRAY));
    }
}
