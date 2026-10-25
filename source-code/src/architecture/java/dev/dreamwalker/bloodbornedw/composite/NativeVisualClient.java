package dev.dreamwalker.bloodbornedw.composite;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.world.chunk.ChunkStatus;
/** Updates are finite authoritative block changes. No world is loaded by a
 * packet for an unwatched chunk or a different dimension. */
public final class NativeVisualClient {
    private NativeVisualClient(){}
    public static void initialize(){ClientPlayNetworking.registerGlobalReceiver(NativeVisuals.UPDATE,(client,handler,buffer,response)->{
        var dimension=buffer.readIdentifier();var pos=buffer.readBlockPos();int visual=buffer.readVarInt()-1;var state=Block.getStateFromRawId(buffer.readVarInt());
        if(buffer.readableBytes()!=0||visual< -1||visual>=NativeGridCatalog.cells().size()||visual>=0&&(NativeVisualLayout.physics(visual)<0||!state.equals(NativeGridCatalog.state(visual))))throw new IllegalArgumentException("Invalid visual update");
        client.execute(()->{if(client.world==null||!client.world.getRegistryKey().getValue().equals(dimension))return;var chunk=client.world.getChunkManager().getChunk(pos.getX()>>4,pos.getZ()>>4,ChunkStatus.FULL,false);if(chunk==null)return;
            // The ordinary state packet clears metadata when it removes a
            // physical block. Never leave a still-renderable physical block
            // without its visual while that vanilla packet is in flight.
            if(visual<0&&client.world.getBlockState(pos).getBlock() instanceof NativePhysicsBlock)return;
            NativeVisuals.data(chunk,NativeVisuals.data(chunk).with(pos,visual));
            if(visual>=0&&!client.world.getBlockState(pos).equals(state))client.world.setBlockState(pos,state,Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
            client.worldRenderer.scheduleBlockRenders(pos.getX()-1,pos.getY()-1,pos.getZ()-1,pos.getX()+1,pos.getY()+1,pos.getZ()+1);
        });
    });}
}
