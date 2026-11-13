package dev.dreamwalker.bloodbornedw.building.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.block.BlockState;
import net.minecraft.block.WallBlock;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(WallBlock.class)
public abstract class VanillaWallPaneMixin {
    @WrapMethod(method = "shouldConnectTo")
    private boolean dreamwalker$connectImportedPane(BlockState neighbor, boolean solid, Direction face,
            Operation<Boolean> original) {
        // The distribution's wall mixin also handles imported families and can return early.
        return ImportedFenceAccess.dreamwalker$matches(neighbor)
                && !ImportedFenceAccess.dreamwalker$wallLike(neighbor)
                && !ImportedFenceAccess.dreamwalker$fenceLike(neighbor)
                || original.call(neighbor, solid, face);
    }
}
