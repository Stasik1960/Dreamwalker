package dev.dreamwalker.bloodbornedw.composite;

import java.util.Map;
import net.minecraft.util.math.ChunkSectionPos;

/** Origin and sparse metadata captured with Sodium's cloned sections. */
public interface RetainedFrameSodiumSnapshot {
    Map<Integer, Integer> dw$frameSection();
    ChunkSectionPos dw$frameOrigin();
}
