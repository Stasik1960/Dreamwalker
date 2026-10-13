package dev.dreamwalker.bloodbornedw.composite.mixin;
import dev.dreamwalker.bloodbornedw.composite.*;
import java.util.function.Function;
import net.minecraft.block.*;
import net.minecraft.client.render.model.*;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.state.StateManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(MultipartUnbakedModel.class)
public abstract class MultipartModelCoreMixin {
    @Shadow @Final private StateManager<Block,BlockState> stateFactory;
    @Unique private volatile MultipartModelCoreCache.Entry dreamwalker$core;
    @Redirect(method="bake",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/model/MultipartBakedModel$Builder;build()Lnet/minecraft/client/render/model/BakedModel;"))
    private BakedModel dreamwalker$exactGraph(MultipartBakedModel.Builder builder,Baker baker,Function<SpriteIdentifier,Sprite> sprites,ModelBakeSettings settings,Identifier currentId){
        if(!MultipartPredicateCache.owned(stateFactory))return builder.build();
        // Every native child bake/predicate call has already completed. The
        // outer Fabric modifyAfterBake call still runs for every current ID.
        var graph=((MultipartBuilderData)builder).dreamwalker$components();var entry=dreamwalker$core;
        if(MultipartModelCoreCache.same(entry,graph,settings))return MultipartModelCoreCache.reuse(entry);
        BakedModel result=builder.build();if(result!=null)dreamwalker$core=MultipartModelCoreCache.entry(graph,settings,result,stateFactory);return result;
    }
}
