package com.yharnam.casino;

import java.util.Arrays;
import net.minecraft.nbt.NbtCompound;

/** One deck, dealer stands on 18, a single split, double after split, one card to split aces. */
final class BlackjackHand {
    static final int SEATS = 7;
    private final int[][] hands = new int[SEATS * 2][];
    private final long[] wagers = new long[SEATS * 2], payouts = new long[SEATS];
    private final int[] deck;
    private int[] dealer = {};
    private int drawn, active = -1;
    private long bank;
    private String phase = "players";

    private BlackjackHand(int[] deck, long bank) {
        this.deck = deck.clone();
        this.bank = bank;
        Arrays.setAll(hands, i -> new int[0]);
    }
    static BlackjackHand start(long[] bets, long bank, int[] deck) {
        BlackjackHand hand = new BlackjackHand(deck, bank);
        long risk = 0;
        for (int i = 0; i < SEATS; i++) {
            if (bets[i] < 0 || (bets[i] & 1) != 0)
                throw new IllegalArgumentException("Для выплаты 3:2 поставьте чётную сумму");
            risk += bets[i] + bets[i] / 2;
            hand.wagers[i * 2] = bets[i];
        }
        if (risk == 0 || risk > bank)
            throw new IllegalArgumentException("Нужна ставка и запас крупье на выплату 3:2");
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < SEATS; i++)
                if (bets[i] > 0)
                    hand.take(i * 2);
            hand.dealer = append(hand.dealer, hand.draw());
        }
        if (CardRules.blackjackTotal(hand.dealer) == 21)
            hand.phase = "showdown";
        else
            hand.advance();
        return hand;
    }
    private static int[] append(int[] cards, int value) {
        int[] more = Arrays.copyOf(cards, cards.length + 1);
        more[cards.length] = value;
        return more;
    }
    private int draw() {
        if (drawn >= deck.length)
            throw new CardTableEntity.DeckEmpty();
        return deck[drawn++];
    }
    private void take(int i) {
        hands[i] = append(hands[i], draw());
    }
    private boolean natural(int i) {
        return hands[(i / 2) * 2 + 1].length == 0 && hands[i].length == 2 && CardRules.blackjackTotal(hands[i]) == 21;
    }
    private void advance() {
        do {
            active++;
        } while (active < hands.length && (wagers[active] == 0 || CardRules.blackjackTotal(hands[active]) >= 21));
        if (active == hands.length) {
            active = -1;
            phase = "dealer";
        }
    }
    int turn() {
        return active < 0 ? -1 : active / 2;
    }
    int splitIndex() {
        return active < 0 ? 0 : active % 2;
    }
    String phase() {
        return phase;
    }
    long extraStake(String action) {
        return active >= 0 && (action.equals("split") || action.equals("double")) ? wagers[active] : 0;
    }
    long committed(int i) {
        return wagers[2 * i] + wagers[2 * i + 1];
    }
    long bankAfter() {
        return bank;
    }
    long payout(int i) {
        return payouts[i];
    }
    int[] cards(int i, int split) {
        return hands[2 * i + split].clone();
    }
    int[] dealerCards() {
        if (phase.equals("players"))
            return new int[] {dealer[0], -1};
        return dealer.clone();
    }
    private long liability() {
        long total = 0;
        for (int i = 0; i < hands.length; i++)
            if (wagers[i] > 0)
                total += natural(i)                            ? wagers[i] + wagers[i] / 2
                    : CardRules.blackjackTotal(hands[i]) <= 21 ? wagers[i]
                                                               : 0;
        return total;
    }
    boolean act(String action) {
        if (active < 0 || !phase.equals("players"))
            return false;
        switch (action) {
            case "stand" -> advance();
            case "hit" -> {
                take(active);
                if (CardRules.blackjackTotal(hands[active]) >= 21)
                    advance();
            }
            case "double" -> {
                if (hands[active].length != 2 || liability() + wagers[active] > bank)
                    return false;
                wagers[active] *= 2;
                take(active);
                advance();
            }
            case "split" -> {
                int[] h = hands[active];
                if (active % 2 != 0 || wagers[active + 1] != 0 || h.length != 2 || h[0] % 13 != h[1] % 13
                    || liability() + wagers[active] > bank)
                    return false;
                wagers[active + 1] = wagers[active];
                hands[active] = new int[] {h[0], draw()};
                hands[active + 1] = new int[] {h[1], draw()};
                if (h[0] % 13 == 12) {
                    active++;
                    advance();
                } else if (CardRules.blackjackTotal(hands[active]) == 21)
                    advance();
            }
            default -> {
                return false;
            }
        }
        return true;
    }
    void dealerAdvance() {
        if (phase.equals("dealer")) {
            if (CardRules.blackjackTotal(dealer) < 18)
                dealer = append(dealer, draw());
            if (CardRules.blackjackTotal(dealer) >= 18)
                phase = "showdown";
        } else if (phase.equals("showdown")) {
            int house = CardRules.blackjackTotal(dealer);
            boolean dealerNatural = dealer.length == 2 && house == 21;
            for (int i = 0; i < hands.length; i++)
                if (wagers[i] > 0) {
                    int score = CardRules.blackjackTotal(hands[i]);
                    long bet = wagers[i], won = 0;
                    if (natural(i))
                        won = dealerNatural ? bet : bet * 2 + bet / 2;
                    else if (!dealerNatural && score <= 21)
                        won = house > 21 || score > house ? bet * 2 : score == house ? bet : 0;
                    payouts[i / 2] += won;
                    bank += bet - won;
                }
            phase = "settled";
        } else
            throw new IllegalArgumentException("Ждём ход игрока");
    }
    NbtCompound toNbt() {
        NbtCompound t = new NbtCompound();
        t.putLong("Bank", bank);
        t.putIntArray("Deck", deck);
        t.putInt("Drawn", drawn);
        t.putIntArray("Dealer", dealer);
        t.putInt("Active", active);
        t.putString("Phase", phase);
        t.putLongArray("Wagers", wagers);
        t.putLongArray("Payouts", payouts);
        for (int i = 0; i < hands.length; i++)
            t.putIntArray("Hand" + i, hands[i]);
        return t;
    }
    static BlackjackHand fromNbt(NbtCompound t) {
        BlackjackHand h = new BlackjackHand(t.getIntArray("Deck"), t.getLong("Bank"));
        h.drawn = t.getInt("Drawn");
        h.dealer = t.getIntArray("Dealer");
        h.active = t.getInt("Active");
        h.phase = t.getString("Phase");
        System.arraycopy(t.getLongArray("Wagers"), 0, h.wagers, 0, h.wagers.length);
        System.arraycopy(t.getLongArray("Payouts"), 0, h.payouts, 0, h.payouts.length);
        for (int i = 0; i < h.hands.length; i++)
            h.hands[i] = t.getIntArray("Hand" + i);
        return h;
    }
}
