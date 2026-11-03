package dev.dreamwalker.editorqa.mixin;
import dev.dreamwalker.editorqa.EditorModelProbe;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockState;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(targets="dev.dreamwalker.bloodbornedw.visual.CityFacadesClient",remap=false)
public abstract class EditorTerrainProbeMixin {
 @Inject(method="terrainModel",at=@At("HEAD"),remap=false)
 private static void head(BakedModel model,BlockPos pos,BlockState state,BlockRenderView world,CallbackInfoReturnable<BakedModel> ci){EditorModelProbe.terrain("terrain_head",pos,world,model,state);}
 @Inject(method="terrainModel",at=@At("RETURN"),remap=false)
 private static void tail(BakedModel model,BlockPos pos,BlockState state,BlockRenderView world,CallbackInfoReturnable<BakedModel> ci){EditorModelProbe.terrain("terrain_return",pos,world,ci.getReturnValue(),state);}
}
