package dev.dreamwalker.bloodbornedw.tool.mixin;

import dev.dreamwalker.bloodbornedw.tool.BbHorizontalMoves;
import dev.dreamwalker.bloodbornedw.tool.BbToolItem;
import dev.dreamwalker.bloodbornedw.tool.BbToolService;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BbToolItem.class)
abstract class BbHorizontalModeMixin {
    @Inject(method = "cycle(Lnet/minecraft/class_3222;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void dw$cycleHorizontal(ServerPlayerEntity player, CallbackInfo ci) {
        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof BbToolItem) || BbToolItem.mode(stack) != BbToolItem.Mode.ROTATE
                || !BbToolService.canEdit(player)) return;
        int mode = BbHorizontalMoves.mode(stack);
        if (mode == BbHorizontalMoves.RIGHT) {
            stack.removeSubNbt(BbHorizontalMoves.MODE_KEY);
            return;
        }
        int next = mode == 0 ? BbHorizontalMoves.LEFT : BbHorizontalMoves.RIGHT;
        stack.getOrCreateNbt().putInt(BbHorizontalMoves.MODE_KEY, next);
        BbToolService.clearSelection(player);
        player.sendMessage(Text.literal("BB · " + BbHorizontalMoves.title(next)), true);
        ci.cancel();
    }

    @Inject(method = "getName(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/text/Text;",
            at = @At("RETURN"), cancellable = true)
    private void dw$horizontalName(ItemStack stack, CallbackInfoReturnable<Text> cir) {
        int mode = BbHorizontalMoves.mode(stack);
        if (mode != 0) cir.setReturnValue(Text.literal("Инструмент BB · " + BbHorizontalMoves.title(mode)));
    }
}
