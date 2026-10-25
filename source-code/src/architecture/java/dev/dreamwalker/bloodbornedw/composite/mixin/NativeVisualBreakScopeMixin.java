package dev.dreamwalker.bloodbornedw.composite.mixin;
import dev.dreamwalker.bloodbornedw.composite.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.network.*;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.*;
/** Vanilla afterBreak requests drops after removing the physical block. Keep
 * its finite visual ID only for the synchronous break call, including errors. */
@Mixin(ServerPlayerInteractionManager.class)
public abstract class NativeVisualBreakScopeMixin {
    @Shadow protected ServerPlayerEntity player;
    @WrapMethod(method="tryBreakBlock")
    private boolean dw$visualDropScope(BlockPos pos,Operation<Boolean> original){
        if(!(player.getWorld().getBlockState(pos).getBlock() instanceof NativePhysicsBlock))return original.call(pos);
        var scope=NativeVisuals.breakScope(player.getWorld(),pos);try{return original.call(pos);}finally{try{scope.close();}catch(Exception failure){throw new IllegalStateException("Visual break scope",failure);}}
    }
}
