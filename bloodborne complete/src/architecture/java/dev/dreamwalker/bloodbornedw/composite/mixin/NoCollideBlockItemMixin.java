package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.architecture.NoCollidePlacement;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native V12 items retain their placement code; only an explicit held-stack lease adds scope. */
@Mixin(BlockItem.class)
public abstract class NoCollideBlockItemMixin {
    @Unique private final ThreadLocal<java.util.ArrayDeque<Boolean>> dreamwalker$leases=ThreadLocal.withInitial(java.util.ArrayDeque::new);
    @Inject(method="place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;",at=@At("HEAD"))
    private void dreamwalker$beginNoCollide(ItemPlacementContext context,CallbackInfoReturnable<ActionResult> result){dreamwalker$leases.get().push(context.getHand()==net.minecraft.util.Hand.MAIN_HAND&&NoCollidePlacement.enter(context.getPlayer()));}
    @Inject(method="place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;",at=@At("RETURN"))
    private void dreamwalker$endNoCollide(ItemPlacementContext context,CallbackInfoReturnable<ActionResult> result){NoCollidePlacement.exit(context.getPlayer(),dreamwalker$leases.get().pop(),result.getReturnValue().isAccepted());}
}
