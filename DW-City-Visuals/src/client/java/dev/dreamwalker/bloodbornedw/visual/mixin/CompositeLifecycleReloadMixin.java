package dev.dreamwalker.bloodbornedw.visual.mixin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceReloader;
import net.minecraft.resource.SynchronousResourceReloader;
import net.minecraft.util.Unit;
import net.minecraft.util.profiler.Profiler;
import org.spongepowered.asm.mixin.Mixin;

/** Keep the whole synchronous listener inside the resource reload completion.
 * A fire-and-forget dispatch from its void reload method would acknowledge the
 * resource epoch before CPU jobs, cached meshes and GPU buffers were invalidated.
 */
@Mixin(targets="dev.dreamwalker.bloodbornedw.composite.CompositeClient$1",remap=false)
abstract class CompositeLifecycleReloadMixin implements SynchronousResourceReloader {
    @Override
    public CompletableFuture<Void> reload(ResourceReloader.Synchronizer synchronizer,
            ResourceManager manager, Profiler prepareProfiler, Profiler applyProfiler,
            Executor prepareExecutor, Executor applyExecutor) {
        MinecraftClient client=MinecraftClient.getInstance();
        return synchronizer.whenPrepared(Unit.INSTANCE).thenRunAsync(()->{
            if(!client.isOnThread())throw new IllegalStateException("Composite reload must apply on client thread");
            applyProfiler.startTick();
            applyProfiler.push("listener");
            try {
                // Calls CompositeClient$1's existing one-argument implementation:
                // resource revision + cancel jobs + CPU/GPU clear + static geometry.
                ((SynchronousResourceReloader)(Object)this).reload(manager);
            } finally {
                applyProfiler.pop();
                applyProfiler.endTick();
            }
        },client);
    }
}
