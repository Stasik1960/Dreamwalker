package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.architecture.NoCollidePlacement;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native V12 items retain their placement code; only an explicit held-stack lease adds scope. */
@Mixin(BlockItem.class)
public abstract class NoCollideBlockItemMixin {
    @WrapMethod(method="place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;")
    private ActionResult dreamwalker$scopedPlacement(ItemPlacementContext original,Operation<ActionResult> operation){
        ItemPlacementContext context=NoCollidePlacement.surfaceContext(original);
        boolean lease=context.getHand()==net.minecraft.util.Hand.MAIN_HAND&&NoCollidePlacement.enter(context.getPlayer());
        boolean accepted=false;
        try{ActionResult result=operation.call(context);accepted=result.isAccepted();return result;}
        finally{NoCollidePlacement.exit(context.getPlayer(),lease,accepted);}
    }
}
