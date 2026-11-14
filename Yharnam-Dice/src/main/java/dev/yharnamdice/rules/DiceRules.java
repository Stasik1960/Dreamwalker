package dev.yharnamdice.rules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Score calculators shared by the server match flow and standalone verification. */
public final class DiceRules {
    private static final String[] HAND_NAMES = {
            "Старшая кость", "Пара", "Две пары", "Тройка", "Стрит 1–5",
            "Стрит 2–6", "Фулл-хаус", "Каре", "Пять одинаковых"
    };

    private DiceRules() {}

    public record PokerHand(int category, List<Integer> tieBreakers) {
        public String label() { return HAND_NAMES[category]; }

        public int rank() {
            int result = category;
            for (int i = 0; i < 5; i++) {
                result *= 7;
                if (i < tieBreakers.size()) result += tieBreakers.get(i);
            }
            return result;
        }
    }

    public static PokerHand pokerHand(List<Integer> dice) {
        if (dice.size() != 5) throw new IllegalArgumentException("poker requires five dice");
        int[] counts = counts(dice, 5);
        List<Integer> groups = new ArrayList<>();
        for (int face = 1; face <= 6; face++) if (counts[face] != 0) groups.add(face);
        groups.sort(Comparator.<Integer>comparingInt(face -> -counts[face]).thenComparing(Comparator.reverseOrder()));
        int kind = counts[groups.get(0)];
        int category = switch (kind) {
            case 5 -> 8;
            case 4 -> 7;
            case 3 -> groups.size() == 2 ? 6 : 3;
            case 2 -> groups.size() == 3 ? 2 : 1;
            default -> counts[6] == 0 ? 4 : counts[1] == 0 ? 5 : 0;
        };
        return new PokerHand(category, List.copyOf(groups));
    }

    public static int pokerRank(List<Integer> dice) { return pokerHand(dice).rank(); }

    public static int farklePoints(List<Integer> dice) {
        return Math.max(0, exactScore(counts(dice, 6)));
    }

    private static int exactScore(int[] counts) {
        int first = 1;
        while (first <= 6 && counts[first] == 0) first++;
        if (first > 6) return 0;

        int score = -1;
        for (int number = 3; number <= counts[first]; number++) {
            int base = first == 1 ? 1000 : first * 100;
            counts[first] -= number;
            int tail = exactScore(counts);
            counts[first] += number;
            if (tail >= 0) score = Math.max(score, (base << (number - 3)) + tail);
        }
        if (first == 1 || first == 5) {
            counts[first]--;
            int tail = exactScore(counts);
            counts[first]++;
            if (tail >= 0) score = Math.max(score, (first == 1 ? 100 : 50) + tail);
        }
        if (first == 1) {
            score = Math.max(score, straight(counts, 1, 6, 1500));
            score = Math.max(score, straight(counts, 1, 5, 500));
        }
        if (first == 2) score = Math.max(score, straight(counts, 2, 6, 750));
        return score;
    }

    private static int straight(int[] counts, int from, int to, int value) {
        for (int i = from; i <= to; i++) if (counts[i] == 0) return -1;
        for (int i = from; i <= to; i++) counts[i]--;
        int tail = exactScore(counts);
        for (int i = from; i <= to; i++) counts[i]++;
        return tail < 0 ? -1 : value + tail;
    }

    public static boolean canScore(List<Integer> dice) {
        int[] counts = counts(dice, 6);
        return counts[1] > 0 || counts[5] > 0 || Arrays.stream(counts).anyMatch(count -> count >= 3);
    }

    private static int[] counts(List<Integer> dice, int limit) {
        if (dice.size() > limit) throw new IllegalArgumentException("too many dice");
        int[] counts = new int[7];
        for (int value : dice) {
            if (value < 1 || value > 6) throw new IllegalArgumentException("die faces are 1–6");
            counts[value]++;
        }
        return counts;
    }
}
