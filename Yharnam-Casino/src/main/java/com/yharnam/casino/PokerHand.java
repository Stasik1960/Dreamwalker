package com.yharnam.casino;

import java.util.Arrays;
import net.minecraft.nbt.NbtCompound;

/** One server-authoritative no-limit hold'em hand. All chip amounts include the player's own stake. */
final class PokerHand {
    private final long[] stack, paid, street, lastActionPrice;
    private final int[] hole, deck;
    private final boolean[] live;
    private int button, turn, streetIndex, drawn, boardCount;
    private int[] board = new int[5];
    private long price, minRaise;
    private String phase;

    private PokerHand(long[] stacks, int[] cards) {
        stack = stacks.clone();
        deck = cards.clone();
        int n = stack.length;
        paid = new long[n];
        street = new long[n];
        lastActionPrice = new long[n];
        hole = new int[2 * n];
        Arrays.fill(hole, -1);
        live = new boolean[n];
    }

    static PokerHand start(long[] stacks, int requestedButton, int[] shuffledDeck) {
        PokerHand h = new PokerHand(stacks, shuffledDeck);
        int players = 0;
        for (int i = 0; i < stacks.length; i++) {
            h.live[i] = stacks[i] > 0;
            if (h.live[i])
                players++;
        }
        if (players < 2 || players > 8 || !validDeck(shuffledDeck))
            throw new IllegalArgumentException("Нужны колода и минимум два игрока с фишками");
        h.button = h.next(Math.floorMod(requestedButton - 1, stacks.length), false);
        int small = players == 2 ? h.button : h.next(h.button, false);
        int big = h.next(small, false);
        for (int pass = 0; pass < 2; pass++) {
            int seat = h.button;
            for (int k = 0; k < players; k++) {
                seat = h.next(seat, false);
                h.hole[seat * 2 + pass] = h.deck[h.drawn++];
            }
        }
        h.place(small, Math.min(1, h.stack[small]));
        h.place(big, Math.min(2, h.stack[big]));
        h.minRaise = 2;
        h.price = 2;
        h.phase = "preflop";
        Arrays.fill(h.lastActionPrice, -1);
        h.advanceTurn(big);
        return h;
    }

    private static boolean validDeck(int[] deck) {
        if (deck.length != 52)
            return false;
        boolean[] seen = new boolean[52];
        for (int c : deck) {
            if (c < 0 || c >= 52 || seen[c])
                return false;
            seen[c] = true;
        }
        return true;
    }

    private int next(int from, boolean needsAction) {
        for (int k = 1; k <= stack.length; k++) {
            int i = (from + k) % stack.length;
            if (live[i] && (!needsAction || (stack[i] > 0 && (lastActionPrice[i] < 0 || street[i] < price))))
                return i;
        }
        return -1;
    }
    private void place(int seat, long amount) {
        stack[seat] -= amount;
        paid[seat] += amount;
        street[seat] += amount;
    }
    private int liveCount(boolean withMoney) {
        int count = 0;
        for (int i = 0; i < stack.length; i++)
            if (live[i] && (!withMoney || stack[i] > 0))
                count++;
        return count;
    }
    private void advanceTurn(int from) {
        turn = next(from, true);
        if (liveCount(false) == 1 || (turn >= 0 && liveCount(true) == 1 && street[turn] >= price))
            turn = -1;
        if (turn < 0)
            phase = liveCount(false) == 1 || streetIndex == 3 ? "showdown" : "deal";
    }

