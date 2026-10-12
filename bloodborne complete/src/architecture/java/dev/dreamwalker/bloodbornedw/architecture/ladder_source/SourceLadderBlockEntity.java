package dev.dreamwalker.bloodbornedw.architecture.ladder_source;

import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;

/** Static source assembly identity. Original typed NBT is opaque provenance, never executed. */
public final class SourceLadderBlockEntity extends dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity {
    private UUID owner;
    private BlockPos root=BlockPos.ORIGIN,backing=BlockPos.ORIGIN;
    private NbtCompound provenance=new NbtCompound();
    public SourceLadderBlockEntity(BlockPos pos,BlockState state){super(SourceLadderRuntime.ENTITY,pos,state);}
    public UUID owner(){return owner;}
    @Override public dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner resident(){return owner!=null&&pos.equals(root)&&dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.isNative(getCachedState())?new dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner(owner,net.minecraft.registry.Registries.BLOCK.getId(getCachedState().getBlock()).toString(),dev.dreamwalker.bloodbornedw.composite.CompositeData.cell(root)):null;}
    public BlockPos root(){return root;}
    public BlockPos backing(){return backing;}
    public NbtCompound provenance(){return provenance.copy();}
    @Override public NbtCompound payload(){NbtCompound payload=super.payload();if(pos.equals(root)){BlockPos delta=backing.subtract(root);payload.putInt("NativeBackingX",delta.getX());payload.putInt("NativeBackingY",delta.getY());payload.putInt("NativeBackingZ",delta.getZ());}return payload;}
    public void set(UUID identity,BlockPos physicalRoot,BlockPos fixedBacking,NbtCompound original){owner=identity;root=physicalRoot.toImmutable();backing=fixedBacking.toImmutable();provenance=original.copy();markDirty();publishShapeSnapshot();}
    @Override protected void writeNbt(NbtCompound nbt){super.writeNbt(nbt);if(owner!=null)nbt.putUuid("Owner",owner);nbt.putLong("Root",root.asLong());nbt.putLong("FixedBacking",backing.asLong());nbt.put("Original",provenance.copy());}
    @Override public void readNbt(NbtCompound nbt){super.readNbt(nbt);owner=nbt.containsUuid("Owner")?nbt.getUuid("Owner"):null;root=BlockPos.fromLong(nbt.getLong("Root"));backing=BlockPos.fromLong(nbt.getLong("FixedBacking"));provenance=nbt.getCompound("Original").copy();
        java.util.List<String> invalid=new java.util.ArrayList<>();if(!nbt.containsUuid("Owner"))invalid.add("Owner expected UUID int-array[4], actual "+type(nbt,"Owner"));if(!nbt.contains("Root",NbtElement.LONG_TYPE))invalid.add("Root expected LONG, actual "+type(nbt,"Root"));if(!nbt.contains("FixedBacking",NbtElement.LONG_TYPE))invalid.add("FixedBacking expected LONG, actual "+type(nbt,"FixedBacking"));if(!nbt.contains("Original",NbtElement.COMPOUND_TYPE))invalid.add("Original expected COMPOUND, actual "+type(nbt,"Original"));
        if(!invalid.isEmpty())dev.dreamwalker.bloodbornedw.DreamwalkerBb.LOG.warn("Source-pair fields normalized with existing behavior: "+String.join("; ",invalid));
        publishShapeSnapshot();
    }
    private static String type(NbtCompound tag,String key){NbtElement value=tag.get(key);return value==null?"MISSING":"tagType="+value.getType();}
    @Override public NbtCompound toInitialChunkDataNbt(){return createNbt();}
    @Override public BlockEntityUpdateS2CPacket toUpdatePacket(){return BlockEntityUpdateS2CPacket.create(this);}
}
