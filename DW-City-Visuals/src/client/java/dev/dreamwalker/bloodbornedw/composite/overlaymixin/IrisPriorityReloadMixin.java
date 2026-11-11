package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.dreamwalker.bloodbornedw.composite.CompositeWorldMesh;
import dev.dreamwalker.bloodbornedw.composite.SurfaceDepthShaders;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.Iris", remap = false)
public abstract class IrisPriorityReloadMixin {
    @WrapMethod(method = "reload()V", remap = false)
    private static void dw$rebuildSurfacePriority(Operation<Void> original) {
        boolean before = SurfaceDepthShaders.compositeEnabled();
        try {
            original.call();
        } finally {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world != null && before != SurfaceDepthShaders.compositeEnabled()) {
                client.execute(CompositeWorldMesh::reload);
            }
        }
    }
}
