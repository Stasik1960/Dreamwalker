package dev.dreamwalker.bloodbornedw.architecture;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.chunk.ChunkStatus;
public final class FauxWaterClient {
    private FauxWaterClient(){}
    public static void initialize(){ClientPlayNetworking.registerGlobalReceiver(FauxWaterlogging.UPDATE,(client,handler,buffer,response)->{
        var dimension=buffer.readIdentifier();var pos=buffer.readBlockPos();boolean present=buffer.readBoolean();if(buffer.isReadable())throw new IllegalArgumentException("Trailing faux water update");
        client.execute(()->{if(client.world==null||!client.world.getRegistryKey().getValue().equals(dimension))return;var chunk=client.world.getChunk(pos.getX()>>4,pos.getZ()>>4,ChunkStatus.FULL,false);if(chunk!=null)FauxWaterlogging.data(chunk,FauxWaterlogging.data(chunk).with(pos,present));});
    });}
}
