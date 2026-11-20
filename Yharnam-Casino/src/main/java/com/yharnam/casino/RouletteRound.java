package com.yharnam.casino;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

/** Printed targets and the chip ledger of a single-zero table. Every chip has one owner or is in escrow. */
public final class RouletteRound {
    public static final int SEATS = 5;
    static final int MAX_BETS_PER_SEAT = 32;
    private static final long TABLE_LIMIT = 1_000_000_000_000L;
    private static final int[] WHEEL = {0,32,15,19,4,21,2,25,17,34,6,27,13,36,11,30,8,23,10,5,24,16,33,1,20,14,31,9,22,18,29,7,28,12,35,3,26};
    private static final double X0=-.19, Y0=-.142, DX=.141, DY=.207, END=X0+12*DX;
    public record Target(long mask, String label, double x, double z, int payout) {
        public boolean covers(int n) { return (mask & (1L << n))!=0; }
    }
    public record Bet(int seat,long amount,Target target) {}
    public record Win(int seat,long amount) {}
    private static final Map<Long,Target> TARGETS=new LinkedHashMap<>();
    private static final Target[] OUTSIDE=new Target[6], DOZEN=new Target[3], COLUMN=new Target[3];
    static {
        inside(X0-.095,Y0+1.5*DY,0);
        for (int c=0;c<12;c++) {
            double x=X0+(c+.5)*DX;
            int a=1+c*3;
            for (int r=0;r<3;r++) {
                double y=Y0+(r+.5)*DY;
                int n=a+r;
                inside(x,y,n);
                if (r<2) inside(x,y+DY/2,n,n+1);
                if (c<11) {
                    inside(x+DX/2,y,n,n+3);
                    if (r<2) inside(x+DX/2,y+DY/2,n,n+1,n+3,n+4);
                }
                if (c==0) {
                    inside(X0,y,0,n);
                    if (r<2) inside(X0,y+DY/2,0,n,n+1);
                }
            }
            inside(x,Y0,a,a+1,a+2);
            if (c<11) inside(x+DX/2,Y0,a,a+1,a+2,a+3,a+4,a+5);
        }
        inside(X0,Y0,0,1,2,3);
        for (int i=0;i<3;i++) {
            final int band=i;
            COLUMN[i]=group("Колонка "+(i+1),END+.074,Y0+(i+.5)*DY,n->(n-1)%3==band);
            DOZEN[i]=group((1+12*i)+"–"+(12+12*i),X0+(i*4+2)*DX,-.225,n->(n-1)/12==band);
        }
        String[] names={"1–18","Чёт","Красное","Чёрное","Нечёт","19–36"};
        for (int i=0;i<6;i++) {
            final int field=i;
            OUTSIDE[i]=group(names[i],X0+(i*2+1)*DX,-.407,n->switch(field) {
                case 0 -> n<=18; case 1 -> n%2==0; case 2 -> AttractionEntity.red(n);
                case 3 -> !AttractionEntity.red(n); case 4 -> n%2!=0; default -> n>18;
            });
        }
    }
    private static Target group(String name,double x,double y,java.util.function.IntPredicate predicate) {
        long mask=0;
        for (int n=1;n<=36;n++) if (predicate.test(n)) mask|=1L<<n;
        return register(mask,name,x,y);
    }
    private static Target register(long mask,String name,double x,double y) {
        Target t=new Target(mask,name,x,-y,36/Long.bitCount(mask)-1);
        TARGETS.put(mask,t);
        return t;
    }
    private static void inside(double x,double y,int... numbers) {
        String label=Arrays.stream(numbers).mapToObj(Integer::toString).collect(java.util.stream.Collectors.joining("/"));
        register(mask(numbers),label,x,y);
    }
    private static long mask(int... numbers) {
        long mask=0;
        for (int n:numbers) mask|=1L<<n;
        return mask;
    }
    private static Target get(int... numbers) { return TARGETS.get(mask(numbers)); }
    public static Target straight(int n) { return n>=0 && n<=36 ? get(n) : null; }
    public static List<Target> targets() { return List.copyOf(TARGETS.values()); }
    public static Target targetAt(double x,double z) {
        double y=-z, u=(x-X0)/DX, v=(y-Y0)/DY;
        boolean gridHeight=v>=0 && v<3;
        if (x<X0-.025) {
            double left=X0-.205+Math.abs(v-1.5)*(.097/1.5);
            return gridHeight && x>=left ? straight(0) : null;
        }
        if (x>=END) return x<=END+.147 && gridHeight ? COLUMN[(int)v] : null;
        if (x<X0-.025 || y>Y0+3*DY || y<-.505) return null;
        int c=Math.max(0,Math.min(11,(int)Math.floor(u))), r=Math.max(0,Math.min(2,(int)Math.floor(v)));
        if (y<Y0-.026) {
            if (x<X0) return null;
            return y>=-.315 ? DOZEN[c/4] : OUTSIDE[c/2];
        }
        int bx=(int)Math.round(u), by=(int)Math.round(v);
        boolean vertical=bx>=0 && bx<12 && Math.abs(u-bx)*DX<=.025;
        boolean horizontal=by>0 && by<3 && Math.abs(v-by)*DY<=.030;
        if (Math.abs(y-Y0)<=.026) {
            int a=1+3*(vertical?bx:c);
            if (!vertical) return get(a,a+1,a+2);
            return bx==0 ? get(0,1,2,3) : get(a-3,a-2,a-1,a,a+1,a+2);
        }
        if (!gridHeight) return null;
        if (vertical) {
            int n=1+3*bx+(horizontal?by-1:r);
            if (bx==0) return horizontal ? get(0,n,n+1) : get(0,n);
            return horizontal ? get(n-3,n-2,n,n+1) : get(n-3,n);
        }
        int n=1+3*c+(horizontal?by-1:r);
        return horizontal ? get(n,n+1) : straight(n);
    }
    public static int[] wheelOrder() { return WHEEL.clone(); }

