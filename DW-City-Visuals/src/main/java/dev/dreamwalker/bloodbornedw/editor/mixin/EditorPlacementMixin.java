package dev.dreamwalker.bloodbornedw.editor.mixin;
import dev.dreamwalker.bloodbornedw.editor.CityEditorServer;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.util.ActionResult;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockState;
import dev.dreamwalker.bloodbornedw.composite.NativeVisuals;
@Mixin(ServerPlayerInteractionManager.class)
abstract class EditorPlacementMixin {
    @WrapMethod(method="interactBlock")
    private ActionResult dw$scope(ServerPlayerEntity player,World world,ItemStack stack,Hand hand,BlockHitResult hit,Operation<ActionResult> original){
        BlockPos pos=null;BlockState before=null;Object identity=null;int nativeIndex=-1;
        if(world instanceof ServerWorld serverWorld&&stack.getItem() instanceof BlockItem){
            pos=new ItemPlacementContext(player,hand,stack,hit).getBlockPos().toImmutable();
            before=world.getBlockState(pos);nativeIndex=NativeVisuals.index(world,pos);identity=CityEditorServer.placementIdentity(serverWorld,pos);
        }
        boolean committed=false;CityEditorServer.beginPlacement();
        try{
            ActionResult result=original.call(player,world,stack,hand,hit);committed=result.isAccepted();
            if(committed&&pos!=null&&world instanceof ServerWorld serverWorld&&
                (world.getBlockState(pos)!=before||NativeVisuals.index(world,pos)!=nativeIndex||!java.util.Objects.equals(identity,CityEditorServer.placementIdentity(serverWorld,pos))))CityEditorServer.placed(serverWorld,pos);
            return result;
        }finally{CityEditorServer.endPlacement(committed);}
    }
}
