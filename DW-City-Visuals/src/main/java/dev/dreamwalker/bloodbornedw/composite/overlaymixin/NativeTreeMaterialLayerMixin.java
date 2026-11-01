package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.NativeGridCatalog;
import java.util.ArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fix a shared native tree material whose source quads sample alpha-zero texels. */
@Mixin(value = NativeGridCatalog.class, remap = false)
public abstract class NativeTreeMaterialLayerMixin {
    @Shadow private static List<NativeGridCatalog.Material> materials;

    @Inject(method = "initialize", at = @At("RETURN"), remap = false)
    private static void dw$cutoutTreeMaterial(CallbackInfo ci) {
        List<NativeGridCatalog.Material> changed = null;
        for (int i = 0; i < materials.size(); i++) {
            var item = materials.get(i);
            if (!item.layer().equals("solid") || !item.sprite().equals(
                    "bloodborne_dw:block/remaining/source/minecraft/block/addon/tree_1")) continue;
            if (changed == null) changed = new ArrayList<>(materials);
            changed.set(i, new NativeGridCatalog.Material(item.sprite(), "cutout", item.tintSource(),
                    item.tintMode(), item.tintProperties(), item.tintOffset()));
        }
        if (changed != null) materials = List.copyOf(changed);
    }
}
