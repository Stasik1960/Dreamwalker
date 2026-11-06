package dev.dreamwalker.bloodbornedw.tool.mixin;

import dev.dreamwalker.bloodbornedw.tool.BbHorizontalMoves;
import dev.dreamwalker.bloodbornedw.DreamwalkerBb;
import dev.dreamwalker.bloodbornedw.tool.BbToolService;
import dev.dreamwalker.bloodbornedw.tool.ToolTarget;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BbToolService.class, remap = false)
abstract class BbHorizontalActionMixin {
    @Inject(method = "apply(Lnet/minecraft/class_3222;Ldev/dreamwalker/bloodbornedw/tool/ToolTarget;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void dw$shiftHorizontal(ServerPlayerEntity player, ToolTarget target, CallbackInfo ci) {
        int mode = BbHorizontalMoves.mode(player.getMainHandStack());
        if (mode == 0) return;
        boolean shifted;
        try {
            shifted = BbHorizontalMoves.shift(player, target, mode);
        } catch (RuntimeException failure) {
            DreamwalkerBb.LOG.warn("BB horizontal shift failed", failure);
            shifted = false;
        }
        player.sendMessage(Text.literal(shifted ? "BB · " + BbHorizontalMoves.title(mode)
                : "BB · Эта конструкция или клетка назначения не допускает сдвиг."), true);
        ci.cancel();
    }
}
