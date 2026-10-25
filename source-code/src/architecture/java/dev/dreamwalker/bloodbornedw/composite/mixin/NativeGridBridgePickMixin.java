package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.*;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Aimed background picking preserves the retained functional owner's item. */
@Mixin({CompositeRootBlock.class,CompositeCellBlock.class})
public abstract class NativeGridBridgePickMixin {
    @Inject(method="getPickStack",at=@At("HEAD"),cancellable=true)
    private void dw$pickBackground(BlockView view,BlockPos pos,BlockState state,CallbackInfoReturnable<ItemStack> result){
        int index=NativeGridBridge.base(view,pos);var player=MinecraftClient.getInstance().player;
        if(index>=0&&view instanceof World world&&NativeGridBridge.backgroundHit(view,pos,player)&&CompositeRuntime.target(world,pos,player)==null)
            result.setReturnValue(NativeGridRuntime.item(index));
    }
}
