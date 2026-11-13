package dev.dreamwalker.buildingqa.mixin;

import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Mouse.class)
public interface MouseInputAccessor {
    @Invoker("onMouseButton")
    void dw$onMouseButton(long window, int button, int action, int modifiers);
}
