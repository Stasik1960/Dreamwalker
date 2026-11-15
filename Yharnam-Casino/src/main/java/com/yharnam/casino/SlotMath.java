package com.yharnam.casino;

import net.minecraft.util.math.random.Random;

final class SlotMath {
    static final int SYMBOLS = 8;
    private static final int[] TRIPLE_PAYOUTS = {80, 50, 32, 25, 22, 18, 16, 14};

    private SlotMath() {}

    static int[] draw(Random random) {
        return new int[]{random.nextInt(SYMBOLS), random.nextInt(SYMBOLS), random.nextInt(SYMBOLS)};
    }

    static int multiplier(int left, int middle, int right) {
        if (left == middle && middle == right) {
            return tripleMultiplier(left);
        }
        if (left == middle || left == right) {
            return pairMultiplier(left);
        }
        if (middle == right) {
            return pairMultiplier(middle);
        }
        return 0;
    }

    static int tripleMultiplier(int symbol) {
        return TRIPLE_PAYOUTS[symbol];
    }

    static int pairMultiplier(int symbol) {
        return symbol < 3 ? 2 : 1;
    }

    static boolean winsTicket(int left, int middle, int right) {
        return left == 0 && middle == 0 && right == 0;
    }
}
