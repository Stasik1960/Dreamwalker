package dev.dreamwalker.bloodbornedw.building.mixin;

import dev.dreamwalker.bloodbornedw.composite.CompositeRuntime;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = CompositeRuntime.class, remap = false)
public interface CompositeShapeUpdateAccess {
    @Invoker(value = "queue", remap = false)
    static void dreamwalker$queue(ServerWorld world, String key, Runnable action) {
        throw new AssertionError("Untransformed accessor");
    }
}
