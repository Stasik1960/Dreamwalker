package dev.dreamwalker.bloodbornedw.composite.studiomixin;

import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureCatalog;
import dev.dreamwalker.bloodbornedw.composite.StudioStaticShapes;
import net.minecraft.util.shape.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = StaticArchitectureCatalog.Recipe.class, remap = false)
public abstract class StudioStaticRecipeMixin implements StudioStaticShapes.Access {
    @Unique private volatile StudioStaticShapes dw$authoredShapes;

    @Override public StudioStaticShapes dw$studioShapes() {
        var shapes = dw$authoredShapes;
        if (shapes != null) return shapes;
        shapes = StudioStaticShapes.combine((StaticArchitectureCatalog.Recipe) (Object) this);
        dw$authoredShapes = shapes;
        return shapes;
    }

    @Inject(method = "outlineShape", at = @At("HEAD"), cancellable = true)
    private void dw$authoredOutline(CallbackInfoReturnable<VoxelShape> result) {
        var shape = dw$studioShapes().outline();
        if (shape != null) result.setReturnValue(shape);
    }
}