    private final long[] balances=new long[SEATS];
    private final List<Bet> bets=new ArrayList<>();
    private final List<Bet> view=Collections.unmodifiableList(bets);
    private long house;
    private int result=-1;
    private boolean spinning;
    private RouletteRound() {}
    public static RouletteRound create() { return new RouletteRound(); }
    public int result() { return result; }
    public boolean spinning() { return spinning; }
    public long house() { return house; }
    public long bankroll(int seat) { return balances[seat]; }
    public List<Bet> bets() { return view; }
    /** Return every unresolved bet to its seat and reopen the round. */
    public void abort() {
        for (Bet bet : bets) balances[bet.seat] += bet.amount;
        bets.clear();
        spinning = false;
        result = -1;
    }
    public int betCount(int seat) {
        int n=0;
        for (Bet b:bets) if (b.seat==seat) n++;
        return n;
    }
    private void open() {
        if (spinning) throw new IllegalStateException("Ставки уже закрыты");
    }
    private void checkDeposit(long amount) {
        open();
        long total=house;
        for (long cash:balances) total+=cash;
        for (Bet bet:bets) total+=bet.amount;
        if (amount<=0 || amount>TABLE_LIMIT-total) throw new IllegalArgumentException("Достигнут лимит фишек стола");
    }
    public void depositHouse(long amount) { checkDeposit(amount); house+=amount; }
    public long withdrawHouse() {
        open();
        if (!bets.isEmpty()) throw new IllegalStateException("Сначала завершите раунд");
        long cash=house;
        house=0;
        return cash;
    }
    public void deposit(int seat,long amount) { checkDeposit(amount); balances[seat]+=amount; }
    public long withdraw(int seat,long amount) {
        open();
        if (amount<=0 || amount>balances[seat]) throw new IllegalArgumentException("Недостаточно фишек");
        balances[seat]-=amount;
        return amount;
    }
    public void addBet(int seat,Target target,long amount) {
        open();
        if (target==null || TARGETS.get(target.mask)!=target) throw new IllegalArgumentException("Неизвестное поле");
        if (amount<=0 || amount>balances[seat]) throw new IllegalArgumentException("Пополните фишки на своём месте");
        if (betCount(seat)>=MAX_BETS_PER_SEAT) throw new IllegalStateException("Лимит — 32 ставки за раунд");
        for (int n=0;n<=36;n++) if (target.covers(n)) {
            long profit=amount*target.payout;
            for (Bet b:bets) if (b.target.covers(n)) profit+=b.amount*b.target.payout;
            if (profit>house) throw new IllegalStateException("Банк крупье не покрывает выплату. Требуется "+profit);
        }
        balances[seat]-=amount;
        bets.add(new Bet(seat,amount,target));
    }
    public void cancelBet(int seat) {
        open();
        for (int i=bets.size()-1;i>=0;i--) if (bets.get(i).seat==seat) {
            balances[seat]+=bets.remove(i).amount;
            return;
        }
    }
    public void closeBets() {
        open();
        if (bets.isEmpty()) throw new IllegalStateException("Сначала игроки должны сделать ставку");
        spinning=true;
    }
    public List<Win> settle(int number) {
        if (!spinning || number<0 || number>36) throw new IllegalStateException("Недействительный результат раунда");
        List<Win> wins=new ArrayList<>();
        for (Bet b:bets) {
            if (b.target.covers(number)) {
                long profit=b.amount*b.target.payout;
                house-=profit;
                balances[b.seat]+=b.amount+profit;
                wins.add(new Win(b.seat,b.amount+profit));
            } else house+=b.amount;
        }
        bets.clear();
        spinning=false;
        result=number;
        return wins;
    }
    public NbtCompound toNbt() {
        var n=new NbtCompound();
        n.putLongArray("Balances",balances); n.putLong("House",house);
        n.putInt("Result",result); n.putBoolean("Spinning",spinning);
        var list=new NbtList();
        for (Bet bet:bets) {
            var b=new NbtCompound();
            b.putInt("Seat",bet.seat); b.putLong("Amount",bet.amount); b.putLong("Mask",bet.target.mask);
            list.add(b);
        }
        n.put("Bets",list);
        return n;
    }
    public static RouletteRound fromNbt(NbtCompound n) {
        var round=create();
        var values=n.getLongArray("Balances");
        System.arraycopy(values,0,round.balances,0,Math.min(SEATS,values.length));
        round.house=n.getLong("House"); round.result=n.contains("Result")?n.getInt("Result"):-1;
        for (var entry:n.getList("Bets",NbtElement.COMPOUND_TYPE)) {
            var b=(NbtCompound)entry;
            var target=TARGETS.get(b.getLong("Mask"));
            int seat=b.getInt("Seat"); long amount=b.getLong("Amount");
            if (seat>=0 && seat<SEATS && target!=null && amount>0 && amount<=TABLE_LIMIT)
                round.bets.add(new Bet(seat,amount,target));
        }
        round.spinning=n.getBoolean("Spinning") && !round.bets.isEmpty();
        return round;
    }
}
