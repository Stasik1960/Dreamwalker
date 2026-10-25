package dev.dreamwalker.bloodbornedw.architecture;

import dev.dreamwalker.bloodbornedw.composite.*;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.*;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.chunk.*;

/** A bit beside the unchanged host/BE, using existing WATERLOGGED states.
 * It changes physics, never the host's model, registry, UUID or saved payload. */
public final class FauxWaterlogging {
    public static final Identifier UPDATE=new Identifier("bloodborne_dw","faux_water");
    private static final ThreadLocal<Integer> PLACING=ThreadLocal.withInitial(()->0);
    private FauxWaterlogging(){}
    public static FauxWaterData data(Chunk chunk){return ((FauxWaterData.Holder)NativeVisuals.unwrap(chunk)).fauxWaterData();}
    public static void data(Chunk chunk,FauxWaterData value){((FauxWaterData.Holder)NativeVisuals.unwrap(chunk)).fauxWaterData(value);}
    public static boolean wet(BlockState state){return state.contains(Properties.WATERLOGGED)&&state.get(Properties.WATERLOGGED);}
    public static boolean logged(BlockView view,BlockPos pos){
        if(view instanceof World world){var chunk=world.getChunk(pos.getX()>>4,pos.getZ()>>4,ChunkStatus.FULL,false);return chunk!=null&&data(chunk).contains(pos);}
        return view instanceof Chunk chunk&&data(chunk).contains(pos);
    }
    public static boolean contains(World world,BlockPos pos){return world.getBlockState(pos).isOf(DwUtilityBlocks.WATER)||logged(world,pos);}
    public static FluidState physics(World world,BlockPos pos,FluidState original){
        if(!original.isOf(Fluids.WATER))return original;
        if(!contains(world,pos))return original;
        return PLACING.get()>0?Fluids.WATER.getDefaultState():DwUtilityBlocks.WATER_FLUID.getDefaultState();
    }
    public static <T> T placement(Supplier<T> action){PLACING.set(PLACING.get()+1);try{return action.get();}finally{int n=PLACING.get()-1;if(n==0)PLACING.remove();else PLACING.set(n);}}
    public static void stage(World world,BlockPos pos,boolean present){var chunk=world.getChunk(pos);data(chunk,data(chunk).with(pos,present));chunk.setNeedsSaving(true);}
    public static void publish(World world,BlockPos pos){if(!(world instanceof ServerWorld server))return;for(var player:PlayerLookup.tracking(server,pos)){PacketByteBuf b=PacketByteBufs.create();b.writeIdentifier(world.getRegistryKey().getValue());b.writeBlockPos(pos);b.writeBoolean(logged(world,pos));ServerPlayNetworking.send(player,UPDATE,b);}}
    /** Chunk state mutations clean stale bits and inherit water during a normal placement. */
    public static void changed(WorldChunk chunk,BlockPos pos,BlockState before,BlockState after){
        boolean old=data(chunk).contains(pos),next=wet(after)&&(old||before.isOf(DwUtilityBlocks.WATER));
        if(old==next)return;data(chunk,data(chunk).with(pos,next));chunk.setNeedsSaving(true);publish(chunk.getWorld(),pos);
    }
    public static boolean fill(ServerWorld world,BlockPos pos,PlayerEntity player){
        if(player==null||!player.isCreative()||!BuildPermissions.canEdit(world,player,pos))return false;
        var state=world.getBlockState(pos);if(!(state.getBlock() instanceof Waterloggable)||!state.contains(Properties.WATERLOGGED)||wet(state))return false;
        var before=world.getBlockEntity(pos);var nbt=before==null?null:before.createNbt();
        stage(world,pos,true);
        try{
            if(!world.setBlockState(pos,state.with(Properties.WATERLOGGED,true),Block.NOTIFY_ALL)){stage(world,pos,false);return false;}
            var after=world.getBlockEntity(pos);if(nbt!=null&&(after!=before||!nbt.equals(after.createNbt())))throw new IllegalStateException("Waterlogging changed the host block entity at "+pos);
            publish(world,pos);world.playSound(null,pos,net.minecraft.sound.SoundEvents.ITEM_BUCKET_EMPTY,net.minecraft.sound.SoundCategory.BLOCKS,1,1);return true;
        }catch(RuntimeException error){stage(world,pos,false);world.setBlockState(pos,state,Block.NOTIFY_ALL);if(nbt!=null&&world.getBlockEntity(pos)!=null)world.getBlockEntity(pos).readNbt(nbt);throw error;}
    }
    public static boolean remove(ServerWorld world,BlockPos pos,PlayerEntity player){
        if(player==null||!player.isCreative()||!logged(world,pos)||!BuildPermissions.canEdit(world,player,pos))return false;
        var state=world.getBlockState(pos);if(!wet(state))return false;
        if(!world.setBlockState(pos,state.with(Properties.WATERLOGGED,false),Block.NOTIFY_ALL))return false;
        stage(world,pos,false);publish(world,pos);return true;
    }
}
