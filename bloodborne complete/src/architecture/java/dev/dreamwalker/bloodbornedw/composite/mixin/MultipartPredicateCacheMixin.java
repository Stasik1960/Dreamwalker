package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.MultipartPredicateCache;
import java.util.function.Predicate;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.json.MultipartModelComponent;
import net.minecraft.client.render.model.json.MultipartModelSelector;
import net.minecraft.state.StateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** ModelLoader repeatedly requests this pure predicate for every Cartesian
 * state. Redirect the one compilation call, without callback allocation on
 * each cache hit or bypassing any model bake/modify callback. */
@Mixin(MultipartModelComponent.class)
public abstract class MultipartPredicateCacheMixin {
    @Unique private volatile MultipartPredicateCache.Entry dreamwalker$predicate;
    @Redirect(method="getPredicate",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/model/json/MultipartModelSelector;getPredicate(Lnet/minecraft/state/StateManager;)Ljava/util/function/Predicate;"))
    private Predicate<BlockState> dreamwalker$compileOnce(MultipartModelSelector selector,StateManager<Block,BlockState> manager) {
        var current=dreamwalker$predicate;
        if(current!=null&&current.manager()==manager)return current.predicate();
        Predicate<BlockState> result=selector.getPredicate(manager);
        if(MultipartPredicateCache.owned(manager))dreamwalker$predicate=MultipartPredicateCache.entry(manager,result);
        return result;
    }
}
