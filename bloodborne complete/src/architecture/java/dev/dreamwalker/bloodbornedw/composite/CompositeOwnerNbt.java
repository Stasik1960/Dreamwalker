package dev.dreamwalker.bloodbornedw.composite;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Set;
import net.minecraft.nbt.*;

/** Lossless owner-list envelope. Outer chunk compression remains vanilla's job. */
public final class CompositeOwnerNbt {
    public static final String KEY="OwnerDataV1";
    public static final int MAX_BYTES=16*1024*1024;
    private static final String OWNERS="owners";
    private CompositeOwnerNbt(){}

    public static void write(NbtCompound target,NbtList owners){
        checkList(owners);
        var inner=new NbtCompound();inner.put(OWNERS,owners);
        byte[] bytes=CompositeData.bytes(inner);
        if(bytes.length==0||bytes.length>MAX_BYTES)throw new IllegalArgumentException("Packed composite owners exceed the16MiB payload budget");
        target.remove(OWNERS);target.putByteArray(KEY,bytes);
    }

    public static NbtList read(NbtCompound source){
        if(!source.contains(KEY))return source.getList(OWNERS,NbtElement.COMPOUND_TYPE);
        if(source.contains(OWNERS))throw new IllegalArgumentException("Mixed legacy and packed composite owners");
        if(!source.contains(KEY,NbtElement.BYTE_ARRAY_TYPE))throw new IllegalArgumentException("Packed composite owners must be a byte array");
        byte[] bytes=source.getByteArray(KEY);
        if(bytes.length==0||bytes.length>MAX_BYTES)throw new IllegalArgumentException("Packed composite owners exceed the16MiB payload budget");
        if(bytes.length<3||bytes[0]!=NbtElement.COMPOUND_TYPE||bytes[1]!=0||bytes[2]!=0)throw new IllegalArgumentException("Packed composite owners require an unnamed root compound");
        try(var input=new DataInputStream(new ByteArrayInputStream(bytes))){
            NbtCompound inner=NbtIo.read(input,new NbtTagSizeTracker(MAX_BYTES));
            if(inner==null||input.available()!=0||!inner.getKeys().equals(Set.of(OWNERS)))throw new IllegalArgumentException("Invalid packed composite owner envelope");
            if(!(inner.get(OWNERS) instanceof NbtList owners))throw new IllegalArgumentException("Packed composite owners must contain a typed list");
            checkList(owners);return owners;
        }catch(IOException failure){throw new IllegalArgumentException("Invalid packed composite owner NBT",failure);}
    }

    private static void checkList(NbtList owners){
        if(owners.getHeldType()!=NbtElement.COMPOUND_TYPE&&!(owners.isEmpty()&&owners.getHeldType()==NbtElement.END_TYPE))throw new IllegalArgumentException("Composite owners must be a compound list");
    }
}
