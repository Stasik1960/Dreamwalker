package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallItem;
import dev.dreamwalker.bloodbornedw.composite.PlacementWallArt;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = PrototypeWallItem.class, remap = false)
public abstract class WallPlacementRotationMixin {
    @WrapMethod(method = "method_7712(Lnet/minecraft/class_1750;)Lnet/minecraft/class_1269;", remap = false)
    private ActionResult dw$placementAngle(ItemPlacementContext context, Operation<ActionResult> original) {
        ItemStack stack = context.getStack();
        ItemStack working = PlacementWallArt.prepare(context);
        if (working == null) return ActionResult.FAIL;
        int count = working.getCount();
        ItemPlacementContext prepared = new ItemPlacementContext(context) {
            @Override public ItemStack getStack() { return working; }
        };
        ActionResult result = original.call(prepared);
        if (result.isAccepted()) stack.decrement(Math.max(0, count - working.getCount()));
        return result;
    }
}
