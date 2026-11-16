package com.yharnam.casino;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class SlotMachineEntity extends BlockEntity {
    private static final long BET_TIMEOUT = 20 * 45L;
    static final int SPIN_DURATION = 70;
    private static final int[] START = {11, 13, 15};
    private static final int[] STOP = {42, 54, 66};

    private UUID machineId = UUID.randomUUID();
    private UUID founder;
    private final Set<UUID> coOwners = new LinkedHashSet<>();
    private long legacyProfit;
    private boolean legacyActiveStake;
    private boolean changeNeedsSpaceCheck;
    private final SimpleInventory safe = new SimpleInventory(27) {
        @Override
        public boolean canPlayerUse(PlayerEntity player) {
            return canOpenVault(player) && world != null && player.getWorld() == world
                    && world.getBlockEntity(pos) == SlotMachineEntity.this
                    && player.squaredDistanceTo(Vec3d.ofCenter(pos)) <= 64;
        }

        @Override
        public void markDirty() {
            super.markDirty();
            changeNeedsSpaceCheck = true;
            SlotMachineEntity.this.markDirty();
        }
    };
    private final ChipPool pool = new ChipPool(safe);

    private UUID bettor;
    private ItemStack escrow = ItemStack.EMPTY;
    private int wager;
    private long armedUntil = -1;
    private long startTick = -1;
    private int[] displayed = {0, 1, 2};
    private int[] target = {0, 1, 2};

    public SlotMachineEntity(BlockPos pos, BlockState state) {
        super(YharnamCasino.SLOT_ENTITY, pos, state);
    }

    public SlotStyle style() {
        return ((SlotMachineBlock)getCachedState().getBlock()).style();
    }

    public long spinStarted() {
        return startTick;
    }

    public int initialIndex(int reel) {
        return displayed[reel];
    }

    public int resultIndex(int reel) {
        return startTick < 0 ? displayed[reel] : target[reel];
    }

    UUID machineId() {
        return machineId;
    }

    @Nullable UUID founderId() {
        return founder;
    }

    boolean matchesKey(UUID keyIdentity) {
        return machineId.equals(keyIdentity);
    }

    void setFounder(UUID owner) {
        if (founder == null) {
            founder = owner;
            markDirty();
        }
    }

    boolean hasAccess(UUID player) {
        return player.equals(founder) || coOwners.contains(player);
    }

    private boolean canOpenVault(PlayerEntity player) {
        return hasAccess(player.getUuid())
                || (player instanceof ServerPlayerEntity serverPlayer && CasinoStaffAccess.isStaff(serverPlayer));
    }

    boolean canDismantle(PlayerEntity player) {
        return founder == null || founder.equals(player.getUuid()) || player.hasPermissionLevel(2);
    }

    boolean bindKey(ServerPlayerEntity player, ItemStack key) {
        if (founder == null && world instanceof ServerWorld server) {
            importLegacyCash(server);
            setFounder(player.getUuid());
        }
        if (world == null || !hasAccess(player.getUuid())) {
            return false;
        }
        CabinetKeyItem.bind(key,this,world);
        return true;
    }

    void setSharedAccess(ServerPlayerEntity actor, PlayerEntity colleague, boolean grant) {
        if (!actor.getUuid().equals(founder)) {
            tell(actor,"vault_founder_only");
            return;
        }
        UUID target = colleague.getUuid();
        if (target.equals(founder)) {
            tell(actor,"vault_founder_protected");
            return;
        }
        boolean changed = grant ? coOwners.add(target) : coOwners.remove(target);
        if (!changed) {
            tell(actor,grant ? "vault_already_member" : "vault_not_member",colleague.getName());
            return;
        }
        markDirty();
        if (!grant && !canOpenVault(colleague) && colleague instanceof ServerPlayerEntity member
                && member.currentScreenHandler instanceof GenericContainerScreenHandler screen
                && screen.getInventory() == safe) {
            member.closeHandledScreen();
        }
        tell(actor,grant ? "vault_added" : "vault_removed",colleague.getName());
        tell(colleague,grant ? "vault_access_given" : "vault_access_taken",getCachedState().getBlock().getName());
    }

    void openVault(ServerWorld world, ServerPlayerEntity player) {
        if (!canOpenVault(player)) {
            tell(player,"vault_denied");
            return;
        }
        importLegacyCash(world);
        placeChangeInSafe();
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId,inventory,viewer) -> GenericContainerScreenHandler.createGeneric9x3(syncId,inventory,safe),
                Text.translatable("container.yharnam_casino.vault")));
        tell(player,"pool_balance",pool.balance());
    }

    void showPool(ServerWorld world, ServerPlayerEntity player) {
        if (!canOpenVault(player)) {
            tell(player,"vault_denied");
            return;
        }
        importLegacyCash(world);
        tell(player,"pool_balance",pool.balance());
    }

    void use(ServerWorld world, PlayerEntity player, ItemStack held, boolean lever) {
        importLegacyCash(world);
        expire(world);
        boolean canCancelBet = startTick < 0 && player.getUuid().equals(bettor);
        if (player instanceof ServerPlayerEntity serverPlayer && player.isSneaking()
                && player.getMainHandStack().isEmpty() && player.getOffHandStack().isEmpty()
                && !canCancelBet && CasinoStaffAccess.isStaff(serverPlayer)) {
            openVault(world,serverPlayer);
            return;
        }
        if (startTick >= 0) {
            tell(player, "spinning");
            return;
        }
        if (bettor != null) {
            if (!bettor.equals(player.getUuid())) {
                tell(player, "occupied");
            } else if (player.isSneaking() && held.isEmpty()) {
                refund(world);
            } else if (lever || held.isEmpty()) {
                if (spin(world)) {
                    tell(player, "started", wager);
                } else {
                    refund(world);
                    tell(player,"pool_full_refunded");
                }
            } else {
                tell(player, "armed", wager);
            }
            return;
        }
        if (!(held.getItem() instanceof ChipItem chip)) {
            tell(player, "use_chip");
            return;
        }
        if (!pool.canStore(held)) {
            tell(player,"pool_full");
            return;
        }
        escrow = held.split(1);
        bettor = player.getUuid();
        wager = chip.value();
        armedUntil = world.getTime() + BET_TIMEOUT;
        markDirty();
        world.playSound(null, pos, SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.BLOCKS, .55F, 1.35F);
        tell(player, "armed", wager);
    }

    private boolean spin(ServerWorld world) {
        if (escrow.isEmpty() || !pool.storeStake(escrow)) {
            return false;
        }
        escrow = ItemStack.EMPTY;
        target = SlotMath.draw(world.getRandom());
        startTick = world.getTime();
        armedUntil = -1;
        sync(world);
        world.playSound(null,pos,YharnamCasino.LEVER_PULL,SoundCategory.BLOCKS,.9F,1.0F);
        return true;
    }

    void serverTick(ServerWorld world) {
        importLegacyCash(world);
        if (changeNeedsSpaceCheck) {
            placeChangeInSafe();
        }
        expire(world);
        if (startTick < 0) {
            return;
        }
        long elapsed = world.getTime() - startTick;
        if (elapsed >= SPIN_DURATION) {
            settle(world);
        } else if (elapsed == STOP[0] || elapsed == STOP[1] || elapsed == STOP[2]) {
            world.playSound(null, pos, SoundEvents.BLOCK_CHAIN_HIT, SoundCategory.BLOCKS, .78F, 1.35F);
        } else if (elapsed > START[0] && elapsed < STOP[2] && elapsed % 5 == 0) {
            world.playSound(null, pos, SoundEvents.BLOCK_CHAIN_HIT, SoundCategory.BLOCKS, .24F, 1.6F);
        }
    }

    private void expire(ServerWorld world) {
        if (bettor != null && startTick < 0 && armedUntil >= 0 && world.getTime() >= armedUntil) {
            refund(world);
        }
    }

    void onRemoved(ServerWorld world) {
        importLegacyCash(world);
        if (bettor != null) {
            if (startTick >= 0) {
                settle(world);
            } else {
                refund(world);
            }
        }
        if (founder != null) {
            CasinoMailbox mailbox = CasinoMailbox.get(world.getServer());
            mailbox.recoverVault(founder,safe.clearToList());
            mailbox.credit(founder,pool.removeChange(),0);
            markDirty();
            ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(founder);
            if (owner != null) {
                mailbox.deliver(owner,false);
            }
        }
    }

    private void refund(ServerWorld world) {
        UUID recipient = bettor;
        int stake = wager;
        ItemStack returned = escrow.isEmpty() ? chipForWager() : escrow;
        clearBet();
        markDirty();
        CasinoMailbox mailbox = CasinoMailbox.get(world.getServer());
        mailbox.recoverVault(recipient,List.of(returned));
        notifyBettor(world,mailbox,recipient,"refunded",stake);
    }

    private void settle(ServerWorld world) {
        UUID recipient = bettor;
        int multiplier = SlotMath.multiplier(target[0], target[1], target[2]);
        int amount = wager * multiplier;
        boolean ticket = SlotMath.winsTicket(target[0], target[1], target[2]);
        displayed = target.clone();
        clearBet();
        sync(world);
        if (recipient != null) {
            CasinoMailbox mailbox = CasinoMailbox.get(world.getServer());
            long paid = payFromPool(amount);
            long outstanding = amount-paid;
            mailbox.credit(recipient,paid,ticket ? 1 : 0);
            if (outstanding > 0) {
                ItemStack note = ChipIouItem.create(this,world,outstanding);
                mailbox.recoverVault(recipient,List.of(note));
                notifyBettor(world,mailbox,recipient,ticket ? "jackpot_iou" : "win_iou",paid,outstanding);
            } else {
                String message = ticket ? "jackpot" : multiplier > 1 ? "win" : multiplier == 1 ? "push" : "loss";
                notifyBettor(world,mailbox,recipient,message,paid);
            }
        }
        if (ticket) {
            world.playSound(null,pos,YharnamCasino.JACKPOT,SoundCategory.BLOCKS,1.0F,1.0F);
        } else if (multiplier > 1) {
            world.playSound(null,pos,SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,SoundCategory.BLOCKS,.75F,.95F);
        }
    }

    private void clearBet() {
        bettor = null;
        escrow = ItemStack.EMPTY;
        wager = 0;
        armedUntil = -1;
        startTick = -1;
    }

    long payFromPool(long amount) {
        if (!(world instanceof ServerWorld server) || amount <= 0) {
            return 0;
        }
        importLegacyCash(server);
        long paid = pool.pay(amount);
        if (paid > 0) {
            placeChangeInSafe();
            markDirty();
        }
        return paid;
    }

    private void placeChangeInSafe() {
        pool.fillSlotsWithChange();
        changeNeedsSpaceCheck = false;
    }

    private ItemStack chipForWager() {
        ChipItem chip = YharnamCasino.chip(wager);
        return chip == null ? ItemStack.EMPTY : new ItemStack(chip);
    }

    private void importLegacyCash(ServerWorld world) {
        if (legacyProfit > 0) {
            long available = founder == null ? legacyProfit
                    : CasinoMailbox.get(world.getServer()).withdrawProfit(founder,legacyProfit);
            pool.addChange(available);
            // A remaining old surplus can still be claimed under the old accounting.
            if (founder != null && legacyProfit > available) {
                CasinoMailbox.get(world.getServer()).archiveProfit(founder,legacyProfit-available);
            }
            legacyProfit = 0;
            changeNeedsSpaceCheck = true;
            markDirty();
        }
        if (legacyActiveStake) {
            ItemStack stake = chipForWager();
            if (!stake.isEmpty() && !pool.storeStake(stake)) {
                pool.addChange(wager);
                changeNeedsSpaceCheck = true;
            }
            legacyActiveStake = false;
            markDirty();
        }
    }

    private static void notifyBettor(ServerWorld world, CasinoMailbox mailbox, UUID recipient,
                                     String message, Object... values) {
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(recipient);
        if (player != null) {
            tell(player,message,values);
            mailbox.deliver(player, false);
        }
    }

    private static void tell(PlayerEntity player, String key, Object... values) {
        player.sendMessage(Text.translatable("message.yharnam_casino." + key, values), true);
    }

    private void sync(ServerWorld world) {
        markDirty();
        BlockState state = getCachedState();
        if (world.getBlockState(pos).isOf(state.getBlock())) {
            world.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);
        }
    }

    public float reelAngle(int reel, float elapsedTicks) {
        float base = (reel - displayed[reel]) * 45.0F;
        if (startTick < 0) {
            return base;
        }
        float time = MathHelper.clamp((elapsedTicks - START[reel]) / (STOP[reel] - START[reel]), 0, 1);
        float progress;
        if (time < .17F) {
            progress = time * time / (2 * .17F * .775F);
        } else if (time < .72F) {
            progress = (time - .085F) / .775F;
        } else {
            progress = 1 - (1 - time) * (1 - time) / (2 * .28F * .775F);
        }
        int advance = Math.floorMod(target[reel] - displayed[reel], SlotMath.SYMBOLS);
        return base - ((reel + 2) * 360 + advance * 45) * progress;
    }

    public float leverAngle(float elapsedTicks) {
        if (startTick < 0 || elapsedTicks < 0 || elapsedTicks >= 24) {
            return 0;
        }
        if (elapsedTicks < 10) {
            return 57 * smoothstep(elapsedTicks / 10);
        }
        if (elapsedTicks < 14) {
            return 57;
        }
        return 57 * (1 - smoothstep((elapsedTicks - 14) / 10));
    }

    private static float smoothstep(float t) {
        return t * t * (3 - 2 * t);
    }

    private void writeVisual(NbtCompound nbt) {
        nbt.putIntArray("Displayed", displayed);
        nbt.putIntArray("Target", target);
        nbt.putLong("StartTick", startTick);
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        writeVisual(nbt);
        nbt.putUuid("MachineId",machineId);
        if (founder != null) {
            nbt.putUuid("Founder",founder);
        }
        nbt.putInt("PoolVersion",1);
        nbt.putLong("PoolChange",pool.savedChange());
        if (legacyProfit > 0) {
            nbt.putLong("LegacyProfit",legacyProfit);
        }
        if (legacyActiveStake) {
            nbt.putBoolean("LegacyActiveStake",true);
        }
        NbtList allowed = new NbtList();
        coOwners.forEach(uuid -> allowed.add(NbtHelper.fromUuid(uuid)));
        nbt.put("CoOwners",allowed);
        DefaultedList<ItemStack> slots = DefaultedList.ofSize(safe.size(),ItemStack.EMPTY);
        for (int slot = 0; slot < safe.size(); slot++) {
            slots.set(slot,safe.getStack(slot));
        }
        nbt.put("Safe",Inventories.writeNbt(new NbtCompound(),slots));
        if (bettor != null) {
            nbt.putUuid("Bettor", bettor);
            nbt.putInt("Wager", wager);
            nbt.putLong("ArmedUntil", armedUntil);
            if (!escrow.isEmpty()) {
                nbt.put("Escrow",escrow.writeNbt(new NbtCompound()));
            }
        }
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        displayed = readIndices(nbt, "Displayed", new int[]{0, 1, 2});
        target = readIndices(nbt, "Target", displayed.clone());
        startTick = nbt.contains("StartTick") ? nbt.getLong("StartTick") : -1;
        bettor = nbt.containsUuid("Bettor") ? nbt.getUuid("Bettor") : null;
        wager = nbt.getInt("Wager");
        armedUntil = nbt.contains("ArmedUntil") ? nbt.getLong("ArmedUntil") : -1;
        escrow = nbt.contains("Escrow") ? ItemStack.fromNbt(nbt.getCompound("Escrow")) : ItemStack.EMPTY;
        if (bettor != null && startTick < 0 && escrow.isEmpty()) {
            escrow = chipForWager();
        }
        if (nbt.containsUuid("MachineId")) {
            machineId = nbt.getUuid("MachineId");
            founder = nbt.containsUuid("Founder") ? nbt.getUuid("Founder") : null;
            boolean hasPool = nbt.getInt("PoolVersion") > 0;
            legacyProfit = Math.max(0,nbt.getLong(hasPool ? "LegacyProfit" : "Profit"));
            legacyActiveStake = hasPool ? nbt.getBoolean("LegacyActiveStake") : bettor != null && startTick >= 0;
            pool.readChange(nbt.getLong("PoolChange"));
            changeNeedsSpaceCheck = pool.savedChange() > 0;
            coOwners.clear();
            for (NbtElement member : nbt.getList("CoOwners",NbtElement.INT_ARRAY_TYPE)) {
                if (((net.minecraft.nbt.NbtIntArray)member).size() == 4) {
                    coOwners.add(NbtHelper.toUuid(member));
                }
            }
            DefaultedList<ItemStack> slots = DefaultedList.ofSize(safe.size(),ItemStack.EMPTY);
            Inventories.readNbt(nbt.getCompound("Safe"),slots);
            for (int slot = 0; slot < safe.size(); slot++) {
                safe.setStack(slot,slots.get(slot));
            }
        }
    }

    private static int[] readIndices(NbtCompound tag, String key, int[] fallback) {
        int[] values = tag.getIntArray(key);
        return values.length == 3 && Arrays.stream(values).allMatch(index -> index >= 0 && index < SlotMath.SYMBOLS)
                ? values : fallback;
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        NbtCompound data = new NbtCompound();
        writeVisual(data);
        return data;
    }

    @Override
    public @Nullable BlockEntityUpdateS2CPacket toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }
}
