package com.yharnam.casino;

import java.util.Arrays;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/** One authoritative ledger and one animation clock per table. No per-chip or per-rat entities. */
public final class AttractionEntity extends BlockEntity {
    public static final int DEALER = 5, NONE = -1;
    private static final double[] X = {-.24, .88, -.24, .88, 1.79, -1.52};
    private static final double[] Z = {.72, .72, -.72, -.72, 0, -.69};
    private final UUID[] people = new UUID[6];
    private final String[] names = new String[6];
    private final int[] values = {1,1,1,1,1};
    private RouletteRound round = RouletteRound.create();
    private long started = -1, seed;
    private boolean hasRun;
    private double initialRotor;
    private RouletteSpin wheel;
    private RatRace race;
    private int lastLane = -1;

    public AttractionEntity(BlockPos pos, BlockState state) {
        super(YharnamCasino.ATTRACTION_ENTITY, pos, state);
        Arrays.fill(names, "");
    }
    public boolean derby() { return getCachedState().isOf(YharnamCasino.RAT_DERBY); }
    public boolean busy() { return started >= 0 || !round.bets().isEmpty(); }
    public boolean moving() { return started >= 0; }
    public int lastLane() { return lastLane; }
    public String name(int i) { return names[i]; }
    public int stake(int i) { return values[i]; }
    public record Pile(int seat, RouletteRound.Target target, int count, long amount, boolean shared) {}
    private java.util.List<Pile> piles = java.util.List.of();
    public java.util.List<Pile> piles() { return piles; }
    private void updatePiles() {
        var grouped = new java.util.LinkedHashMap<Long,Pile>();
        var seatsPerField=new java.util.HashMap<Long,Integer>();
        for (var bet:round.bets()) {
            seatsPerField.merge(bet.target().mask(),1<<bet.seat(),(a,b)->a|b);
            long key=(bet.target().mask()<<3)|bet.seat();
            Pile old=grouped.get(key);
            grouped.put(key,new Pile(bet.seat(),bet.target(),old==null ? 1 : old.count()+1,
                (old==null ? 0 : old.amount()) + bet.amount(),false));
        }
        piles=grouped.values().stream().map(p->new Pile(p.seat(),p.target(),p.count(),p.amount(),
                Integer.bitCount(seatsPerField.get(p.target().mask()))>1)).toList();
    }
    public RouletteRound ledger() { return round; }
    public static double seatX(int i) { return X[i]; }
    public static double seatZ(int i) { return Z[i]; }
    public static boolean starter(double x, double z) { return x >= 1.02 && x <= 1.67 && z >= .51 && z <= .84; }
    public static int marker(double x, double z) {
        for (int i=0; i<X.length; i++) {
            double dx = x-X[i], dz = z-Z[i];
            if (i != 4 ? Math.abs(dx)<.29 && Math.abs(dz)<.142 :
                    Math.abs(dx)<.092 && Math.abs(dz)<.32) return i;
        }
        return NONE;
    }
    public static boolean atSide(int marker,double x,double z) {
        if (marker==DEALER) return z<-.61 || x<-1.56;
        if (marker==4) return x>1.50;
        return marker<2 ? z>.61 : z<-.61;
    }
    public int role(UUID id) {
        for (int i=0;i<people.length;i++) if (id.equals(people[i])) return i;
        return NONE;
    }
    public double elapsed(float delta) {
        return world == null || !moving() ? 0 : Math.max(0,world.getTime()-started+delta);
    }
    public RouletteSpin.Frame ball(float delta) {
        if (wheel == null) return new RouletteSpin.Frame(0, 0, .319, .929);
        return wheel.at(moving() ? elapsed(delta) : wheel.duration());
    }
    public double ratProgress(int lane, float delta) {
        if (race == null) return 0;
        return race.progress(lane, moving() ? elapsed(delta)-12 : race.duration());
    }

