package dev.dreamwalker.brickreuse;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
public final class BrickReuseMetrics {
    private static final LongAdder PROVEN = new LongAdder();
    private static final LongAdder HITS = new LongAdder();
    private static final LongAdder CHILD_KEYS = new LongAdder();
    private static final LongAdder PARENTS = new LongAdder(), PARENT_HITS = new LongAdder();
    public static void proven(int childKeys) { PROVEN.increment(); CHILD_KEYS.add(childKeys); }
    public static void hit() { HITS.increment(); }
    public static void parentFirst() { PARENTS.increment(); }
    public static void parentHit() { PARENT_HITS.increment(); }
    public static Map<String,Long> metrics() {
        return Map.of("provenGraphs", PROVEN.sum(), "earlyHits", HITS.sum(), "provedUniqueChildren", CHILD_KEYS.sum(), "parentFirst", PARENTS.sum(), "parentHits", PARENT_HITS.sum());
    }
}

