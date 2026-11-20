package com.yharnam.casino;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** One physical chip and one reserved capsule per sale. Only counts, animation and colour leave the server. */
public final class GachaponEntity extends BlockEntity {
    public enum Phase { IDLE, PAID, TURNING, TRAY }
    public static final int TURN_TICKS = 32, HOLD_TICKS = 600, CAPACITY = 256;
    private static final GachaRarity[] COLORS = GachaRarity.values();
    private final EnumMap<GachaRarity,List<ItemStack>> stock = new EnumMap<>(GachaRarity.class);
    private final int[] counts = new int[COLORS.length];
    private final List<ItemStack> cashbox = new ArrayList<>();
    private UUID identity = UUID.randomUUID(), founder, customer;
    private final Set<UUID> operators = new HashSet<>();
    private Phase phase = Phase.IDLE;
    private ItemStack payment = ItemStack.EMPTY, prize = ItemStack.EMPTY;
    private GachaRarity result;
    private long since;

    public GachaponEntity(BlockPos pos, BlockState state) {
        super(YharnamCasino.GACHAPON_ENTITY, pos, state);
        for (var color : COLORS) stock.put(color,new ArrayList<>());
    }
    void setFounder(UUID id) { if (founder == null) { founder=id; markDirty(); } }
    public UUID identity() { return identity; }
    public boolean canAccess(PlayerEntity p) {
        return p.getUuid().equals(founder) || operators.contains(p.getUuid()) ||
            p instanceof ServerPlayerEntity server && CasinoStaffAccess.isStaff(server);
    }
    void share(ServerPlayerEntity giver, PlayerEntity receiver, boolean grant) {
        if (!giver.getUuid().equals(founder) && !CasinoStaffAccess.isStaff(giver)) { tell(giver,"gacha.denied"); return; }
        if (grant) operators.add(receiver.getUuid()); else operators.remove(receiver.getUuid());
        markDirty();
        tell(giver,grant?"gacha.shared":"gacha.revoked",receiver.getName());
    }
    public Phase phase() { return phase; }
    public GachaRarity result() { return result; }
    public int stockCount(GachaRarity r) { return counts[r.ordinal()]; }
    public float elapsed(float delta) { return world==null ? 0 : Math.max(0, world.getTime()-since+delta); }
    private int total() { return Arrays.stream(counts).sum(); }

    public void interact(ServerPlayerEntity p, Vec3d at) {
        if (!(world instanceof ServerWorld server) || p.isSpectator()) return;
        if (phase != Phase.IDLE) advanceClock(server);
        ItemStack held=p.getMainHandStack();
        boolean adminKey=held.isOf(YharnamCasino.GM_KEY);
        if (held.isOf(YharnamCasino.CABINET_KEY) || adminKey) {
            if (!canAccess(p) || adminKey && !CasinoStaffAccess.isStaff(p)) { tell(p,"gacha.denied"); return; }
            if (!adminKey) CabinetKeyItem.bind(held,identity,pos,server);
            if (p.isSneaking()) {
                if (phase!=Phase.IDLE) { tell(p,"gacha.busy"); return; }
                boolean unload=at.y>=1.0;
                List<ItemStack> out=unload?drainStock():new ArrayList<>(cashbox);
                if (!unload) cashbox.clear();
                returnTo(server,p.getUuid(),out);
                tell(p,unload?"gacha.unloaded":"gacha.cash_taken");
                changed(server);
            } else tell(p,"gacha.service",counts[0],counts[1],counts[2],counts[3],
                    cashbox.stream().mapToLong(s->(long)((ChipItem)s.getItem()).value()*s.getCount()).sum());
            return;
        }
        if (held.getItem() instanceof CapsuleItem item && at.y>=1.0) {
            if (!canAccess(p)) { tell(p,"gacha.denied"); return; }
            int count=Math.min(CAPACITY-total(),p.isSneaking()?held.getCount():1);
            if (count<=0) { tell(p,"gacha.full"); return; }
            var batch=stock.get(item.rarity());
            for (int i=0;i<count;i++) {
                var one=held.split(1);
                if (!CapsuleItem.sealed(one)) CapsuleItem.seal(one,ItemStack.EMPTY);
                batch.add(one);
            }
            counts[item.rarity().ordinal()]+=count;
            p.getInventory().markDirty();
            tell(p,"gacha.loaded",count,total());
            changed(server);
            return;
        }
        boolean intake=at.x>=-.38 && at.x<=-.038 && at.y>=.61 && at.y<=.97 && at.z>=.18;
        boolean crank=at.x>=-.025 && at.x<=.36 && at.y>=.59 && at.y<=.973 && at.z>=.18;
        boolean tray=Math.abs(at.x)<=.27 && at.y>=.15 && at.y<=.57 && at.z>=.16;
        if (phase!=Phase.IDLE && !p.getUuid().equals(customer)) { tell(p,"gacha.busy"); return; }
        if (phase==Phase.PAID && p.isSneaking() && held.isEmpty()) {
            refund(server);
            tell(p,"gacha.refunded");
        } else if (phase==Phase.IDLE && intake && held.getItem() instanceof ChipItem) {
            if (!fitsCashbox(held)) { tell(p,"gacha.cash_full"); return; }
            customer=p.getUuid(); payment=held.split(1);
            p.getInventory().markDirty();
            transition(server,Phase.PAID);
            sound(server,SoundEvents.BLOCK_STONE_BUTTON_CLICK_ON,.95f);
            tell(p,"gacha.paid");
        } else if (phase==Phase.PAID && crank) {
            // The hand may still hold the remainder of the stack just used for the single payment.
            spin(server);
            tell(p,"gacha.turning");
        } else if (phase==Phase.TRAY && tray) {
            collect(server);
            tell(p,"gacha.collected");
        } else {
            tell(p,switch(phase) { case IDLE -> "gacha.play_help"; case PAID -> "gacha.paid"; case TURNING -> "gacha.turning"; case TRAY -> "gacha.pickup"; });
        }
    }