    public void use(ServerPlayerEntity player, double x, double z) {
        if (!(world instanceof ServerWorld server) || player.isSpectator()) return;
        if (derby()) {
            if (resetButton(true,x,z)) { if (canReset(player)) resetGame(server); else tell(player,"Сбросить стол может крупье или ГМ"); return; }
            if (!starter(x,z)) { tell(player, "Кнопка старта справа, рядом с красной рукоятью"); return; }
            if (moving()) { tell(player, "Заезд уже идёт"); return; }
            seed=server.random.nextLong();
            race=new RatRace(seed);
            started=server.getTime();
            lastLane=-1;
            hasRun=true;
            server.playSound(null,pos,SoundEvents.BLOCK_LEVER_CLICK,SoundCategory.BLOCKS,.7f,.8f);
            announce(server,"Крысиные бега! Стартуют дорожки 1–4");
            changed(server);
            return;
        }
        if (resetButton(false,x,z)) { if (canReset(player)) resetGame(server); else tell(player,"Сбросить стол может крупье или ГМ"); return; }
        int marker=marker(x,z), role=role(player.getUuid());
        try {
            if (role == NONE) {
                if (marker == NONE) { tell(player,"Выберите свободную цветную метку места или метку крупье"); return; }
                if (people[marker] != null) { tell(player,"Это место занимает " + names[marker]); return; }
                if (moving()) { tell(player,"Дождитесь окончания вращения"); return; }
                var standing=GamingFurnitureBlock.attractionHit(pos,getCachedState().get(GamingFurnitureBlock.FACING),player.getPos());
                if (!atSide(marker,standing.x,standing.z)) { tell(player,"Обойдите стол и займите место со стороны метки"); return; }
                people[marker]=player.getUuid();
                names[marker]=player.getGameProfile().getName();
                tell(player,marker==DEALER ? "Вы крупье. Внесите фишки в банк на своей метке. ПКМ по колесу — запуск."
                    : "Ваше место №"+(marker+1)+". Положите фишки на свою метку, затем нажмите на поле ставки.");
            } else if (moving()) {
                tell(player,"Ставки закрыты. Шарик в игре");
                return;
            } else if (marker == role) {
                ItemStack held=player.getMainHandStack();
                if (held.getItem() instanceof ChipItem chip) {
                    int count=player.isSneaking() ? held.getCount() : 1;
                    long cash=(long)count*chip.value();
                    if (role==DEALER) round.depositHouse(cash);
                    else { round.deposit(role,cash); values[role]=chip.value(); }
                    held.decrement(count);
                    player.getInventory().markDirty();
                    tell(player,"Внесено "+cash+" · "+(role==DEALER ? "банк "+round.house() : "остаток "+round.bankroll(role)));
                } else if (player.isSneaking() && held.isEmpty()) {
                    if (role==DEALER && !round.bets().isEmpty()) {
                        tell(player,"Сначала завершите раунд со ставками"); return;
                    }
                    release(server,role);
                    tell(player,"Место освобождено. Остаток возвращён");
                } else if (role!=DEALER && held.isEmpty()) {
                    int[] chips=YharnamCasino.CHIP_VALUES;
                    int next=(Arrays.binarySearch(chips,values[role])+1)%chips.length;
                    values[role]=chips[Math.max(0,next)];
                    tell(player,"Номинал ставки "+values[role]+" · остаток "+round.bankroll(role));
                } else {
                    tell(player,role==DEALER ? "Банк "+round.house()+" · ПКМ по колесу — запуск · Shift+ПКМ пустой рукой — выйти"
                        : "Остаток "+round.bankroll(role)+" · ставка "+values[role]+" · Shift+ПКМ пустой рукой по месту — выйти");
                }
            } else if (role==DEALER) {
                if (Math.hypot(x-RouletteSpin.CX,z-RouletteSpin.CZ)>.68) {
                    tell(player,"Для запуска нажмите на колесо"); return;
                }
                round.closeBets();
                initialRotor=ball(0).rotor();
                seed=server.random.nextLong();
                wheel=new RouletteSpin(seed,initialRotor);
                started=server.getTime();
                hasRun=true;
                server.playSound(null,pos,SoundEvents.BLOCK_STONE_BUTTON_CLICK_ON,SoundCategory.BLOCKS,.8f,1.35f);
                announce(server,"Ставки закрыты. Крупье запустил шарик");
            } else {
                if (people[DEALER]==null || !present(server,DEALER)) { tell(player,"Для игры нужен крупье у стола"); return; }
                if (marker!=NONE) { tell(player,"Это место другого участника"); return; }
                var target=RouletteRound.targetAt(x,z);
                if (target==null) { tell(player,"Наведитесь на номер, линию, угол или внешнее поле"); return; }
                if (player.isSneaking()) {
                    if (round.betCount(role)==0) { tell(player,"Отменять нечего"); return; }
                    round.cancelBet(role);
                    tell(player,"Последняя ставка снята · остаток "+round.bankroll(role));
                } else {
                    int value=player.getMainHandStack().getItem() instanceof ChipItem chip ? chip.value() : values[role];
                    round.addBet(role,target,value);
                    values[role]=value;
                    tell(player,target.label()+" · поставлено "+value+" · остаток "+round.bankroll(role));
                }
            }
            changed(server);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            tell(player,ex.getMessage());
        }
    }
    private static void tell(ServerPlayerEntity player, String line) { player.sendMessage(Text.literal(line),true); }
    private void announce(ServerWorld server, String line) {
        for (var player:server.getEntitiesByClass(ServerPlayerEntity.class,new Box(pos).expand(12), p->true))
            player.sendMessage(Text.literal(line),false);
    }
    private boolean present(ServerWorld server,int i) {
        return GamingFurnitureBlock.inRange(server,pos,people[i]);
    }
    private void release(ServerWorld server,int i) {
        if (people[i]==null) return;
        long amount;
        if (i==DEALER) amount=round.withdrawHouse();
        else {
            while (round.betCount(i)>0) round.cancelBet(i);
            amount=round.bankroll(i);
            if (amount>0) round.withdraw(i,amount);
            values[i]=1;
        }
        UUID owner=people[i];
        people[i]=null;
        names[i]="";
        if (amount<=0) return;
        var mailbox=CasinoMailbox.get(server.getServer());
        mailbox.credit(owner,amount,0);
        var online=server.getServer().getPlayerManager().getPlayer(owner);
        if (online!=null) mailbox.deliver(online,false);
    }
    private void settle(ServerWorld server) {
        int number=wheel.number();
        var wins=round.settle(number);
        String color=number==0 ? "зеро" : red(number) ? "красное" : "чёрное";
        announce(server,"Рулетка · "+number+" · "+color);
        for (int i=0;i<5;i++) if (people[i]!=null) {
            long amount=0;
            for (var win:wins) if (win.seat()==i) amount+=win.amount();
            var p=server.getServer().getPlayerManager().getPlayer(people[i]);
            if (p!=null && amount>0) p.sendMessage(Text.literal("Рулетка · на ваше место выплачено "+amount+" с учётом ставки"),false);
        }
    }
    public static boolean red(int n) {
        return switch(n) { case 1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36 -> true; default -> false; };
    }
    public static boolean resetButton(boolean derby,double x,double z) {
        double cx=derby ? -1.23 : -.85, cz=derby ? .70 : -.70;
        return Math.abs(x-cx)<=.16 && Math.abs(z-cz)<=.12;
    }
    private boolean canReset(ServerPlayerEntity player) {
        return derby() || people[DEALER]==null || CasinoStaffAccess.isStaff(player) || people[DEALER].equals(player.getUuid());
    }
    private void resetGame(ServerWorld server) {
        if (!derby()) round.abort();
        started=-1;
        wheel=null;
        race=null;
        lastLane=-1;hasRun=false;initialRotor=0;seed=0;
        for (int i=0;i<=DEALER;i++) if (people[i]!=null) release(server,i);
        round=RouletteRound.create();
        updatePiles();
        changed(server);
        announce(server,derby()?"Заезд сброшен":"Стол сброшен. Ставки и фишки возвращены");
    }
    void onRemoved(ServerWorld server) {
        if (!derby()) {
            if (round.spinning()) settle(server);
            for (int i=0;i<=DEALER;i++) release(server,i);
        }
        started=-1;
    }
    void onLoaded(ServerWorld server) { schedule(server); }
    private void schedule(ServerWorld server) {
        if (moving()) {
            int length=derby() ? race.duration()+12 : wheel.duration();
            server.scheduleBlockTick(pos,getCachedState().getBlock(),(int)Math.max(1,Math.min(10,started+length-server.getTime())));
        } else {
            for (UUID id:people) if (id!=null) {
                server.scheduleBlockTick(pos,getCachedState().getBlock(),10); break;
            }
        }
    }
    void advanceClock(ServerWorld server) {
        boolean edit=false;
        if(!derby()) {
            boolean dealerGone=people[DEALER]!=null && !present(server,DEALER);
            boolean seatGone=false;
            for(int i=0;i<DEALER;i++) if(people[i]!=null && !present(server,i)) seatGone=true;
            if(dealerGone || seatGone) {
                if(round.spinning() || dealerGone) {
                    round.abort();started=-1;hasRun=false;wheel=null;initialRotor=0;
                }
                for(int i=0;i<=DEALER;i++) if(people[i]!=null && !present(server,i)) release(server,i);
                edit=true;
            }
        }
        if (moving() && server.getTime()-started >= (derby() ? race.duration()+12 : wheel.duration())) {
            if (derby()) {
                lastLane=race.winner();
                announce(server,"Крысиные бега · первой финишировала дорожка №"+(lastLane+1));
            } else settle(server);
            server.playSound(null,pos,SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(),SoundCategory.BLOCKS,.7f,1.2f);
            started=-1; edit=true;
        }
        if (edit) changed(server); else schedule(server);
    }
    private void changed(ServerWorld server) {
        markDirty();
        server.updateListeners(pos,getCachedState(),getCachedState(),Block.NOTIFY_LISTENERS);
        schedule(server);
    }
    private void writeState(NbtCompound n) {
        n.put("Ledger",round.toNbt());
        n.putLong("Started",started); n.putLong("Seed",seed); n.putBoolean("HasRun",hasRun);
        n.putDouble("Rotor",initialRotor); n.putInt("LastLane",lastLane);
        n.putIntArray("Chips",values);
        for (int i=0;i<people.length;i++) if (people[i]!=null) {
            n.putUuid("Owner"+i,people[i]); n.putString("Name"+i,names[i]);
        }
    }
    @Override protected void writeNbt(NbtCompound n) { super.writeNbt(n); writeState(n); }
    @Override public NbtCompound toInitialChunkDataNbt() { var n=new NbtCompound(); writeState(n); return n; }
    @Override public BlockEntityUpdateS2CPacket toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }
    @Override public void readNbt(NbtCompound n) {
        super.readNbt(n);
        round=RouletteRound.fromNbt(n.getCompound("Ledger"));
        updatePiles();
        started=n.contains("Started") ? n.getLong("Started") : -1;
        long newSeed=n.getLong("Seed");
        double newRotor=n.getDouble("Rotor");
        hasRun=n.getBoolean("HasRun");
        if(!hasRun) { wheel=null;race=null; }
        if (hasRun && derby() && (race==null || seed!=newSeed)) race=new RatRace(newSeed);
        if (hasRun && !derby() && (wheel==null || seed!=newSeed || initialRotor!=newRotor)) wheel=new RouletteSpin(newSeed,newRotor);
        seed=newSeed; initialRotor=newRotor;
        lastLane=n.contains("LastLane") ? n.getInt("LastLane") : -1;
        int[] chips=n.getIntArray("Chips");
        for (int i=0;i<people.length;i++) {
            people[i]=n.containsUuid("Owner"+i)?n.getUuid("Owner"+i):null;
            names[i]=n.getString("Name"+i);
            if (i<values.length) values[i]=i<chips.length && chips[i]>0 ? chips[i] : 1;
        }
    }
}
