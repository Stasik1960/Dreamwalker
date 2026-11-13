package dev.dreamwalker.bloodbornedw.building.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = "dev.dreamwalker.bloodbornedw.tool.DebugAdjustments", remap = false)
abstract class CompositeShiftOwnerMixin {
    @ModifyVariable(method = "renderPose", at = @At("LOAD"), argsOnly = true, ordinal = 0)
    private static BlockState dw$matchInstalledOwner(BlockState state, World world, BlockPos root,
            BlockState rendered) {
        // Composite and static renderers can pass a substitute model state.
        // The synchronized entry belongs to the block installed in this cell.
        return world.getBlockState(root);
    }
}
