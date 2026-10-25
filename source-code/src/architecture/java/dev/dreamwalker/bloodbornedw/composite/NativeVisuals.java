package dev.dreamwalker.bloodbornedw.composite;

import java.util.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.block.*;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.chunk.*;

/** Chunk-owned metadata and bounded change packets. Physics queries never use
 * this table. Rendering workers see NativeVisualView immutable snapshots only. */
public final class NativeVisuals {
    public static final Identifier UPDATE=new Identifier("bloodborne_dw","native_visual");
    public static final String ITEM_KEY="NativeVisualIndex";
    private static final ThreadLocal<BreakValue> BREAK=new ThreadLocal<>();
    private record BreakValue(World world,BlockPos pos,int index){}
    private NativeVisuals(){}
    public static Chunk unwrap(Chunk chunk){return chunk instanceof WrapperProtoChunk wrapper?wrapper.getWrappedChunk():chunk;}
    public static NativeVisualData data(Chunk chunk){return ((NativeVisualChunk)unwrap(chunk)).nativeVisualData();}
    public static void data(Chunk chunk,NativeVisualData data){((NativeVisualChunk)unwrap(chunk)).nativeVisualData(data);}
    public static int index(BlockView view,BlockPos pos){
        if(view instanceof NativeVisualView snapshot)return snapshot.nativeVisualIndex(pos);
        if(view instanceof ProjectedBlockView projected)return index(projected.backingWorld(),projected.worldPosition(pos));
        if(view instanceof Chunk chunk)return data(chunk).at(pos);
        if(view instanceof World world){var chunk=world.getChunk(pos.getX()>>4,pos.getZ()>>4,ChunkStatus.FULL,false);return chunk==null?-1:data(chunk).at(pos);}
        return -1;
    }
    public static int required(BlockView view,BlockPos pos){int value=index(view,pos);if(value<0)throw new IllegalStateException("Missing native visual index at "+pos+" in "+view.getClass().getName());return value;}
    private static final java.util.Set<Class<?>> PREVIEW_WARNINGS=java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Real chunks remain strict. A foreign preview containing only a state
     * cannot encode its material, so use its physical representative without
     * writing that fallback to a chunk or changing authoritative metadata. */
    public static int forView(BlockView view,BlockPos pos,BlockState state){
        int visual=index(view,pos);if(visual>=0)return visual;
        if(view instanceof World||view instanceof Chunk||view instanceof NativeVisualView||view instanceof ProjectedBlockView)return required(view,pos);
        if(!(state.getBlock() instanceof NativePhysicsBlock physical))throw new IllegalArgumentException("Not a native physical preview");
        if(PREVIEW_WARNINGS.size()<32&&PREVIEW_WARNINGS.add(view.getClass()))org.slf4j.LoggerFactory.getLogger("DreamwalkerNativeVisual").warn("Preview {} supplies no NativeVisualView attachment; using a physical representative for display only",view.getClass().getName());
        return NativeVisualLayout.representative(physical.physics(state));
    }
    public static void stage(World world,BlockPos pos,int index){var chunk=world.getChunk(pos);data(chunk,data(chunk).with(pos,index));chunk.setNeedsSaving(true);}
    public static void publish(World world,BlockPos pos){
        if(!(world instanceof ServerWorld server))return;
        int index=index(world,pos);var state=world.getBlockState(pos);
        for(var player:PlayerLookup.tracking(server,pos)){
            var buffer=PacketByteBufs.create();buffer.writeIdentifier(world.getRegistryKey().getValue());buffer.writeBlockPos(pos);buffer.writeVarInt(index+1);buffer.writeVarInt(Block.getRawIdFromState(state));ServerPlayNetworking.send(player,UPDATE,buffer);
        }
    }
    public static void reconcile(WorldChunk chunk,BlockPos pos,BlockState state){
        var current=data(chunk);int index=current.at(pos),next=index;
        if(state.getBlock() instanceof NativePhysicsBlock physical){if(index<0||!state.equals(NativeGridCatalog.state(index)))next=NativeVisualLayout.representative(physical.physics(state));}
        else next=-1;
        // State replacement already has vanilla notification/transaction rules.
        // Do not leak a provisional silent composite write through a side packet.
        if(next==index)return;data(chunk,current.with(pos,next));chunk.setNeedsSaving(true);
    }
    public static boolean write(World world,BlockPos pos,int index,int flags){
        var next=NativeGridCatalog.state(index);if(!(next.getBlock() instanceof NativePhysicsBlock))throw new IllegalArgumentException("Whole roles use their retained state path");
        var chunk=world.getChunk(pos);var before=data(chunk);var previous=world.getBlockState(pos);data(chunk,before.with(pos,index));
        try{
            boolean changed=world.setBlockState(pos,next,flags);
            if(!changed&&!world.getBlockState(pos).equals(next)){data(chunk,data(chunk).with(pos,before.at(pos)));return false;}
            chunk.setNeedsSaving(true);publish(world,pos);return true;
        }catch(RuntimeException failure){data(chunk,data(chunk).with(pos,before.at(pos)));world.setBlockState(pos,previous,flags|Block.SKIP_DROPS);throw failure;}
    }
    public static AutoCloseable breakScope(World world,BlockPos pos){var previous=BREAK.get();BREAK.set(new BreakValue(world,pos.toImmutable(),required(world,pos)));return ()->{if(previous==null)BREAK.remove();else BREAK.set(previous);};}
    public static int dropIndex(World world,BlockPos pos){var value=BREAK.get();if(value!=null&&value.world==world&&value.pos.equals(pos))return value.index;return required(world,pos);}
}
