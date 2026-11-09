package dev.dreamwalker.bloodbornedw.composite.studiomixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.dreamwalker.bloodbornedw.composite.StudioRpGeometry;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RpObjectEntity.class, remap = false)
public abstract class StudioRpMixin {
    @Inject(method = {"selectionBoxes", "interactionBoxes"}, at = @At("HEAD"), cancellable = true)
    private void dw$authoredOutline(CallbackInfoReturnable<List<Box>> result) {
        var layout = StudioRpGeometry.layout((RpObjectEntity) (Object) this);
        if (layout != null) result.setReturnValue(layout.outline());
    }

    @ModifyReturnValue(method = "queryBounds", at = @At("RETURN"))
    private Box dw$includeStudioGeometry(Box original) {
        var layout = StudioRpGeometry.layout((RpObjectEntity) (Object) this);
        return layout == null || layout.extent() == null ? original : original.union(layout.extent());
    }

    @Inject(method = "method_5688(Lnet/minecraft/class_1657;Lnet/minecraft/class_1268;)Lnet/minecraft/class_1269;",
            at = @At("HEAD"), cancellable = true)
    private void dw$sitOnAuthoredSurface(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> result) {
        var seated = StudioRpGeometry.seat((RpObjectEntity) (Object) this, player, hand);
        if (seated != ActionResult.PASS) result.setReturnValue(seated);
    }
}
