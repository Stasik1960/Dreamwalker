package dev.dreamwalker.bloodbornedw.tool.mixin;

import dev.dreamwalker.bloodbornedw.tool.BbToolService;
import dev.dreamwalker.bloodbornedw.tool.DebugToolModes;
import dev.dreamwalker.bloodbornedw.tool.ToolTarget;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BbToolService.class, remap = false)
abstract class BbHorizontalActionMixin {
    @Inject(method = "initialize", at = @At(value = "FIELD",
            target = "Ldev/dreamwalker/bloodbornedw/tool/BbToolService;ITEM:Ldev/dreamwalker/bloodbornedw/tool/BbToolItem;",
            opcode = Opcodes.PUTSTATIC, shift = At.Shift.AFTER))
    private static void dw$registerTools(CallbackInfo ci) {
        DebugToolModes.register();
    }

    @Inject(method = "apply(Lnet/minecraft/class_3222;Ldev/dreamwalker/bloodbornedw/tool/ToolTarget;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void dw$applyTool(ServerPlayerEntity player, ToolTarget target, CallbackInfo ci) {
        if (DebugToolModes.apply(player, target)) ci.cancel();
    }

    @Inject(method = "lambda$initialize$5", at = @At("HEAD"), cancellable = true)
    private static void dw$applyCustomNpc(PlayerEntity player, World world, Hand hand, Entity entity,
            EntityHitResult hit, CallbackInfoReturnable<ActionResult> cir) {
        if (hand == Hand.MAIN_HAND && player instanceof ServerPlayerEntity server
                && DebugToolModes.applyCustomNpc(server, entity)) cir.setReturnValue(ActionResult.SUCCESS);
    }
}
