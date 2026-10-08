package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.architecture.RpCreativeAttackReach;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional compatibility: distant RP origins must not cancel authorized nearby-part Creative editing. */
@Pseudo
@Mixin(targets = "com.jamieswhiteshirt.reachentityattributes.ReachEntityAttributes", remap = false)
public abstract class RpReachAttributesCompatibilityMixin {
    @Shadow(remap = false)
    public static double getSquaredAttackRange(LivingEntity player, double squaredBaseAttackRange) {
        throw new AssertionError("Optional ReachEntityAttributes shadow must be supplied by the installed target");
    }

    // The external API name is stable/unmapped; the typed Minecraft arguments are remapped by Loom.
    @Inject(method = "isWithinAttackRange", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private static void dreamwalker$creativeRpPart(PlayerEntity player, Entity target,
                                                   CallbackInfoReturnable<Boolean> cir) {
        var hit = RpCreativeAttackReach.editablePartHit(player, target);
        if (hit != null && player.getEyePos().squaredDistanceTo(hit) <= getSquaredAttackRange(player, 64))
            cir.setReturnValue(true);
    }
}
