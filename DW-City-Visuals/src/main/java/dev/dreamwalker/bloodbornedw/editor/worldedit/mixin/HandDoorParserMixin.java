package dev.dreamwalker.bloodbornedw.editor.worldedit.mixin;

import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import dev.dreamwalker.bloodbornedw.editor.worldedit.HandDoorPlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.sk89q.worldedit.extension.factory.parser.pattern.SingleBlockPatternParser", remap = false)
abstract class HandDoorParserMixin {
    @Inject(method = "parseFromInput(Ljava/lang/String;Lcom/sk89q/worldedit/extension/input/ParserContext;)Lcom/sk89q/worldedit/function/pattern/Pattern;",
        at = @At("RETURN"), cancellable = true)
    private void dw$markHandDoor(String input, ParserContext context, CallbackInfoReturnable<Pattern> cir) {
        if (!"hand".equalsIgnoreCase(input) && !"offhand".equalsIgnoreCase(input)) return;
        if (cir.getReturnValue() instanceof BlockStateHolder<?> block
                && HandDoorPlacement.ID.equals(block.getBlockType().getId())) {
            cir.setReturnValue(new HandDoorPlacement.Marker(block.toBaseBlock()));
        }
    }
}
