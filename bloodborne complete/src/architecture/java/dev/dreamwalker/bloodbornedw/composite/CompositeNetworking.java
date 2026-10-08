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
    public static final Identifier OWNERS_SELECTION=CompositeArchitecture.id("composite_owner_selection");
    public static void initialize(){ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->send(handler.player,handler.player.getServerWorld(),new ArrayList<>(CompositeLedger.get(handler.player.getServerWorld()).cells())));ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->CompositeRuntime.clearSelection(handler.player.getUuid()));net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player,origin,destination)->{CompositeRuntime.clearSelection(player.getUuid());send(player,destination,new ArrayList<>(CompositeLedger.get(destination).cells()));});}
    public static void publish(ServerWorld world,List<Cell> cells){long started=dev.dreamwalker.bloodbornedw.diagnostics.ArchitectureDiagnostics.begin(world,true);try{for(ServerPlayerEntity player:world.getPlayers())send(player,world,cells);}catch(RuntimeException failure){dev.dreamwalker.bloodbornedw.diagnostics.DwDiagnostics.error(world,"UNASSIGNED","",cells.isEmpty()?null:CompositeData.pos(cells.get(0)),"COMPOSITE_SYNC","Owned contributions publish failed: "+failure.getMessage(),failure);throw failure;}finally{dev.dreamwalker.bloodbornedw.diagnostics.ArchitectureDiagnostics.finish(world,null,cells.isEmpty()?null:CompositeData.pos(cells.get(0)),"architecture.owners_sync_shared_batch",started);}}
    /** Server selects from its current ray hits. There is no client-provided owner request. */
    public static void sendSelection(ServerPlayerEntity player,Owner owner){if(!ServerPlayNetworking.canSend(player,OWNERS_SELECTION))return;var buffer=PacketByteBufs.create();buffer.writeIdentifier(player.getServerWorld().getRegistryKey().getValue());buffer.writeUuid(owner.instanceId());buffer.writeIdentifier(new Identifier(owner.registryId()));buffer.writeBlockPos(CompositeData.pos(owner.root()));dev.dreamwalker.bloodbornedw.diagnostics.DwDiagnostics.network("server","send",OWNERS_SELECTION.toString(),buffer.readableBytes());ServerPlayNetworking.send(player,OWNERS_SELECTION,buffer);}
    private static void send(ServerPlayerEntity player,ServerWorld world,List<Cell> cells){if(!ServerPlayNetworking.canSend(player,OWNERS))return;for(int start=0;start<cells.size();start+=64){var buffer=PacketByteBufs.create();buffer.writeIdentifier(world.getRegistryKey().getValue());int count=Math.min(64,cells.size()-start);buffer.writeVarInt(count);for(int i=0;i<count;i++){Cell cell=cells.get(start+i);buffer.writeBlockPos(CompositeData.pos(cell));NbtCompound tag=new NbtCompound();tag.put("owners",CompositeData.contributions(CompositeLedger.get(world).at(cell)));buffer.writeNbt(tag);}dev.dreamwalker.bloodbornedw.diagnostics.DwDiagnostics.network("server","send",OWNERS.toString(),buffer.readableBytes());ServerPlayNetworking.send(player,OWNERS,buffer);}}
    private CompositeNetworking(){}
}
