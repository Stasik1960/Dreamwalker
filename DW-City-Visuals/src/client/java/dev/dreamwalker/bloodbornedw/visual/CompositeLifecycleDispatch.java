package dev.dreamwalker.bloodbornedw.visual;

import dev.dreamwalker.bloodbornedw.composite.CompositeWorldMesh;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Confines complete invalidation operations to the client/render thread. */
public final class CompositeLifecycleDispatch {
    private static final Logger LOG=LoggerFactory.getLogger("dw-composite-lifecycle");
    private static final AtomicBoolean REPORTED=new AtomicBoolean();
    private static final ThreadLocal<Boolean> COLOR_DISPATCH=ThreadLocal.withInitial(()->false);
    private CompositeLifecycleDispatch() {}
    private static void report(String operation) {
        if(REPORTED.compareAndSet(false,true))LOG.warn("Off-thread composite lifecycle operation {} queued on client thread",operation,new Throwable("Off-thread lifecycle origin"));
    }
    public static boolean deferReload() {
        MinecraftClient client=MinecraftClient.getInstance();
        if(client.isOnThread())return false;
        report("reload"); client.execute(CompositeWorldMesh::reload); return true;
    }
    public static void disconnect(ClientPlayNetworkHandler handler,MinecraftClient client) {
        ClientWorld oldWorld=handler.getWorld();
        Runnable operation=()->{if(client.world==null||client.world==oldWorld)CompositeWorldMesh.disconnect();};
        if(client.isOnThread())operation.run(); else {report("disconnect");client.execute(operation);}
    }
    public static void forget(ClientWorld world, WorldChunk chunk, ChunkPos pos) {
        MinecraftClient client=MinecraftClient.getInstance();
        Runnable operation=()->{
            if(client.world!=world)return;
            WorldChunk current=world.getChunkManager().getChunk(pos.x,pos.z,ChunkStatus.FULL,false);
            if(current!=null && current!=chunk)return;
            CompositeWorldMesh.forgetChunk(pos);
        };
        if(client.isOnThread())operation.run(); else {report("forgetChunk");client.execute(operation);}
    }
    /** Called from resetChunkColor with the actual receiver, including stale worlds. */
    public static void colors(ClientWorld world,ChunkPos pos) {
        MinecraftClient client=MinecraftClient.getInstance();
        Runnable operation=()->{
            if(client.world!=world)return;
            COLOR_DISPATCH.set(true);
            try { CompositeWorldMesh.chunkColorsChanged(pos); } finally { COLOR_DISPATCH.remove(); }
        };
        if(client.isOnThread())operation.run(); else {report("chunkColorsChanged");client.execute(operation);}
    }
    public static boolean dispatchedColors() { return COLOR_DISPATCH.get(); }
}
