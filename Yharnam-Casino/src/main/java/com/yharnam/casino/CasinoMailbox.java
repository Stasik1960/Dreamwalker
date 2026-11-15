package com.yharnam.casino;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class CasinoMailbox extends PersistentState {
    private record Reward(long chips, long tickets) {}

    private final Map<UUID, Reward> rewards = new HashMap<>();
    private final Map<UUID, Long> profits = new HashMap<>();
    private final Map<UUID, Long> archivedProfits = new HashMap<>();
    private final Map<UUID, List<ItemStack>> recoveredVaults = new HashMap<>();

    static CasinoMailbox get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(
                CasinoMailbox::read, CasinoMailbox::new, "yharnam_casino_rewards");
    }

    void credit(UUID recipient, long chips, long tickets) {
        if (chips == 0 && tickets == 0) {
            return;
        }
        rewards.merge(recipient, new Reward(chips, tickets),
                (old, added) -> new Reward(old.chips + added.chips, old.tickets + added.tickets));
        markDirty();
    }

    void recordProfit(UUID founder, long delta) {
        if (founder == null || delta == 0) {
            return;
        }
        profits.compute(founder, (ignored, balance) -> saturatedAdd(balance == null ? 0 : balance, delta));
        markDirty();
    }

    long availableProfit(UUID founder, long cabinetNet) {
        if (founder == null || cabinetNet <= 0) {
            return 0;
        }
        return Math.min(cabinetNet, Math.max(0, profits.getOrDefault(founder, 0L)));
    }

    long withdrawProfit(UUID founder, long cabinetNet) {
        long amount = availableProfit(founder, cabinetNet);
        if (amount == 0) {
            return 0;
        }
        setOrRemove(profits, founder, profits.getOrDefault(founder, 0L) - amount);
        markDirty();
        return amount;
    }

    void archiveProfit(UUID founder, long cabinetNet) {
        if (founder == null || cabinetNet <= 0) {
            return;
        }
        archivedProfits.compute(founder,
                (ignored, archived) -> saturatedAdd(archived == null ? 0 : archived, cabinetNet));
        markDirty();
    }

    void claimArchivedProfit(UUID founder) {
        if (founder == null) {
            return;
        }
        long archived = Math.max(0, archivedProfits.getOrDefault(founder, 0L));
        long pooled = Math.max(0, profits.getOrDefault(founder, 0L));
        long amount = Math.min(archived, pooled);
        if (amount == 0) {
            return;
        }
        setOrRemove(archivedProfits, founder, archived - amount);
        setOrRemove(profits, founder, pooled - amount);
        credit(founder, amount, 0);
    }

    void recoverVault(UUID founder, List<ItemStack> contents) {
        if (founder == null || contents == null || contents.isEmpty()) {
            return;
        }
        List<ItemStack> recovered = recoveredVaults.computeIfAbsent(founder, ignored -> new ArrayList<>());
        boolean changed = false;
        for (ItemStack stack : contents) {
            if (stack != null && !stack.isEmpty() && stack.getCount() > 0) {
                recovered.add(stack.copy());
                changed = true;
            }
        }
        if (changed) {
            markDirty();
        }
    }

    boolean deliver(ServerPlayerEntity player, boolean explain) {
        UUID recipient = player.getUuid();
        Reward reward = rewards.get(recipient);
        List<ItemStack> recovered = recoveredVaults.get(recipient);
        if (reward == null && (recovered == null || recovered.isEmpty())) {
            if (explain) {
                player.sendMessage(Text.translatable("message.yharnam_casino.no_claim"), false);
            }
            return false;
        }

        PlayerInventory inventory = player.getInventory();
        long owedTickets = reward == null ? 0 : reward.tickets;
        long owedChips = reward == null ? 0 : reward.chips;
        long tickets = transfer(inventory, YharnamCasino.PRIZE_TICKET, owedTickets, true);
        long cash = owedChips;
        int[] denominations = YharnamCasino.CHIP_VALUES;
        // Fill existing stacks first. A full inventory may still have room for change.
        for (boolean fillEmpty : new boolean[]{false, true}) {
            for (int index = denominations.length - 1; index >= 0; index--) {
                int value = denominations[index];
                long available = cash / value;
                long remainder = transfer(inventory, YharnamCasino.chip(value), available, fillEmpty);
                cash -= (available - remainder) * value;
            }
        }

        long deliveredChips = owedChips - cash;
        long deliveredTickets = owedTickets - tickets;
        boolean deliveredReward = deliveredChips != 0 || deliveredTickets != 0;
        if (deliveredReward) {
            if (cash == 0 && tickets == 0) {
                rewards.remove(recipient);
            } else {
                rewards.put(recipient, new Reward(cash, tickets));
            }
        }

        long deliveredVaultItems = 0;
        List<ItemStack> vaultRemainder = new ArrayList<>();
        if (recovered != null) {
            for (ItemStack stored : recovered) {
                ItemStack remainder = transferRecovered(inventory, stored);
                deliveredVaultItems += stored.getCount() - remainder.getCount();
                if (!remainder.isEmpty()) {
                    vaultRemainder.add(remainder);
                }
            }
            if (vaultRemainder.isEmpty()) {
                recoveredVaults.remove(recipient);
            } else {
                recoveredVaults.put(recipient, vaultRemainder);
            }
        }

        boolean delivered = deliveredReward || deliveredVaultItems != 0;
        if (delivered) {
            inventory.markDirty();
            markDirty();
        }
        if (deliveredReward && explain) {
            player.sendMessage(Text.translatable("message.yharnam_casino.claimed", deliveredChips, deliveredTickets), false);
        }
        if (deliveredVaultItems != 0) {
            player.sendMessage(Text.translatable("message.yharnam_casino.vault_recovered", deliveredVaultItems), false);
        }
        if (cash != 0 || tickets != 0) {
            player.sendMessage(Text.translatable("message.yharnam_casino.owed", cash, tickets), false);
        }
        if (!vaultRemainder.isEmpty()) {
            long remainingItems = vaultRemainder.stream().mapToLong(ItemStack::getCount).sum();
            player.sendMessage(Text.translatable("message.yharnam_casino.vault_owed",
                    remainingItems, vaultRemainder.size()), false);
        }
        return delivered;
    }

    private static ItemStack transferRecovered(PlayerInventory inventory, ItemStack stored) {
        ItemStack remainder = stored.copy();
        remainder.setCount(fillRecoveredExisting(remainder, inventory.offHand.get(0), remainder.getCount()));
        for (ItemStack target : inventory.main) {
            if (remainder.isEmpty()) {
                break;
            }
            remainder.setCount(fillRecoveredExisting(remainder, target, remainder.getCount()));
        }
        for (int slot = 0; slot < inventory.main.size() && !remainder.isEmpty(); slot++) {
            if (inventory.getStack(slot).isEmpty()) {
                int quantity = Math.min(remainder.getCount(), remainder.getMaxCount());
                ItemStack placed = remainder.copy();
                placed.setCount(quantity);
                inventory.setStack(slot, placed);
                remainder.decrement(quantity);
            }
        }
        return remainder;
    }

    private static int fillRecoveredExisting(ItemStack source, ItemStack target, int count) {
        if (count == 0 || target.isEmpty() || !ItemStack.canCombine(source, target)) {
            return count;
        }
        int quantity = Math.min(count, Math.max(0, target.getMaxCount() - target.getCount()));
        target.increment(quantity);
        return count - quantity;
    }

    private static long transfer(PlayerInventory inventory, Item item, long count, boolean fillEmpty) {
        ItemStack plain = new ItemStack(item);
        // Include the offhand for existing stacks. New stacks go to inventory slots only.
        ItemStack offhand = inventory.offHand.get(0);
        count = fillExisting(plain, offhand, count);
        for (ItemStack stack : inventory.main) {
            if (count == 0) {
                break;
            }
            count = fillExisting(plain, stack, count);
        }
        if (fillEmpty) {
            for (int slot = 0; slot < inventory.main.size() && count > 0; slot++) {
                if (inventory.getStack(slot).isEmpty()) {
                    int quantity = (int)Math.min(count, item.getMaxCount());
                    inventory.setStack(slot, new ItemStack(item, quantity));
                    count -= quantity;
                }
            }
        }
        return count;
    }

    private static long fillExisting(ItemStack plain, ItemStack stack, long count) {
        if (count == 0 || stack.isEmpty() || !ItemStack.canCombine(plain, stack)) {
            return count;
        }
        int quantity = (int)Math.min(count, Math.max(0, stack.getMaxCount() - stack.getCount()));
        stack.increment(quantity);
        return count - quantity;
    }

    private static CasinoMailbox read(NbtCompound tag) {
        CasinoMailbox mailbox = new CasinoMailbox();
        NbtList list = tag.getList("Rewards", NbtElement.COMPOUND_TYPE);
        for (NbtElement element : list) {
            NbtCompound entry = (NbtCompound)element;
            if (entry.containsUuid("Player")) {
                long chips = Math.max(0, entry.getLong("Chips"));
                long tickets = Math.max(0, entry.getLong("Tickets"));
                if (chips > 0 || tickets > 0) {
                    mailbox.rewards.put(entry.getUuid("Player"), new Reward(chips, tickets));
                }
            }
        }
        readLongMap(tag, "Profits", "Balance", mailbox.profits, true);
        readLongMap(tag, "ArchivedProfits", "Amount", mailbox.archivedProfits, false);
        NbtList vaults = tag.getList("RecoveredVaults", NbtElement.COMPOUND_TYPE);
        for (NbtElement element : vaults) {
            NbtCompound entry = (NbtCompound)element;
            if (!entry.containsUuid("Player")) {
                continue;
            }
            List<ItemStack> contents = new ArrayList<>();
            NbtList items = entry.getList("Items", NbtElement.COMPOUND_TYPE);
            for (NbtElement itemElement : items) {
                ItemStack stack = ItemStack.fromNbt((NbtCompound)itemElement);
                if (!stack.isEmpty() && stack.getCount() > 0) {
                    contents.add(stack);
                }
            }
            if (!contents.isEmpty()) {
                mailbox.recoveredVaults.put(entry.getUuid("Player"), contents);
            }
        }
        return mailbox;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound tag) {
        NbtList list = new NbtList();
        rewards.forEach((uuid, reward) -> {
            NbtCompound entry = new NbtCompound();
            entry.putUuid("Player", uuid);
            entry.putLong("Chips", reward.chips);
            entry.putLong("Tickets", reward.tickets);
            list.add(entry);
        });
        tag.put("Rewards", list);
        writeLongMap(tag, "Profits", "Balance", profits);
        writeLongMap(tag, "ArchivedProfits", "Amount", archivedProfits);
        NbtList vaults = new NbtList();
        recoveredVaults.forEach((uuid, contents) -> {
            if (contents.isEmpty()) {
                return;
            }
            NbtCompound entry = new NbtCompound();
            entry.putUuid("Player", uuid);
            NbtList items = new NbtList();
            for (ItemStack stack : contents) {
                if (!stack.isEmpty() && stack.getCount() > 0) {
                    items.add(stack.writeNbt(new NbtCompound()));
                }
            }
            if (!items.isEmpty()) {
                entry.put("Items", items);
                vaults.add(entry);
            }
        });
        tag.put("RecoveredVaults", vaults);
        return tag;
    }

    private static long saturatedAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        if (right < 0 && left < Long.MIN_VALUE - right) {
            return Long.MIN_VALUE;
        }
        return left + right;
    }

    private static void setOrRemove(Map<UUID, Long> map, UUID uuid, long amount) {
        if (amount == 0) {
            map.remove(uuid);
        } else {
            map.put(uuid, amount);
        }
    }

    private static void readLongMap(NbtCompound tag, String listKey, String valueKey,
                                    Map<UUID, Long> destination, boolean signed) {
        NbtList list = tag.getList(listKey, NbtElement.COMPOUND_TYPE);
        for (NbtElement element : list) {
            NbtCompound entry = (NbtCompound)element;
            if (!entry.containsUuid("Player")) {
                continue;
            }
            long value = entry.getLong(valueKey);
            if ((!signed && value <= 0) || value == 0) {
                continue;
            }
            destination.put(entry.getUuid("Player"), value);
        }
    }

    private static void writeLongMap(NbtCompound tag, String listKey, String valueKey,
                                     Map<UUID, Long> source) {
        NbtList list = new NbtList();
        source.forEach((uuid, value) -> {
            if (value == 0) {
                return;
            }
            NbtCompound entry = new NbtCompound();
            entry.putUuid("Player", uuid);
            entry.putLong(valueKey, value);
            list.add(entry);
        });
        tag.put(listKey, list);
    }
}
