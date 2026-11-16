package com.yharnam.casino;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

final class GameMasterKeyItem extends Item {
    GameMasterKeyItem() {
        super(new Settings().maxCount(1));
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        if (!(world.getBlockState(context.getBlockPos()).getBlock() instanceof SlotMachineBlock)) {
            return ActionResult.PASS;
        }
        if (world instanceof ServerWorld server && context.getPlayer() instanceof ServerPlayerEntity player) {
            SlotMachineEntity machine = SlotMachineBlock.findMachine(world,context.getBlockPos());
            if (!CasinoStaffAccess.isStaff(player)) {
                player.sendMessage(Text.translatable("message.yharnam_casino.gm_key_denied"),true);
            } else if (machine != null) {
                if (player.isSneaking()) {
                    machine.openDenominations(player);
                } else {
                    machine.openVault(server,player);
                }
            }
        }
        return ActionResult.success(world.isClient);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("item.yharnam_casino.gm_key.tooltip"));
    }
}
