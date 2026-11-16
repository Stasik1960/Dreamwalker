package com.yharnam.casino;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

final class ChipIouItem extends Item {
    private static final String RECEIPT = "YharnamCasinoIou";

    ChipIouItem() {
        super(new Settings().maxCount(1));
    }

    static ItemStack create(SlotMachineEntity machine, ServerWorld world, long amount) {
        ItemStack stack = new ItemStack(YharnamCasino.CHIP_IOU);
        NbtCompound receipt = new NbtCompound();
        receipt.putLong("Amount", amount);
        receipt.putString("IssuerName", machine.getCachedState().getBlock().getTranslationKey());
        receipt.putLong("IssuerPosition", machine.getPos().asLong());
        if (machine.founderId() != null) {
            var server = world.getServer();
            var owner = server.getPlayerManager().getPlayer(machine.founderId());
            if (owner != null) {
                receipt.putString("OwnerName", owner.getGameProfile().getName());
            } else if (server.getUserCache() != null) {
                server.getUserCache().getByUuid(machine.founderId())
                        .ifPresent(profile -> receipt.putString("OwnerName", profile.getName()));
            }
        }
        stack.setSubNbt(RECEIPT, receipt);
        return stack;
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        if (!(world.getBlockState(context.getBlockPos()).getBlock() instanceof SlotMachineBlock)) {
            return ActionResult.PASS;
        }
        if (!world.isClient && context.getPlayer() instanceof ServerPlayerEntity player) {
            player.sendMessage(Text.translatable("message.yharnam_casino.iou_show_owner"), true);
        }
        return ActionResult.success(world.isClient);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip,
                              TooltipContext context) {
        NbtCompound receipt = stack.getSubNbt(RECEIPT);
        long amount = receipt == null ? 0 : Math.max(0, receipt.getLong("Amount"));
        tooltip.add(Text.translatable("item.yharnam_casino.chip_iou.tooltip", amount));
        if (receipt != null && receipt.contains("IssuerName") && receipt.contains("IssuerPosition")) {
            BlockPos pos = BlockPos.fromLong(receipt.getLong("IssuerPosition"));
            tooltip.add(Text.translatable("item.yharnam_casino.chip_iou.issuer",
                    Text.translatable(receipt.getString("IssuerName")),pos.getX(),pos.getY(),pos.getZ()));
        }
        if (receipt != null && receipt.contains("OwnerName")) {
            tooltip.add(Text.translatable("item.yharnam_casino.chip_iou.owner",receipt.getString("OwnerName")));
        }
        tooltip.add(Text.translatable("item.yharnam_casino.chip_iou.use"));
    }
}
