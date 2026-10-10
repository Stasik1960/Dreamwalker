package dev.dreamwalker.bloodbornedw.architecture;

import dev.dreamwalker.bloodbornedw.architecture.wall.*;
import dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

/** Versioned, loaded-chunk-only migration. Hidden registry aliases are never new content. */
public final class CatalogueMigration {
    public static final int VERSION=1;
    private static final String KEY="CanonicalCatalogueVersion";
    private CatalogueMigration() {}
    public static BlockState target(BlockState old) {
        if(old.getBlock() instanceof PrototypeWallBlock) {
            int material=PrototypeWallBlock.material(old);
            if(material==0)return old; // 90011 is a separate, unaffected object.
            return PrototypeWallBlock.canonicalForm(PrototypeWallArchitecture.changeMaterial(old,
                    (old.get(PrototypeWallBlock.ROTATION)&1)==0?6:1));
        }
        if(old.getBlock() instanceof PrototypeLadderBlock) {
            BlockState next=PrototypeArchitecture.ladderItem(0).getBlock().getDefaultState();
            for(var property:old.getProperties())if(next.contains(property))next=copy(next,old,property);
            return next.with(PrototypeLadderBlock.VARIANT,0);
        }
        return old;
    }
    private static <T extends Comparable<T>> BlockState copy(BlockState next,BlockState old,net.minecraft.state.property.Property<T> property) {
        return next.with(property,old.get(property));
    }
    public static void loadedChunk(ServerWorld world,ChunkPos chunk) {
        var loaded=world.getChunkManager().getWorldChunk(chunk.x,chunk.z);
        if(loaded==null)return;
        for(BlockPos pos:List.copyOf(loaded.getBlockEntities().keySet()))loadedRoot(world,pos);
    }
    public static void loadedRoot(ServerWorld world,BlockPos pos) {
        var chunk=world.getChunkManager().getWorldChunk(pos.getX()>>4,pos.getZ()>>4);
        if(chunk==null||!(chunk.getBlockEntity(pos) instanceof CompositeBlockEntity own)||own.resident()==null)return;
        BlockState old=chunk.getBlockState(pos),next=target(old);
        if(old.equals(next))return;
        NbtCompound payload=own.payload();
        if(payload.getInt(KEY)>=VERSION)return;
        payload.putInt(KEY,VERSION);
        // Rebuild authored geometry for the current type; retain all other typed fields and offsets.
        payload=VerticalMount.nativePayload(world,pos,next,payload);
        var result=CompositeRuntime.migrateCatalogue(world,own.resident(),next,payload);
        if(result.outcome()!=TransactionCore.Outcome.COMMITTED)
            dev.dreamwalker.bloodbornedw.DreamwalkerBb.LOG.warn(result.reason());
    }
    public static boolean retired(String path) {
        return path.equals("builder_tool")||path.equals("composite_builder")||path.equals("wall_builder")||path.equals("prototype_ladder_art_1")||path.equals("prototype_ladder_art_2")
                ||path.matches("prototype_wall_skin_[23457]");
    }
    public static ItemStack canonicalStack(ItemStack old) {
        if(old.isEmpty())return old;
        if(!(old.getItem() instanceof BlockItem item))return old;
        var block=item.getBlock();
        if(!(block instanceof PrototypeWallBlock)&&!(block instanceof PrototypeLadderBlock))return old;
        if(!old.hasNbt()&&!retired(net.minecraft.registry.Registries.ITEM.getId(old.getItem()).getPath()))return old;
        ItemStack next=old.copy();NbtCompound form=next.getOrCreateSubNbt("BlockStateTag");
        net.minecraft.item.Item canonical=old.getItem();
        if(block instanceof PrototypeWallBlock) {
            if(PrototypeWallItem.material(old)==0)return old;
            int yaw=0;
            try { yaw=Integer.parseInt(form.getString("rotation"))&7; } catch(NumberFormatException ignored) { if(PrototypeWallItem.material(old)==1)yaw=1; }
            int material=(yaw&1)==0?6:1;
            canonical=PrototypeWallArchitecture.materialBlock(material).asItem();
            form.putString("material",Integer.toString(material));form.putString("rotation",Integer.toString(yaw));
            if(material==1) {
                form.putString("connections","manual");form.putString("up","true");
                for(var direction:net.minecraft.util.math.Direction.Type.HORIZONTAL)form.putString(direction.asString(),"none");
            }
        } else {
            canonical=PrototypeArchitecture.ladderItem(0);form.putString("variant","0");
        }
        if(canonical!=old.getItem()) { ItemStack replacement=new ItemStack(canonical,old.getCount());replacement.setNbt(next.getNbt().copy());next=replacement; }
        return ItemStack.areEqual(old,next)?old:next;
    }
}
