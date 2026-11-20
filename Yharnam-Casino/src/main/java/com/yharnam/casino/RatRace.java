package com.yharnam.casino;

import java.util.Random;

/** Each clockwork motor changes speed eight times. Arrival is the crossing of the painted finish line. */
public final class RatRace {
    public static final int LANES = 4;
    private static final int STAGES = 8;
    private final double[][] arrivals = new double[LANES][STAGES+1];
    private final int winner;
    private final int duration;
    public RatRace(long seed) {
        Random random = new Random(seed);
        int first = 0;
        double last = 0;
        for (int lane=0; lane<LANES; lane++) {
            for (int stage=1; stage<=STAGES; stage++)
                arrivals[lane][stage] = arrivals[lane][stage-1] + 10 + random.nextDouble()*17;
            if (arrivals[lane][STAGES] < arrivals[first][STAGES]) first = lane;
            last = Math.max(last, arrivals[lane][STAGES]);
        }
        winner = first;
        duration = (int)Math.ceil(last);
    }
    public double progress(int lane, double tick) {
        double[] times = arrivals[lane];
        if (tick <= 0) return 0;
        for (int s=1; s<=STAGES; s++)
            if (tick < times[s]) return (s-1 + (tick-times[s-1])/(times[s]-times[s-1]))/STAGES;
        return 1;
    }
    public int winner() { return winner; }
    public int duration() { return duration; }
}
