package dev.dreamwalker.bloodbornedw.composite.mixin;
import dev.dreamwalker.bloodbornedw.composite.*;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.client.render.model.MultipartBakedModel;
import net.minecraft.state.StateManager;
import org.spongepowered.asm.mixin.*;
@Mixin(MultipartBakedModel.class)
public abstract class MultipartStateCacheMixin implements MultipartStateCacheData {
    @Shadow @Final @Mutable private Map<BlockState,BitSet> stateCache;
    @Override public void dreamwalker$ownedStates(StateManager<Block,BlockState> manager){stateCache=new MultipartModelCoreCache.OwnedStates(manager);}
    @Override public Map<BlockState,BitSet> dreamwalker$stateBits(){return stateCache;}
}