    void act(int seat, long raiseBy, boolean fold) {
        if (turn < 0 || turn != seat)
            throw new IllegalArgumentException("Сейчас ход другого игрока или крупье");
        if (raiseBy < 0)
            throw new IllegalArgumentException("Некорректная ставка");
        if (fold)
            live[seat] = false;
        else {
            long call = Math.max(0, price - street[seat]);
            if (raiseBy > 0 && stack[seat] > call) {
                if (liveCount(true) < 2 || (lastActionPrice[seat] >= 0 && price - lastActionPrice[seat] < minRaise))
                    throw new IllegalArgumentException("Можно только уравнять или сбросить карты");
                long increase = Math.min(stack[seat] - call, raiseBy);
                if (increase < minRaise && increase < stack[seat] - call)
                    throw new IllegalArgumentException("Минимальное повышение: " + minRaise);
                place(seat, call + increase);
                price = street[seat];
                if (increase >= minRaise)
                    minRaise = increase;
            } else
                place(seat, Math.min(stack[seat], call));
            lastActionPrice[seat] = price;
        }
        advanceTurn(seat);
    }

    void dealerAdvance() {
        if (phase.equals("showdown")) {
            settle();
            return;
        }
        if (!phase.equals("deal"))
            throw new IllegalArgumentException("Ждём ход игрока");
        streetIndex++;
        drawn++; // burn one before flop, turn and river
        int count = streetIndex == 1 ? 3 : 1;
        for (int i = 0; i < count; i++)
            board[boardCount++] = deck[drawn++];
        Arrays.fill(street, 0);
        Arrays.fill(lastActionPrice, -1);
        price = 0;
        minRaise = 2;
        phase = switch (streetIndex) {
            case 1 -> "flop";
            case 2 -> "turn";
            default -> "river";
        };
        advanceTurn(button);
    }
    private void settle() {
        long[] ranks = new long[stack.length];
        for (int i = 0; i < stack.length; i++)
            if (live[i] && boardCount == 5)
                ranks[i] = best(hole[2 * i], hole[2 * i + 1], board);
        long previous = 0;
        for (long level : Arrays.stream(paid).distinct().sorted().toArray()) {
            if (level == 0)
                continue;
            long pot = 0, best = -1;
            for (int i = 0; i < paid.length; i++)
                if (paid[i] >= level) {
                    pot += level - previous;
                    if (live[i])
                        best = Math.max(best, ranks[i]);
                }
            // Return any unmatched excess left by a folded sole contributor.
            if (best < 0) {
                for (int i = 0; i < paid.length; i++)
                    if (paid[i] >= level)
                        stack[i] += level - previous;
            } else {
                int winners = 0;
                for (int i = 0; i < paid.length; i++)
                    if (live[i] && paid[i] >= level && ranks[i] == best)
                        winners++;
                long odd = pot % winners;
                for (int k = 1; k <= paid.length; k++) {
                    int i = (button + k) % paid.length;
                    if (live[i] && paid[i] >= level && ranks[i] == best) {
                        stack[i] += pot / winners;
                        if (odd > 0) {
                            stack[i]++;
                            odd--;
                        }
                    }
                }
            }
            previous = level;
        }
        Arrays.fill(paid, 0);
        phase = "settled";
        turn = -1;
    }
    static long best(int first, int second, int[] board) {
        int[] seven = {first, second, board[0], board[1], board[2], board[3], board[4]};
        long best = 0;
        for (int a = 0; a < 6; a++)
            for (int b = a + 1; b < 7; b++) {
                int[] freq = new int[15];
                int mask = 0, suit = -1;
                boolean flush = true;
                for (int i = 0; i < 7; i++)
                    if (i != a && i != b) {
                        int card = seven[i], rank = 2 + card % 13;
                        freq[rank]++;
                        mask |= 1 << rank;
                        if (suit < 0)
                            suit = card / 13;
                        else if (suit != card / 13)
                            flush = false;
                    }
                if ((mask & (1 << 14)) != 0)
                    mask |= 2;
                int straight = 0;
                for (int high = 14; high >= 5; high--)
                    if (((mask >> (high - 4)) & 31) == 31) {
                        straight = high;
                        break;
                    }
                int[] groups = new int[5];
                int n = 0;
                for (int copies = 4; copies >= 1; copies--)
                    for (int rank = 14; rank >= 2; rank--)
                        if (freq[rank] == copies)
                            groups[n++] = rank;
                int top = freq[groups[0]], next = freq[groups[1]];
                int type = flush && straight > 0 ? 8
                    : top == 4                   ? 7
                    : top == 3 && next == 2      ? 6
                    : flush                      ? 5
                    : straight > 0               ? 4
                    : top == 3                   ? 3
                    : top == 2 && next == 2      ? 2
                    : top == 2                   ? 1
                                                 : 0;
                long value = (long) type << 24;
                if (type == 4 || type == 8)
                    value |= (long) straight << 16;
                else
                    for (int k = 0; k < n; k++)
                        value |= (long) groups[k] << (16 - 4 * k);
                best = Math.max(best, value);
            }
        return best;
    }
    int[] board() {
        return Arrays.copyOf(board, boardCount);
    }
    int[] cards(int seat, boolean privately) {
        if (hole[2 * seat] < 0 || !live[seat])
            return new int[0];
        boolean reveal = liveCount(false) > 1 && (phase.equals("settled") || phase.equals("showdown"));
        return privately || reveal ? new int[] {hole[2 * seat], hole[2 * seat + 1]} : new int[] {-1, -1};
    }
    String shownCombination(int seat) {
        if (boardCount != 5 || !live[seat] || liveCount(false) < 2 || !(phase.equals("showdown") || phase.equals("settled")))
            return "";
        int category = (int) (best(hole[seat*2], hole[seat*2+1], board) >>> 24);
        return switch(category) {
            case 8 -> "Стрит-флеш"; case 7 -> "Каре"; case 6 -> "Фулл-хаус"; case 5 -> "Флеш";
            case 4 -> "Стрит"; case 3 -> "Тройка"; case 2 -> "Две пары"; case 1 -> "Пара"; default -> "Старшая карта";
        };
    }
    int turn() {
        return turn;
    }
    int button() {
        return button;
    }
    String phase() {
        return phase;
    }
    long balance(int seat) {
        return stack[seat];
    }
    long committed(int seat) {
        return paid[seat];
    }
    long toCall(int seat) {
        return Math.min(stack[seat], Math.max(0, price - street[seat]));
    }
    NbtCompound toNbt() {
        NbtCompound t = new NbtCompound();
        t.putLongArray("Stack", stack);
        t.putLongArray("Paid", paid);
        t.putLongArray("Street", street);
        t.putLongArray("Acted", lastActionPrice);
        t.putIntArray("Hole", hole);
        t.putIntArray("Deck", deck);
        t.putIntArray("Board", board);
        t.putInt("BoardCount", boardCount);
        t.putInt("Drawn", drawn);
        t.putInt("Button", button);
        t.putInt("Turn", turn);
        t.putInt("Round", streetIndex);
        t.putLong("Price", price);
        t.putLong("MinRaise", minRaise);
        t.putString("Phase", phase);
        byte[] active = new byte[live.length];
        for (int i = 0; i < live.length; i++)
            active[i] = (byte) (live[i] ? 1 : 0);
        t.putByteArray("Live", active);
        return t;
    }
    static PokerHand fromNbt(NbtCompound t) {
        PokerHand h = new PokerHand(t.getLongArray("Stack"), t.getIntArray("Deck"));
        int n = h.stack.length;
        System.arraycopy(t.getLongArray("Paid"), 0, h.paid, 0, n);
        System.arraycopy(t.getLongArray("Street"), 0, h.street, 0, n);
        System.arraycopy(t.getLongArray("Acted"), 0, h.lastActionPrice, 0, n);
        System.arraycopy(t.getIntArray("Hole"), 0, h.hole, 0, 2 * n);
        byte[] active = t.getByteArray("Live");
        for (int i = 0; i < n; i++)
            h.live[i] = active[i] != 0;
        h.board = t.getIntArray("Board");
        h.boardCount = t.getInt("BoardCount");
        h.drawn = t.getInt("Drawn");
        h.button = t.getInt("Button");
        h.turn = t.getInt("Turn");
        h.streetIndex = t.getInt("Round");
        h.price = t.getLong("Price");
        h.minRaise = t.getLong("MinRaise");
        h.phase = t.getString("Phase");
        return h;
    }
}
