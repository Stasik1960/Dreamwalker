package dev.dreamwalker.bloodbornedw.tool.mixin;

import dev.dreamwalker.bloodbornedw.tool.BbToolItem;
import dev.dreamwalker.bloodbornedw.tool.DebugToolModes;
import java.util.List;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BbToolItem.class)
abstract class BbHorizontalModeMixin {
    @Inject(method = "cycle(Lnet/minecraft/class_3222;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void dw$cycleTool(ServerPlayerEntity player, CallbackInfo ci) {
        DebugToolModes.cycle(player);
        ci.cancel();
    }

    @Inject(method = "mode(Lnet/minecraft/class_1799;)Ldev/dreamwalker/bloodbornedw/tool/BbToolItem$Mode;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void dw$legacyMode(ItemStack stack, CallbackInfoReturnable<BbToolItem.Mode> cir) {
        if (!(stack.getItem() instanceof BbToolItem)) return;
        var legacy = DebugToolModes.action(stack).legacy;
        cir.setReturnValue(legacy == null ? BbToolItem.Mode.UP : legacy);
    }

    @Inject(method = "getName(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/text/Text;",
            at = @At("HEAD"), cancellable = true)
    private void dw$toolName(ItemStack stack, CallbackInfoReturnable<Text> cir) {
        cir.setReturnValue(Text.literal(DebugToolModes.tool(stack).title + " · " + DebugToolModes.action(stack).title));
    }

    @Inject(method = "appendTooltip", at = @At("HEAD"), cancellable = true)
    private void dw$toolTooltip(ItemStack stack, World world, List<Text> tooltip,
                                TooltipContext context, CallbackInfo ci) {
        tooltip.add(Text.literal("ПКМ — следующий режим. ЛКМ — применить к цели."));
        if (DebugToolModes.tool(stack) == DebugToolModes.Tool.BLOCK) {
            tooltip.add(Text.literal("Привязка консольных команд доступна OP4."));
        }
        ci.cancel();
    }
}
