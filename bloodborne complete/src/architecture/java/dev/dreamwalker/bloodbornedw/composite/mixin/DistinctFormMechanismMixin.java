package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.link.CompositeMechanismBridge;
import dev.dreamwalker.bloodbornedw.link.MechanismLinks;
import dev.dreamwalker.bloodbornedw.composite.ImportedTypeAliases;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CompositeMechanismBridge.class)
abstract class DistinctFormMechanismMixin {
    @Redirect(method = "inspect", at = @At(value = "INVOKE",
            target = "Ldev/dreamwalker/bloodbornedw/composite/ImportedTypeAliases;equivalent(Ljava/lang/String;Ljava/lang/String;)Z", ordinal = 0),
            remap = false)
    private boolean dreamwalker$compareActualDoorId(String specId, String registry, ServerWorld world, MechanismLinks.TargetRef target) {
        var block = world.getBlockState(BlockPos.fromLong(target.root())).getBlock();
        return ImportedTypeAliases.equivalent(Registries.BLOCK.getId(block).toString(), registry);
    }

    @Redirect(method = "inspect", at = @At(value = "INVOKE", target = "Ljava/lang/String;equals(Ljava/lang/Object;)Z", ordinal = 1), remap = false)
    private boolean dreamwalker$matchDoorOwner(String specId, Object ownerId, ServerWorld world, MechanismLinks.TargetRef target) {
        return Registries.BLOCK.getId(world.getBlockState(BlockPos.fromLong(target.root())).getBlock()).toString().equals(ownerId);
    }
}
