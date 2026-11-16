package com.yharnam.casino;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

final class CabinetKeyItem extends Item {
    private static final String LINK = "Cabinet";

    CabinetKeyItem() {
        super(new Settings().maxCount(1));
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        if (!(world.getBlockState(context.getBlockPos()).getBlock() instanceof SlotMachineBlock)) {
            return ActionResult.PASS;
        }
        if (world instanceof ServerWorld server && context.getPlayer() instanceof ServerPlayerEntity player) {
            SlotMachineEntity cabinet = SlotMachineBlock.findMachine(world,context.getBlockPos());
            if (cabinet != null) {
                if (!cabinet.bindKey(player,context.getStack()) && !CasinoStaffAccess.isStaff(player)) {
                    player.sendMessage(Text.translatable("message.yharnam_casino.vault_denied"),true);
                } else if (player.isSneaking()) {
                    cabinet.showPool(server,player);
                } else {
                    cabinet.openVault(server,player);
                }
            }
        }
        return ActionResult.success(world.isClient);
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity player, LivingEntity target, Hand hand) {
        if (!(target instanceof PlayerEntity colleague)) {
            return ActionResult.PASS;
        }
        if (player.getWorld() instanceof ServerWorld world && player instanceof ServerPlayerEntity founder) {
            SlotMachineEntity cabinet = linkedCabinet(world,player,stack);
            if (cabinet == null) {
                player.sendMessage(Text.translatable("message.yharnam_casino.key_nearby"),true);
            } else {
                cabinet.setSharedAccess(founder,colleague,!player.isSneaking());
            }
        }
        return ActionResult.success(player.getWorld().isClient);
    }

    private static @Nullable SlotMachineEntity linkedCabinet(ServerWorld world, PlayerEntity player, ItemStack key) {
        NbtCompound tag = key.getSubNbt(LINK);
        if (tag == null || !tag.containsUuid("Identity")
                || !world.getRegistryKey().getValue().toString().equals(tag.getString("World"))) {
            return null;
        }
        BlockPos pos = BlockPos.fromLong(tag.getLong("Pos"));
        if (player.squaredDistanceTo(Vec3d.ofCenter(pos)) > 100 || !world.isChunkLoaded(pos)) {
            return null;
        }
        if (world.getBlockEntity(pos) instanceof SlotMachineEntity cabinet && cabinet.matchesKey(tag.getUuid("Identity"))) {
            return cabinet;
        }
        return null;
    }

    static void bind(ItemStack key, SlotMachineEntity cabinet, World world) {
        NbtCompound link = new NbtCompound();
        link.putUuid("Identity",cabinet.machineId());
        link.putString("World",world.getRegistryKey().getValue().toString());
        link.putLong("Pos",cabinet.getPos().asLong());
        key.setSubNbt(LINK,link);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("item.yharnam_casino.cabinet_key.tooltip"));
        NbtCompound link = stack.getSubNbt(LINK);
        if (link != null && link.containsUuid("Identity")) {
            BlockPos pos = BlockPos.fromLong(link.getLong("Pos"));
            tooltip.add(Text.translatable("item.yharnam_casino.cabinet_key.link",pos.getX(),pos.getY(),pos.getZ()));
        }
    }
}
