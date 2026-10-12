package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

public final class CompositeNetworking {
    public static final Identifier OWNERS=CompositeArchitecture.id("composite_owners");
    public static void initialize(){
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->send(handler.player,handler.player.getServerWorld(),new ArrayList<>(CompositeLedger.get(handler.player.getServerWorld()).cells())));
        net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player,origin,destination)->send(player,destination,new ArrayList<>(CompositeLedger.get(destination).cells())));
    }
    public static void publish(ServerWorld world,List<Cell> cells){
        for(ServerPlayerEntity player:world.getPlayers())send(player,world,cells);
    }
    /** Server selects from its current ray hits. There is no client-provided owner request. */

    private static void send(ServerPlayerEntity player,ServerWorld world,List<Cell> cells){if(!ServerPlayNetworking.canSend(player,OWNERS))return;for(int start=0;start<cells.size();start+=64){var buffer=PacketByteBufs.create();buffer.writeIdentifier(world.getRegistryKey().getValue());int count=Math.min(64,cells.size()-start);buffer.writeVarInt(count);for(int i=0;i<count;i++){Cell cell=cells.get(start+i);buffer.writeBlockPos(CompositeData.pos(cell));NbtCompound tag=new NbtCompound();tag.put("owners",CompositeData.contributions(CompositeLedger.get(world).at(cell)));buffer.writeNbt(tag);};ServerPlayNetworking.send(player,OWNERS,buffer);}}
    private CompositeNetworking(){}
}
