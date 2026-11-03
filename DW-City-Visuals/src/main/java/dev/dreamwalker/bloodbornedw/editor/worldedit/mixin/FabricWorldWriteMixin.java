package dev.dreamwalker.bloodbornedw.editor.worldedit.mixin;

import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import dev.dreamwalker.bloodbornedw.editor.worldedit.AppearanceTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Keep clipboard/history metadata out of native block entity writes. */
@Pseudo
@Mixin(targets = "com.sk89q.worldedit.fabric.FabricWorld", remap = false)
abstract class FabricWorldWriteMixin {
    @ModifyVariable(method = "setBlock(Lcom/sk89q/worldedit/math/BlockVector3;Lcom/sk89q/worldedit/world/block/BlockStateHolder;Lcom/sk89q/worldedit/util/SideEffectSet;)Z",
            at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private BlockStateHolder<?> dw$stripPrivateAppearance(BlockStateHolder<?> value) {
        return value instanceof BaseBlock block ? AppearanceTag.forNativeWrite(block) : value;
    }
}
