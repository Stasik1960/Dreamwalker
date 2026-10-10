package dev.dreamwalker.bloodbornedw.architecture;

import dev.dreamwalker.bloodbornedw.composite.CompositeArchitecture;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Deserialize-only alias for the retired 90008 builder tool.
 *
 * <p>The registry id must survive so old inventories, containers and dropped
 * stacks can be read.  The first normal tick or use rewrites the stack to
 * 90009 without touching any world chunks or discarding its NBT.</p>
 */
final class LegacyBuildingTool extends Item {
    LegacyBuildingTool() { super(new Settings().maxCount(1)); }

    static ItemStack canonical(ItemStack old) {
        ItemStack upgraded = new ItemStack(CompositeArchitecture.BUILDER, old.getCount());
        if (old.hasNbt()) upgraded.setNbt(old.getNbt().copy());
        return upgraded;
    }

    private static ItemStack upgrade(PlayerEntity player, Hand hand, ItemStack old) {
        ItemStack upgraded = canonical(old);
        player.setStackInHand(hand, upgraded);
        player.getInventory().markDirty();
        return upgraded;
    }

    @Override public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        upgrade(player, hand, player.getStackInHand(hand));
        return CompositeArchitecture.BUILDER.use(world, player, hand);
    }

    @Override public ActionResult useOnBlock(ItemUsageContext context) {
        PlayerEntity player = context.getPlayer();
        if (player == null) return ActionResult.FAIL;
        upgrade(player, context.getHand(), context.getStack());
        return CompositeArchitecture.BUILDER.useOnBlock(context);
    }

    @Override public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (entity instanceof PlayerEntity player && !world.isClient) {
            // PlayerInventory uses the same 0..40 slots for main, armour and
            // offhand stacks; a copied stack retains BuilderAction and options.
            player.getInventory().setStack(slot, canonical(stack));
            player.getInventory().markDirty();
        }
    }

}
