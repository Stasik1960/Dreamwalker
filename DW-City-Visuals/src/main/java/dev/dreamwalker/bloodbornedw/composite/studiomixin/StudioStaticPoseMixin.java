package dev.dreamwalker.bloodbornedw.composite.studiomixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.dreamwalker.bloodbornedw.composite.CompositeSpec.Part;
import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureCatalog;
import dev.dreamwalker.bloodbornedw.composite.StudioFootprints;
import dev.dreamwalker.bloodbornedw.composite.StudioLayouts;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import java.util.List;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = StaticArchitectureCatalog.Pose.class, remap = false)
public abstract class StudioStaticPoseMixin {
    @Inject(method = "outline", at = @At("HEAD"), cancellable = true)
    private void dw$authoredOutline(CallbackInfoReturnable<Map<Cell, List<Box>>> result) {
        var geometry = StudioFootprints.staticPose((StaticArchitectureCatalog.Pose) (Object) this);
        if (geometry != null) result.setReturnValue(geometry.outline());
    }

    @ModifyReturnValue(method = "parts", at = @At("RETURN"))
    private List<Part> dw$authoredStaticArt(List<Part> parts) {
        var pose = (StaticArchitectureCatalog.Pose) (Object) this;
        var model = StudioLayouts.model(pose.block().spec);
        return model == null ? parts : StudioLayouts.art(parts, model, StudioLayouts.pose(model, pose.state()), true);
    }
}
