package dev.dreamwalker.bloodbornedw.visual.mixin;

import net.minecraft.network.ClientConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientConnection.class)
public interface ConnectionCloseStateAccessor {
    @Accessor("disconnected") boolean dw$closeHandled();
}
