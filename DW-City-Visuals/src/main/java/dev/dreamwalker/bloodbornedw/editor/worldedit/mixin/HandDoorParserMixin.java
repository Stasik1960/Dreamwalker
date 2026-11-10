package dev.dreamwalker.bloodbornedw.editor.worldedit.mixin;

import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.world.block.BaseBlock;
import dev.dreamwalker.bloodbornedw.editor.worldedit.HandDoorPlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.dreamwalker.bloodbornedw.compat.worldedit.WorldEditBridge$NumericBlocks", remap = false)
abstract class HandDoorParserMixin {
    @Inject(method = "parseFromInput(Ljava/lang/String;Lcom/sk89q/worldedit/extension/input/ParserContext;)Lcom/sk89q/worldedit/world/block/BaseBlock;",
        at = @At("RETURN"), cancellable = true)
    private void dw$markHandDoor(String input, ParserContext context, CallbackInfoReturnable<BaseBlock> cir) {
        if (!"hand".equalsIgnoreCase(input) && !"offhand".equalsIgnoreCase(input)) return;
        BaseBlock block = cir.getReturnValue();
        if (block != null && HandDoorPlacement.ID.equals(block.getBlockType().getId())) {
            cir.setReturnValue(new HandDoorPlacement.Marker(block));
        }
    }
}
