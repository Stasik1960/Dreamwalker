package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;

public final class CompositeNetworking {
    public static final Identifier OWNERS=CompositeArchitecture.id("composite_owners");
    public static void initialize(){
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->initial(handler.player,handler.player.getServerWorld()));
        net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player,origin,destination)->initial(player,destination));
    }
    private static void initial(ServerPlayerEntity player,ServerWorld world){
        var ledger=CompositeLedger.get(world);
        // Sharded worlds synchronize on the actual vanilla chunk-data send.
        // Legacy saves keep the existing full-ledger join/world-change path.
        if(!ledger.sharded())send(player,world,new ArrayList<>(ledger.cells()));
    }
    public static void publish(ServerWorld world,List<Cell> cells){
        if(!CompositeLedger.get(world).sharded()){
            for(ServerPlayerEntity player:world.getPlayers())send(player,world,cells);
            return;
        }
        for(var entry:byChunk(cells).entrySet())
            for(var player:PlayerLookup.tracking(world,new ChunkPos(entry.getKey())))send(player,world,entry.getValue());
    }
    /** Called after vanilla sends this exact chunk to this exact player. No
     * per-player cache and no distant owner data are retained or broadcast. */
    public static void watchedChunk(ServerPlayerEntity player,ServerWorld world,ChunkPos chunk){
        var ledger=CompositeLedger.get(world);if(!ledger.sharded())return;
        send(player,world,new ArrayList<>(ledger.chunkCells(chunk)));
    }
    static Map<Long,List<Cell>> byChunk(Collection<Cell> cells){
        Map<Long,List<Cell>> result=new LinkedHashMap<>();
        for(var cell:cells)result.computeIfAbsent(ChunkPos.toLong(cell.x()>>4,cell.z()>>4),ignored->new ArrayList<>()).add(cell);
        return result;
    }

    private static void send(ServerPlayerEntity player,ServerWorld world,List<Cell> cells){if(!ServerPlayNetworking.canSend(player,OWNERS))return;var ledger=CompositeLedger.get(world);for(int start=0;start<cells.size();start+=64){var buffer=PacketByteBufs.create();buffer.writeIdentifier(world.getRegistryKey().getValue());int count=Math.min(64,cells.size()-start);buffer.writeVarInt(count);for(int i=0;i<count;i++){Cell cell=cells.get(start+i);buffer.writeBlockPos(CompositeData.pos(cell));NbtCompound tag=new NbtCompound();tag.put("owners",CompositeData.contributions(ledger.at(cell)));if(ledger.sharded())tag.putBoolean("OwnerShardClient",true);buffer.writeNbt(tag);};ServerPlayNetworking.send(player,OWNERS,buffer);}}
    private CompositeNetworking(){}
}
