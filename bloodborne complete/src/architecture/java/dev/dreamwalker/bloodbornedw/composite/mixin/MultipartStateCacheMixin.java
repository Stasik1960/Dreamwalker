package dev.dreamwalker.bloodbornedw.composite.mixin;
import dev.dreamwalker.bloodbornedw.composite.*;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.client.render.model.MultipartBakedModel;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.state.StateManager;
import java.util.function.Predicate;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.*;
@Mixin(MultipartBakedModel.class)
public abstract class MultipartStateCacheMixin implements MultipartStateCacheData,MultipartBuilderData {
    @Shadow @Final @Mutable private Map<BlockState,BitSet> stateCache;
    @Shadow @Final private List<Pair<Predicate<BlockState>,BakedModel>> components;
    @Override public void dreamwalker$ownedStates(StateManager<Block,BlockState> manager){stateCache=new MultipartModelCoreCache.OwnedStates(manager);}
    @Override public Map<BlockState,BitSet> dreamwalker$stateBits(){return stateCache;}
    @Override public List<Pair<Predicate<BlockState>,BakedModel>> dreamwalker$components(){return components;}
}
