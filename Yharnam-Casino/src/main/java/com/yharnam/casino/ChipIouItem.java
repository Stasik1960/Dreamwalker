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
import java.util.UUID;

final class ChipIouItem extends Item {
    private static final String RECEIPT = "YharnamCasinoIou";
    private static final String CLAIM = "Claim";
    private static final String MACHINE = "Machine";
    private static final String VISIBLE_AMOUNT = "Amount";

    ChipIouItem() {
        super(new Settings().maxCount(1));
    }

    static ItemStack create(UUID claimId, UUID machineId, long amount) {
        ItemStack stack = new ItemStack(YharnamCasino.CHIP_IOU);
        NbtCompound receipt = new NbtCompound();
        receipt.putUuid(CLAIM, claimId);
        receipt.putUuid(MACHINE, machineId);
        receipt.putLong(VISIBLE_AMOUNT, amount);
        stack.setSubNbt(RECEIPT, receipt);
        return stack;
    }

    static @Nullable UUID claimId(ItemStack stack) {
        NbtCompound receipt = stack.getSubNbt(RECEIPT);
        return receipt != null && receipt.containsUuid(CLAIM) ? receipt.getUuid(CLAIM) : null;
    }

    static void setVisibleAmount(ItemStack stack, long amount) {
        NbtCompound receipt = stack.getSubNbt(RECEIPT);
        if (receipt != null) {
            receipt.putLong(VISIBLE_AMOUNT, Math.max(0, amount));
        }
    }

    static void labelIssuer(ItemStack stack, SlotMachineEntity machine) {
        NbtCompound receipt = stack.getSubNbt(RECEIPT);
        if (receipt != null) {
            receipt.putString("IssuerName", machine.getCachedState().getBlock().getTranslationKey());
            receipt.putLong("IssuerPosition", machine.getPos().asLong());
        }
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        SlotMachineEntity machine = SlotMachineBlock.findMachine(world, context.getBlockPos());
        if (machine == null) {
            return ActionResult.PASS;
        }
        if (world instanceof ServerWorld server && context.getPlayer() instanceof ServerPlayerEntity player) {
            CasinoIouState.Redemption result = CasinoIouState.get(server.getServer())
                    .redeem(player, context.getStack(), machine);
            if (result.status() == CasinoIouState.RedemptionStatus.PAID
                    && context.getStack().isEmpty()
                    && player.getStackInHand(context.getHand()) == context.getStack()) {
                // Creative item use restores the count of the old stack after this method.
                player.setStackInHand(context.getHand(),ItemStack.EMPTY);
            }
            switch (result.status()) {
                case PAID -> player.sendMessage(Text.translatable(
                        "message.yharnam_casino.iou_paid", result.paid()), true);
                case PARTIAL -> player.sendMessage(Text.translatable(
                        "message.yharnam_casino.iou_partial", result.paid(), result.remaining()), true);
                case EMPTY_POOL -> player.sendMessage(Text.translatable(
                        "message.yharnam_casino.iou_empty_pool", result.remaining()), true);
                case WRONG_MACHINE -> player.sendMessage(Text.translatable(
                        "message.yharnam_casino.iou_wrong_machine"), true);
                case INVALID -> player.sendMessage(Text.translatable(
                        "message.yharnam_casino.iou_invalid"), true);
            }
        }
        return ActionResult.success(world.isClient);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip,
                              TooltipContext context) {
        NbtCompound receipt = stack.getSubNbt(RECEIPT);
        long amount = receipt == null ? 0 : Math.max(0, receipt.getLong(VISIBLE_AMOUNT));
        tooltip.add(Text.translatable("item.yharnam_casino.chip_iou.tooltip", amount));
        if (receipt != null && receipt.contains("IssuerName") && receipt.contains("IssuerPosition")) {
            BlockPos pos = BlockPos.fromLong(receipt.getLong("IssuerPosition"));
            tooltip.add(Text.translatable("item.yharnam_casino.chip_iou.issuer",
                    Text.translatable(receipt.getString("IssuerName")),pos.getX(),pos.getY(),pos.getZ()));
        }
        if (receipt != null) {
            tooltip.add(Text.translatable("item.yharnam_casino.chip_iou.use"));
        }
    }
}
