package dev.dreamwalker.bloodbornedw.visual.mixin;

import net.minecraft.client.network.ClientLoginNetworkHandler;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.network.ClientConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientLoginNetworkHandler.class)
public interface ClientLoginConnectionAccessor {
    @Accessor("connection") ClientConnection dw$getConnection();
    @Accessor("serverInfo") ServerInfo dw$getServerInfo();
}
