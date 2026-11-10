package dev.dreamwalker.bloodbornedw.editor.worldedit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.function.block.BlockReplace;
import com.sk89q.worldedit.function.pattern.Pattern;
import dev.dreamwalker.bloodbornedw.editor.worldedit.HandDoorPlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.sk89q.worldedit.command.RegionCommands", remap = false)
abstract class HandDoorSetMixin {
    @WrapOperation(method = "set", at = @At(value = "NEW", target = "com/sk89q/worldedit/function/block/BlockReplace"))
    private BlockReplace dw$setBothDoorHalves(Extent extent, Pattern pattern, Operation<BlockReplace> original) {
        return pattern instanceof HandDoorPlacement.Marker marker
            ? new HandDoorPlacement(extent, marker)
            : original.call(extent, pattern);
    }
}
