package dev.dreamwalker.bloodbornedw.composite.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.dreamwalker.bloodbornedw.composite.DistinctForms;
import dev.dreamwalker.bloodbornedw.composite.ImportedCollisionMigration;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ImportedCollisionMigration.class)
abstract class DistinctFormCollisionMigrationMixin {
    @ModifyExpressionValue(method = "rebuild", at = @At(value = "INVOKE",
            target = "Ljava/lang/String;equals(Ljava/lang/Object;)Z"), remap = false)
    private static boolean dreamwalker$matchRegisteredForm(boolean legacyIdMatches,
            @Local BlockState current, @Local(argsOnly = true) Owner owner) {
        return legacyIdMatches || DistinctForms.fixed(current.getBlock())
                && Registries.BLOCK.getId(current.getBlock()).toString().equals(owner.registryId());
    }
}
