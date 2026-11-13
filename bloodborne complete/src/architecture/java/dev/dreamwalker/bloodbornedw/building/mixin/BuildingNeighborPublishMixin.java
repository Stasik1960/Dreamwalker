package dev.dreamwalker.bloodbornedw.building.mixin;

import dev.dreamwalker.bloodbornedw.composite.BuildingNeighbors;
import dev.dreamwalker.bloodbornedw.composite.FabricCompositeWorld;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import java.util.List;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FabricCompositeWorld.class, remap = false)
public abstract class BuildingNeighborPublishMixin {
    @Shadow @Final public ServerWorld world;

    @Inject(method = "publishCommitted", at = @At("RETURN"), remap = false)
    private void dreamwalker$publishSupportChanges(List<Cell> cells, CallbackInfo ci) {
        BuildingNeighbors.publishShapeChanges(world, cells);
    }
}
