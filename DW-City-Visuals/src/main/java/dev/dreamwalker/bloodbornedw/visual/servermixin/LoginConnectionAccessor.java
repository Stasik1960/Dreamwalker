package dev.dreamwalker.bloodbornedw.visual.servermixin;

import net.minecraft.network.ClientConnection;
import net.minecraft.server.network.ServerLoginNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerLoginNetworkHandler.class)
public interface LoginConnectionAccessor {
    @Accessor("connection") ClientConnection dw$getConnection();
}
