package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.*;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockItem.class)
public abstract class RetainedFrameBlockItemMixin {
    @WrapMethod(method = "method_7712(Lnet/minecraft/class_1750;)Lnet/minecraft/class_1269;", remap = false)
    private ActionResult dw$scope(ItemPlacementContext context, Operation<ActionResult> original) {
        RetainedFramePlacement.enter();
        try { return original.call(context); } finally { RetainedFramePlacement.exit(); }
    }
}
