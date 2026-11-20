package com.yharnam.casino;

import java.util.Arrays;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** All bets are held here until paid back through the persistent casino mailbox. */
public final class CardTableEntity extends BlockEntity {
    public static final Identifier PEEK = YharnamCasino.id("private_hand");
    private static final long LIMIT = 100_000_000;
    private final boolean blackjack;
    private final UUID[] owners;
    private final String[] names;
    private final long[] deposits;
    private UUID dealer;
    private String dealerName = "";
    private ItemStack deck = ItemStack.EMPTY;
    private long bank, draftRaise;
    private int button = -1, round;
    private PokerHand poker;
    private BlackjackHand jack;
    private NbtCompound displayed = new NbtCompound();
    private NbtCompound previous = new NbtCompound();
    private String result = "";


    public CardTableEntity(BlockPos pos, BlockState state) {
        super(YharnamCasino.CARD_TABLE_ENTITY, pos, state);
        blackjack = state.isOf(YharnamCasino.BLACK_JACK_TABLE);
        int seats = blackjack ? 7 : 6;
        owners = new UUID[seats];
        names = new String[seats];
        Arrays.fill(names, "");
        deposits = new long[seats];
    }
    public boolean blackjack() {
        return blackjack;
    }
    public int seatCount() {
        return owners.length;
    }
    public NbtCompound display() {
        return displayed;
    }
    public boolean playing() {
        return poker != null || jack != null;
    }
    private int seatOf(UUID id) {
        for (int i = 0; i < owners.length; i++)
            if (id.equals(owners[i]))
                return i;
        return -1;
    }
    private void pay(UUID id, long amount) {
        if (id == null || amount == 0)
            return;
        ServerWorld w = (ServerWorld) world;
        CasinoMailbox mailbox = CasinoMailbox.get(w.getServer());
        mailbox.credit(id, amount, 0);
        ServerPlayerEntity online = w.getServer().getPlayerManager().getPlayer(id);
        if (online != null)
            mailbox.deliver(online, false);
    }
    private boolean absent(UUID id) {
        return !GamingFurnitureBlock.inRange((ServerWorld)world,pos,id);
    }
    void watch(ServerWorld w) {
        boolean dealerGone=dealer!=null && absent(dealer);
        boolean playerGone=Arrays.stream(owners).anyMatch(id -> id!=null && absent(id));
        if(dealerGone || playerGone) {
            if(playing()) refundHand();
            if(dealerGone) leaveDealer(null);
            else for(int i=0;i<owners.length;i++) if(owners[i]!=null && absent(owners[i])) withdrawSeat(i);
            refresh();
        }
        onLoaded(w);
    }
    void onLoaded(ServerWorld w) {
        if(dealer!=null || Arrays.stream(owners).anyMatch(java.util.Objects::nonNull))
            w.scheduleBlockTick(pos,getCachedState().getBlock(),10);
    }
    private void refundHand() {
        // Deposits remain the original buy-ins throughout poker and include every paid split/double.
        // The hand objects contain a second accounting view of those same chips.
        poker=null; jack=null; draftRaise=0;
        clearLast();
        for(int i=0;i<owners.length;i++) {
            pay(owners[i],deposits[i]);
            deposits[i]=0;
        }
        result="Партия сброшена, ставки возвращены";
    }
    private void message(ServerPlayerEntity p, String text) {
        p.sendMessage(Text.literal(text), true);
    }
    private void refresh() {
        markDirty();
        displayed = publicData();
        if (world instanceof ServerWorld w)
            w.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
    }
    private int turn() {
        return poker != null ? poker.turn() : jack != null ? jack.turn() : -1;
    }
    private String phase() {
        return poker != null ? poker.phase() : jack != null ? jack.phase() : "waiting";
    }
    private void clearLast() {
        previous = new NbtCompound();
        result = "";
    }
    private void withdrawSeat(int seat) {
        pay(owners[seat], deposits[seat]);
        deposits[seat] = 0;
        owners[seat] = null;
        names[seat] = "";
    }
    private void leaveDealer(ServerPlayerEntity receiver) {
        for (int i = 0; i < owners.length; i++)
            if (owners[i] != null)
                withdrawSeat(i);
        pay(dealer, bank);
        bank = 0;
        if (!deck.isEmpty()) {
            if (receiver != null && receiver.getUuid().equals(dealer))
                receiver.getInventory().offerOrDrop(deck);
            else if (dealer != null) {
                CasinoMailbox box = CasinoMailbox.get(((ServerWorld) world).getServer());
                box.recoverVault(dealer, java.util.List.of(deck));
                var online=((ServerWorld)world).getServer().getPlayerManager().getPlayer(dealer);
                if(online!=null) box.deliver(online,false);
            }
            deck = ItemStack.EMPTY;
        }
        dealer = null;
        dealerName = "";
    }
    public void interact(ServerPlayerEntity p, Hand hand, int target) {
        if (!(world instanceof ServerWorld) || p.isSpectator())
            return;
        UUID id = p.getUuid();
        ItemStack held = p.getStackInHand(hand);
        boolean sneak = p.isSneaking();
        try {
            if (target == -3) {
                resetRound(p);
                return;
            }
            if (target == -2) {
                int seat = seatOf(id);
                if (poker != null && seat >= 0) {
                    if (held.getItem() instanceof ChipItem chip && turn() == seat) {
                        long cap = poker.balance(seat) - poker.toCall(seat);
                        if (cap <= 0)
                            throw new IllegalArgumentException("Доступен только колл на оставшиеся фишки");
                        long add = (long) chip.value() * (sneak ? held.getCount() : 1);
                        draftRaise = Math.min(cap, draftRaise + add);
                        refresh();
                        message(p, "Повышение на " + draftRaise + ". Подтвердите пустой рукой на своём месте.");
                        return;
                    }
                    if (sneak && held.isEmpty() && turn() == seat && draftRaise > 0) {
                        draftRaise = 0;
                        refresh();
                        message(p, "Набор повышения отменён");
                        return;
                    }
                    int[] cards = poker.cards(seat, true);
                    if (cards.length > 0) {
                        var buf = PacketByteBufs.create();
                        buf.writeBlockPos(pos);
                        buf.writeVarInt(seat);
                        buf.writeVarInt(round);
                        buf.writeIntArray(cards);
                        ServerPlayNetworking.send(p, PEEK, buf);
                        message(
                            p, "Ваши карты: " + cardName(cards[0]) + "  " + cardName(cards[1]) + "  ·  " + status());
                        return;
                    }
                }
                message(p, status());
                return;
            }
            if (target == -1) {
                if (dealer != null && !dealer.equals(id) && absent(dealer)) {
                    if(playing()) refundHand();
                    leaveDealer(null);
                    refresh();
                }
                if (dealer != null && !dealer.equals(id))
                    throw new IllegalArgumentException("Место крупье занято: " + dealerName);
                if (seatOf(id) >= 0)
                    throw new IllegalArgumentException("Сначала покиньте игровое место");
                if (dealer == null) {
                    if (!held.isEmpty() && !(held.getItem() instanceof CardDeckItem)
                        && !(blackjack && held.getItem() instanceof ChipItem))
                        throw new IllegalArgumentException("Положите колоду на место крупье");
                    dealer = id;
                    dealerName = p.getName().getString();
                }
                if (held.getItem() instanceof CardDeckItem) {
                    if (!deck.isEmpty() || playing())
                        throw new IllegalArgumentException("В столе уже есть колода");
                    deck = held.split(1);
                    message(p, "Колода установлена. Игроки могут занять места фишками.");
                } else if (!playing() && held.getItem() instanceof ChipItem chip && blackjack) {
                    int count = sneak ? held.getCount() : 1;
                    long value = (long) count * chip.value();
                    if (bank + value > LIMIT)
                        throw new IllegalArgumentException("Банк заполнен");
                    bank += value;
                    held.decrement(count);
                    message(p, "Банк крупье: " + bank);
                } else if (held.isEmpty() && sneak && !playing()) {
                    leaveDealer(p);
                    message(p, "Вы забрали колоду и банк");
                } else if (held.isEmpty()) {
                    if (playing())
                        dealerAction();
                    else if (deck.isEmpty())
                        message(p, "Вы крупье. Положите колоду на место крупье.");
                    else
                        startHand();
                } else
                    throw new IllegalArgumentException("Крупье: колода, фишки в банк или пустая рука для раздачи");
            } else if (target >= 0 && target < owners.length) {
                if (id.equals(dealer))
                    throw new IllegalArgumentException("Крупье и игрок занимают разные места");
                if (owners[target] != null && !id.equals(owners[target]))
                    throw new IllegalArgumentException("Здесь играет " + names[target]);
                int own = seatOf(id);
                if (own >= 0 && own != target)
                    throw new IllegalArgumentException("Используйте своё игровое место");
                if (!playing()) {
                    if (held.getItem() instanceof ChipItem chip) {
                        if (deck.isEmpty())
                            throw new IllegalArgumentException("Крупье должен установить колоду");
                        int count = sneak ? held.getCount() : 1;
                        long value = (long) count * chip.value();
                        if (deposits[target] + value > LIMIT)
                            throw new IllegalArgumentException("Достигнут предел места");
                        clearLast();
                        owners[target] = id;
                        names[target] = p.getName().getString();
                        deposits[target] += value;
                        held.decrement(count);
                        message(p, "На вашем месте: " + deposits[target]);
                    } else if (held.isEmpty() && sneak && own == target) {
                        withdrawSeat(target);
                        message(p, "Фишки возвращены");
                    } else
                        throw new IllegalArgumentException(
                            "ПКМ фишкой — занять место и внести ставку. Shift — весь стак.");
                } else {
                    if (own != target || turn() != target)
                        throw new IllegalArgumentException(status());
                    if (poker != null) {
                        if (!held.isEmpty() && !(held.getItem() instanceof ChipItem))
                            throw new IllegalArgumentException("Нужна фишка или пустая рука");
                        long raise = held.getItem() instanceof ChipItem chip ? (sneak ? deposits[target] : chip.value())
                                                                             : draftRaise;
                        poker.act(target, raise, held.isEmpty() && sneak);
                        draftRaise = 0;
                    } else {
                        String action = held.isEmpty()           ? (sneak ? "stand" : "hit")
                            : held.getItem() instanceof ChipItem ? (sneak ? "split" : "double")
                                                                 : "";
                        long extra = jack.extraStake(action);
                        long available =
                            held.getItem() instanceof ChipItem chip ? (long) held.getCount() * chip.value() : 0;
                        if (extra > available)
                            throw new IllegalArgumentException(
                                "Для дополнительной ставки возьмите фишки на сумму " + extra + " или больше");
                        if (!jack.act(action))
                            throw new IllegalArgumentException(
                                "Этот ход сейчас недоступен. Проверьте карты и банк крупье.");
                        if (extra > 0) {
                            int denomination = ((ChipItem) held.getItem()).value();
                            int count = (int) ((extra + denomination - 1) / denomination);
                            held.decrement(count);
                            deposits[target] += extra;
                            pay(id, (long) count * denomination - extra);
                        }
                    }
                    message(p, status());
                }
            }
            onLoaded((ServerWorld)world);
            refresh();
        } catch (IllegalArgumentException e) {
            message(p, e.getMessage());
            refresh();
        } catch (DeckEmpty e) {
            cancelMisdeal();
            refresh();
            message(p, "Колода закончилась. Ставки возвращены.");
        }
    }
    private void resetRound(ServerPlayerEntity actor) {
        if(dealer!=null && !actor.getUuid().equals(dealer) && !CasinoStaffAccess.isStaff(actor)) {
            message(actor,"Сбросить партию может крупье или ГМ");return;
        }
        refundHand();
        refresh();
        message(actor,result);
    }
    static final class DeckEmpty extends RuntimeException {}
    private static String cardName(int card) {
        String[] ranks = {"2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A"};
        return ranks[card % 13] + "♣♦♥♠".charAt(card / 13);
    }
    private void startHand() {
        if (deck.isEmpty())
            throw new IllegalArgumentException("Установите колоду");
        if (blackjack)
            jack = BlackjackHand.start(deposits, bank, CardRules.shuffledDeck());
        else {
            poker = PokerHand.start(deposits, button + 1, CardRules.shuffledDeck());
            button = poker.button();
        }
        round++;
        draftRaise = 0;
        clearLast();
    }
    private void dealerAction() {
        if (turn() >= 0) throw new IllegalArgumentException(status());
        if (poker != null) poker.dealerAdvance();
        else
            jack.dealerAdvance();
        if (phase().equals("settled"))
            cashOut();
    }
    private void cashOut() {
        previous = currentCards();
        for(int i=0;i<owners.length;i++) {
            previous.putString("Seat"+i,names[i]);
            if(owners[i]!=null) previous.putUuid("SeatId"+i,owners[i]);
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < owners.length; i++)
            if (owners[i] != null) {
                long won = poker != null ? poker.balance(i) : jack.payout(i);
                if (poker != null ? won > deposits[i] : won > 0) {
                    if (!text.isEmpty())
                        text.append(" · ");
                    long net = won - deposits[i];
                    text.append(names[i]).append(net > 0 ? " +" : " ").append(net == 0 ? "ничья" : Long.toString(net));
                }
                pay(owners[i], won);
                deposits[i] = 0;
                owners[i] = null;
                names[i] = "";
            }
        result = text.isEmpty() ? (jack != null ? "Выиграл крупье" : "Банк разделён") : text.toString();
        if (jack != null)
            bank = jack.bankAfter();
        poker = null;
        jack = null;
        draftRaise = 0;
    }
    private void cancelMisdeal() {
        poker = null;
        jack = null;
        draftRaise = 0;
        for (int i = 0; i < owners.length; i++)
            withdrawSeat(i);
        clearLast();
        result = "Возврат ставок";
    }
    private void finishInterrupted() {
        // Forced removal settles the hand. Never commit a new chip on someone's behalf.
        try {
            while (playing()) {
                if (turn() >= 0) {
                    if (poker != null)
                        poker.act(turn(), 0, poker.toCall(turn()) > 0);
                    else
                        jack.act("stand");
                } else
                    dealerAction();
            }
        } catch (DeckEmpty ex) {
            cancelMisdeal();
        }
    }
    public void onRemoved(ServerWorld w) {
        finishInterrupted();
        for (int i = 0; i < owners.length; i++)
            withdrawSeat(i);
        leaveDealer(null);
    }
    private String status() {
        if (!playing())
            return result.isEmpty() ? "Фишка — занять место. Крупье нажимает на колоду для раздачи." : result;
        if (turn() < 0)
            return "Ход крупье: ПКМ по колоде";
        return "Ход: " + names[turn()] + (poker != null ? " · Уравнять: " + poker.toCall(turn()) : " · Ещё / хватит");
    }
    private NbtCompound currentCards() {
        NbtCompound t = new NbtCompound();
        for (int i = 0; i < owners.length; i++) {
            t.putIntArray("Cards" + i,
                poker != null      ? poker.cards(i, false)
                    : jack != null ? jack.cards(i, 0)
                                   : new int[0]);
            if (poker != null) t.putString("Combo"+i,poker.shownCombination(i));
            if (jack != null)
                t.putIntArray("Split" + i, jack.cards(i, 1));
        }
        if (jack != null)
            t.putIntArray("DealerCards", jack.dealerCards());
        if (poker != null)
            t.putIntArray("Board", poker.board());
        return t;
    }
    private NbtCompound publicData() {
        NbtCompound t = playing() || previous.isEmpty() ? currentCards() : previous.copy();
        t.putLong("DraftRaise", draftRaise);
        t.putInt("RoundNumber", round);
        t.putBoolean("Public", true);
        t.putString("Phase", phase());
        t.putString("Result", result);
        t.putString("Dealer", dealerName);
        t.putLong("Bank", bank);
        t.putInt("Turn", turn());
        t.putInt("SplitTurn", jack != null ? jack.splitIndex() : 0);
        t.putInt("Button", poker != null ? poker.button() : -1);
        if (deck.getItem() instanceof CardDeckItem item)
            t.putString("Back", item.backTexture().toString());
        for (int i = 0; i < owners.length; i++) {
            if (!t.contains("Seat"+i) || owners[i]!=null) t.putString("Seat" + i, names[i]);
            if(owners[i]!=null) t.putUuid("SeatId"+i,owners[i]);
            t.putLong("Bet" + i, poker != null ? poker.committed(i) : deposits[i]);
            t.putLong("Stack" + i, poker != null ? poker.balance(i) : 0);
        }
        return t;
    }
    @Override
    public NbtCompound toInitialChunkDataNbt() {
        return publicData();
    }
    @Override
    public BlockEntityUpdateS2CPacket toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }
    @Override
    protected void writeNbt(NbtCompound t) {
        super.writeNbt(t);
        t.putLong("DraftRaise", draftRaise);
        t.putInt("RoundNumber", round);
        if (dealer != null)
            t.putUuid("DealerId", dealer);
        t.putString("DealerName", dealerName);
        t.putLong("Bank", bank);
        t.putInt("Button", button);
        t.putString("Result", result);
        t.put("Previous", previous.copy());
        if (!deck.isEmpty())
            t.put("Deck", deck.writeNbt(new NbtCompound()));
        for (int i = 0; i < owners.length; i++)
            if (owners[i] != null) {
                t.putUuid("Owner" + i, owners[i]);
                t.putString("Name" + i, names[i]);
                t.putLong("Deposit" + i, deposits[i]);
            }
        if (jack != null)
            t.put("BJ", jack.toNbt());
        if (poker != null)
            t.put("Poker", poker.toNbt());
    }
    @Override
    public void readNbt(NbtCompound t) {
        super.readNbt(t);
        draftRaise = t.getLong("DraftRaise");
        round = t.getInt("RoundNumber");
        if (t.getBoolean("Public")) {
            displayed = t.copy();
            return;
        }
        dealer = t.containsUuid("DealerId") ? t.getUuid("DealerId") : null;
        dealerName = t.getString("DealerName");
        bank = t.getLong("Bank");
        button = t.contains("Button") ? t.getInt("Button") : -1;
        result = t.getString("Result");
        previous = t.getCompound("Previous");
        deck = t.contains("Deck") ? ItemStack.fromNbt(t.getCompound("Deck")) : ItemStack.EMPTY;
        for (int i = 0; i < owners.length; i++) {
            owners[i] = t.containsUuid("Owner" + i) ? t.getUuid("Owner" + i) : null;
            names[i] = t.getString("Name" + i);
            deposits[i] = t.getLong("Deposit" + i);
        }
        jack = t.contains("BJ") ? BlackjackHand.fromNbt(t.getCompound("BJ")) : null;
        poker = t.contains("Poker") ? PokerHand.fromNbt(t.getCompound("Poker")) : null;
        displayed = publicData();
    }
}
