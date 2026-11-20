package com.yharnam.casino;

import java.security.SecureRandom;
import java.util.stream.IntStream;

public final class CardRules {
    private static final SecureRandom RANDOM = new SecureRandom();
    static int[] shuffledDeck() {
        int[] cards = IntStream.range(0, 52).toArray();
        for (int i = cards.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1), tmp = cards[i];
            cards[i] = cards[j];
            cards[j] = tmp;
        }
        return cards;
    }
    public static int blackjackTotal(int[] cards) {
        int total = 0;
        boolean ace = false;
        for (int card : cards) {
            int rank = card % 13;
            ace |= rank == 12;
            total += rank == 12 ? 1 : Math.min(10, rank + 2);
        }
        return ace && total <= 11 ? total + 10 : total;
    }
}
