package dev.dreamwalker.bloodbornedw.conversion.mixin;

import dev.dreamwalker.bloodbornedw.conversion.ConversionChunkControl;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.*;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Bypass only the importer's spawn ticket/wait; keep forced-chunk restoration. */
@Mixin(MinecraftServer.class)
public abstract class ConversionStartupMixin {
    @Redirect(method="prepareStartRegion",at=@At(value="INVOKE",target="Lnet/minecraft/server/world/ServerChunkManager;addTicket(Lnet/minecraft/server/world/ChunkTicketType;Lnet/minecraft/util/math/ChunkPos;ILjava/lang/Object;)V"))
    private <T> void dw$importerSpawnTicket(ServerChunkManager manager,ChunkTicketType<T> type,ChunkPos position,int distance,T argument){
        MinecraftServer server=(MinecraftServer)(Object)this;
        if(ConversionChunkControl.active(server)){
            if(type!=(Object)ChunkTicketType.START)throw new IllegalStateException("Unexpected native spawn ticket type");
            ConversionChunkControl.bypassed(server);return;
        }
        manager.addTicket(type,position,distance,argument);
    }
    @Redirect(method="prepareStartRegion",at=@At(value="INVOKE",target="Lnet/minecraft/server/world/ServerChunkManager;getTotalChunksLoadedCount()I"))
    private int dw$importerSkipSpawnWait(ServerChunkManager manager){
        MinecraftServer server=(MinecraftServer)(Object)this;
        // This sentinel affects only vanilla's private 441-chunk preload wait.
        // Importer telemetry enumerates actual holders/futures; never this value.
        if(ConversionChunkControl.active(server)&&ConversionChunkControl.spawnPreloadBypassed(server))return 441;
        return manager.getTotalChunksLoadedCount();
    }
}

