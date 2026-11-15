package com.yharnam.casino;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative bearer debts created by slot machines. */
final class CasinoIouState extends PersistentState {
    private static final String SAVE_KEY = "yharnam_casino_ious";

    private static final class Claim {
        private final UUID machineId;
        private @Nullable UUID founder;
        private long remaining;
        private boolean issuerActive;

        private Claim(UUID machineId, @Nullable UUID founder, long remaining, boolean issuerActive) {
            this.machineId = machineId;
            this.founder = founder;
            this.remaining = remaining;
            this.issuerActive = issuerActive;
        }
    }

    enum RedemptionStatus {
        PAID,
        PARTIAL,
        EMPTY_POOL,
        WRONG_MACHINE,
        INVALID
    }

    record Redemption(RedemptionStatus status, long paid, long remaining) {}

    private final Map<UUID, Claim> claims = new HashMap<>();

    static CasinoIouState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(
                CasinoIouState::read, CasinoIouState::new, SAVE_KEY);
    }

    ItemStack issue(UUID machineId, @Nullable UUID founder, long amount) {
        if (amount <= 0) {
            return ItemStack.EMPTY;
        }
        UUID claimId;
        do {
            claimId = UUID.randomUUID();
        } while (claims.containsKey(claimId));

        claims.put(claimId, new Claim(machineId, founder, amount, true));
        markDirty();
        return ChipIouItem.create(claimId, machineId, amount);
    }

    /** Adds ownership to debts written while the same, still-live cabinet was unowned. */
    void reassignIssuer(UUID machineId, UUID founder) {
        boolean changed = false;
        for (Claim claim : claims.values()) {
            if (claim.issuerActive && claim.machineId.equals(machineId) && claim.founder == null) {
                claim.founder = founder;
                changed = true;
            }
        }
        if (changed) {
            markDirty();
        }
    }

    /** Marks an issuer as dismantled. This must only be called for an actual block removal. */
    void retireIssuer(UUID machineId, @Nullable UUID founder) {
        boolean changed = false;
        for (Claim claim : claims.values()) {
            if (!claim.machineId.equals(machineId)) {
                continue;
            }
            if (claim.founder == null && founder != null) {
                claim.founder = founder;
                changed = true;
            }
            if (claim.issuerActive) {
                claim.issuerActive = false;
                changed = true;
            }
        }
        if (changed) {
            markDirty();
        }
    }

    Redemption redeem(ServerPlayerEntity bearer, ItemStack note, SlotMachineEntity machine) {
        UUID claimId = ChipIouItem.claimId(note);
        if (claimId == null) {
            return new Redemption(RedemptionStatus.INVALID, 0, 0);
        }
        Claim claim = claims.get(claimId);
        if (claim == null || claim.remaining <= 0) {
            return new Redemption(RedemptionStatus.INVALID, 0, 0);
        }

        boolean original = claim.machineId.equals(machine.machineId());
        boolean eligibleReplacement = !claim.issuerActive && claim.founder != null
                && claim.founder.equals(machine.founderId());
        if (!original && !eligibleReplacement) {
            ChipIouItem.setVisibleAmount(note, claim.remaining);
            return new Redemption(RedemptionStatus.WRONG_MACHINE, 0, claim.remaining);
        }

        long paid = machine.payFromPool(claim.remaining);
        if (paid < 0 || paid > claim.remaining) {
            throw new IllegalStateException("Slot machine returned an invalid IOU payment: " + paid);
        }
        if (paid == 0) {
            ChipIouItem.setVisibleAmount(note, claim.remaining);
            return new Redemption(RedemptionStatus.EMPTY_POOL, 0, claim.remaining);
        }

        claim.remaining -= paid;
        long remaining = claim.remaining;
        if (remaining == 0) {
            claims.remove(claimId);
            note.decrement(1);
        } else {
            ChipIouItem.setVisibleAmount(note, remaining);
        }
        markDirty();

        CasinoMailbox mailbox = CasinoMailbox.get(bearer.getServer());
        mailbox.credit(bearer.getUuid(), paid, 0);
        mailbox.deliver(bearer, false);
        return new Redemption(remaining == 0 ? RedemptionStatus.PAID : RedemptionStatus.PARTIAL,
                paid, remaining);
    }

    private static CasinoIouState read(NbtCompound tag) {
        CasinoIouState state = new CasinoIouState();
        NbtList entries = tag.getList("Claims", NbtElement.COMPOUND_TYPE);
        for (NbtElement element : entries) {
            NbtCompound entry = (NbtCompound)element;
            if (!entry.containsUuid("Claim") || !entry.containsUuid("Machine")) {
                continue;
            }
            long remaining = entry.getLong("Remaining");
            if (remaining <= 0) {
                continue;
            }
            UUID claimId = entry.getUuid("Claim");
            UUID machineId = entry.getUuid("Machine");
            UUID founder = entry.containsUuid("Founder") ? entry.getUuid("Founder") : null;
            boolean active = !entry.contains("IssuerActive", NbtElement.BYTE_TYPE)
                    || entry.getBoolean("IssuerActive");
            state.claims.putIfAbsent(claimId, new Claim(machineId, founder, remaining, active));
        }
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound tag) {
        NbtList entries = new NbtList();
        claims.forEach((claimId, claim) -> {
            if (claim.remaining <= 0) {
                return;
            }
            NbtCompound entry = new NbtCompound();
            entry.putUuid("Claim", claimId);
            entry.putUuid("Machine", claim.machineId);
            if (claim.founder != null) {
                entry.putUuid("Founder", claim.founder);
            }
            entry.putLong("Remaining", claim.remaining);
            entry.putBoolean("IssuerActive", claim.issuerActive);
            entries.add(entry);
        });
        tag.put("Claims", entries);
        return tag;
    }
}
