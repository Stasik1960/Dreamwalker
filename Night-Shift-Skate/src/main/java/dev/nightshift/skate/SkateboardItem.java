package dev.nightshift.skate;

import java.util.List;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

final class SkateboardItem extends Item {
    private final BoardStyle style;

    SkateboardItem(Settings settings, BoardStyle style) {
        super(settings);
        this.style = style;
    }

    BoardStyle style() {
        return style;
    }

    @Override
    public Text getName(ItemStack stack) {
        BoardStyle preset = BoardSetup.fromStack(stack).matchingPreset();
        return preset == null ? Text.translatable("item.night_shift_skate.skateboard.custom")
                : Text.translatable("item.night_shift_skate." + preset.itemId());
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        PlayerEntity player = context.getPlayer();
        if (context.getSide() != Direction.UP || player == null || player.hasVehicle()) {
            return ActionResult.PASS;
        }

        BlockPos pos = context.getBlockPos();
        var state = world.getBlockState(pos);
        VoxelShape surface = state.getCollisionShape(world, pos);
        if (surface.isEmpty()) {
            return ActionResult.FAIL;
        }

        SkateboardEntity board = NightShiftSkate.SKATEBOARD_ENTITY.create(world);
        if (board == null) {
            return ActionResult.FAIL;
        }
        double x = pos.getX() + 0.5;
        double z = pos.getZ() + 0.5;
        double height = pos.getY() + surface.getMax(Direction.Axis.Y);
        if (state.getBlock() instanceof SkateRampBlock ramp) {
            Direction uphill = state.get(SkateRampBlock.FACING);
            double halfWidth = board.getWidth() * 0.5;
            height = ramp.heightAt(state, pos, x + uphill.getOffsetX() * halfWidth, z + uphill.getOffsetZ() * halfWidth);
        }
        board.refreshPositionAndAngles(x, height, z, player.getYaw(), 0);
        if (board.getBoundingBox().intersects(player.getBoundingBox())
                || !world.isSpaceEmpty(board, board.getBoundingBox()) || !world.canPlayerModifyAt(player, pos)) {
            return ActionResult.FAIL;
        }
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }

        board.setBoardStack(context.getStack());
        if (!world.spawnEntity(board)) {
            return ActionResult.FAIL;
        }
        if (!player.getAbilities().creativeMode) {
            context.getStack().decrement(1);
        }
        world.playSound(null, pos, SoundEvents.BLOCK_WOOD_PLACE, SoundCategory.PLAYERS, 0.6f, 1.3f);
        return ActionResult.CONSUME;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        BoardSetup setup = BoardSetup.fromStack(stack);
        for (SkatePart.Type type : SkatePart.Type.values()) {
            tooltip.add(Text.translatable(setup.part(type).translationKey()).formatted(Formatting.DARK_GRAY));
        }
        tooltip.add(Text.translatable("item.night_shift_skate.skateboard.controls").formatted(Formatting.GRAY));
    }
}
