package dev.dreamwalker.bloodbornedw.composite;

import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;

/** The retained gothic frame has binary alpha and no physical glass pane. */
public final class RetainedFrameState {
    private RetainedFrameState() {}

    public static boolean matches(BlockState state) {
        return state.getBlock() instanceof SourceFallbackCarrierV1271
            && SourceFallbackCarrierV1271.TRANSLUCENT_ID.equals(Registries.BLOCK.getId(state.getBlock()))
            && state.get(SourceFallbackCarrierV1271.SOURCE) == 63
            && state.get(SourceFallbackCarrierV1271.SOURCE_BANK) == 0;
    }
}