    private boolean fitsCashbox(ItemStack stack) {
        if (cashbox.size()<64) return true;
        return cashbox.stream().anyMatch(s->ItemStack.canCombine(s,stack) && s.getCount()<s.getMaxCount());
    }
    private void bankPayment() {
        for (ItemStack stack:cashbox) if (ItemStack.canCombine(stack,payment) && stack.getCount()<stack.getMaxCount()) {
            stack.increment(1); payment=ItemStack.EMPTY; return;
        }
        cashbox.add(payment); payment=ItemStack.EMPTY;
    }
    private void spin(ServerWorld server) {
        var color=GachaRarity.roll(server.random.nextInt(100));
        var templates=stock.get(color);
        if (templates.isEmpty()) {
            prize=new ItemStack(YharnamCasino.capsule(color));
            CapsuleItem.seal(prize,ItemStack.EMPTY);
        } else {
            prize=templates.get(server.random.nextInt(templates.size())).copy();
        }
        result=color;
        bankPayment();
        transition(server,Phase.TURNING);
        sound(server,SoundEvents.BLOCK_LEVER_CLICK,.70f);
    }
    private void refund(ServerWorld server) {
        UUID recipient=customer;
        ItemStack chip=payment;
        clearSale();
        returnTo(server,recipient,List.of(chip));
        changed(server);
    }
    private void collect(ServerWorld server) {
        UUID recipient=customer;
        ItemStack capsule=prize;
        clearSale();
        returnTo(server,recipient,List.of(capsule));
        changed(server);
    }
    private void clearSale() {
        customer=null; payment=ItemStack.EMPTY; prize=ItemStack.EMPTY; result=null; phase=Phase.IDLE; since=0;
    }
    private List<ItemStack> drainStock() {
        var items=new ArrayList<ItemStack>(total());
        for (var batch:stock.values()) { items.addAll(batch); batch.clear(); }
        Arrays.fill(counts,0);
        return items;
    }
    void onRemoved(ServerWorld server) {
        if (phase==Phase.PAID) refund(server);
        else if (phase==Phase.TRAY || phase==Phase.TURNING) collect(server);
        var contents=drainStock();
        contents.addAll(cashbox);
        cashbox.clear();
        if (founder!=null) returnTo(server,founder,contents);
        else for (var stack:contents) ItemScatterer.spawn(server,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,stack);
        markDirty();
    }
    private static void returnTo(ServerWorld server,UUID id,List<ItemStack> items) {
        if (id==null || items.isEmpty()) return;
        var mailbox=CasinoMailbox.get(server.getServer());
        mailbox.recoverVault(id,items);
        var online=server.getServer().getPlayerManager().getPlayer(id);
        if (online!=null) mailbox.deliver(online,false);
    }
    private static void tell(PlayerEntity player,String key,Object... args) { player.sendMessage(Text.translatable(key,args),true); }
    private void sound(ServerWorld world,net.minecraft.sound.SoundEvent sound,float pitch) {
        world.playSound(null,pos,sound,SoundCategory.BLOCKS,.65f,pitch);
    }
    private void transition(ServerWorld server,Phase next) {
        phase=next; since=server.getTime();
        changed(server);
        schedule(server);
    }
    private int duration() { return phase==Phase.TURNING ? TURN_TICKS : HOLD_TICKS; }
    void schedule(ServerWorld w) {
        if (phase!=Phase.IDLE) w.scheduleBlockTick(pos,getCachedState().getBlock(),(int)Math.max(1,Math.min(16,since+duration()-w.getTime())));
    }
    void advanceClock(ServerWorld w) {
        if (phase==Phase.IDLE) return;
        if (!GamingFurnitureBlock.inRange(w,pos,customer)) {
            if(phase==Phase.PAID) refund(w);
            else collect(w);
            return;
        }
        if (w.getTime()<since+duration()) { schedule(w); return; }
        if (phase==Phase.PAID) { refund(w); return; }
        if (phase==Phase.TRAY) { collect(w); return; }
        // Use the original deadline, also when a sleeping chunk resumes minutes later.
        since+=TURN_TICKS; phase=Phase.TRAY;
        if (w.getTime()>=since+HOLD_TICKS) { collect(w); return; }
        sound(w,SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,1.35f);
        var buyer=w.getServer().getPlayerManager().getPlayer(customer);
        if (buyer!=null) tell(buyer,"gacha.pickup");
        changed(w); schedule(w);
    }
    private void changed(ServerWorld w) {
        markDirty();
        w.updateListeners(pos,getCachedState(),getCachedState(),Block.NOTIFY_LISTENERS);
    }
    private void publicState(NbtCompound n) {
        n.putIntArray("Counts",counts); n.putString("Phase",phase.name()); n.putLong("Since",since);
        if (result!=null) n.putString("Color",result.name());
    }
    @Override public NbtCompound toInitialChunkDataNbt() {
        var n=new NbtCompound(); publicState(n); return n;
    }
    @Override public BlockEntityUpdateS2CPacket toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }

    private static NbtList stacks(List<ItemStack> items) {
        var list=new NbtList();
        for (var it:items) list.add(it.writeNbt(new NbtCompound()));
        return list;
    }
    @Override protected void writeNbt(NbtCompound n) {
        super.writeNbt(n); publicState(n);
        n.putUuid("Identity",identity);
        if (founder!=null) n.putUuid("Founder",founder);
        if (customer!=null) n.putUuid("Customer",customer);
        if (!payment.isEmpty()) n.put("Payment",payment.writeNbt(new NbtCompound()));
        if (!prize.isEmpty()) n.put("Prize",prize.writeNbt(new NbtCompound()));
        var owners=new NbtList();
        for (UUID id:operators) owners.add(net.minecraft.nbt.NbtHelper.fromUuid(id));
        n.put("Operators",owners);
        n.put("Drawer",stacks(cashbox));
        for (var color:COLORS) n.put("Stock_"+color.id(),stacks(stock.get(color)));
    }
    @Override public void readNbt(NbtCompound n) {
        super.readNbt(n);
        try { phase=Phase.valueOf(n.getString("Phase")); }
        catch (IllegalArgumentException e) { phase=Phase.IDLE; }
        try { result=GachaRarity.valueOf(n.getString("Color")); }
        catch (IllegalArgumentException e) { result=null; }
        since=n.getLong("Since");
        Arrays.fill(counts,0);
        int[] visible=n.getIntArray("Counts");
        System.arraycopy(visible,0,counts,0,Math.min(visible.length,counts.length));
        if (!n.containsUuid("Identity")) return; // tracking packet
        identity=n.getUuid("Identity");
        founder=n.containsUuid("Founder")?n.getUuid("Founder"):null;
        customer=n.containsUuid("Customer")?n.getUuid("Customer"):null;
        payment=ItemStack.fromNbt(n.getCompound("Payment"));
        prize=ItemStack.fromNbt(n.getCompound("Prize"));
        operators.clear(); cashbox.clear();
        for (var id:n.getList("Operators",NbtElement.INT_ARRAY_TYPE)) operators.add(net.minecraft.nbt.NbtHelper.toUuid(id));
        for (var entry:n.getList("Drawer",NbtElement.COMPOUND_TYPE)) {
            var s=ItemStack.fromNbt((NbtCompound)entry);
            if (s.getItem() instanceof ChipItem) cashbox.add(s);
        }
        for (var color:COLORS) {
            var batch=stock.get(color); batch.clear();
            for (var entry:n.getList("Stock_"+color.id(),NbtElement.COMPOUND_TYPE)) {
                var s=ItemStack.fromNbt((NbtCompound)entry);
                if (s.getItem()==YharnamCasino.capsule(color)) batch.add(s);
            }
            counts[color.ordinal()]=batch.size();
        }
    }
}
