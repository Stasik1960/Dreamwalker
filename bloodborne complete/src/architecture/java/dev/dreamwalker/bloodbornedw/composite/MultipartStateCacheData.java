package dev.dreamwalker.bloodbornedw.composite;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.state.StateManager;
/** Only explicitly shared owned cores opt into the safe lazy state map. */
public interface MultipartStateCacheData {
    void dreamwalker$ownedStates(StateManager<Block,BlockState> manager);
    Map<BlockState,BitSet> dreamwalker$stateBits();
}
