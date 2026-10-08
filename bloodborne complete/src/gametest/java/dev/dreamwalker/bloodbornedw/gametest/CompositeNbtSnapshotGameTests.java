package dev.dreamwalker.bloodbornedw.gametest;

import dev.dreamwalker.bloodbornedw.composite.CompositeData;
import dev.dreamwalker.bloodbornedw.runtime.CellSnapshot.BlockData;
import java.io.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.nbt.*;
import net.minecraft.test.*;

/** Snapshot equality is typed NBT equality, independent of hash-map iteration. */
public final class CompositeNbtSnapshotGameTests implements FabricGameTest {
    @GameTest(templateName="bloodborne_dw:window_test",tickLimit=40,batchId="composite_nbt")
    public void stableTypedPayloadAcrossMapOrdersCopiesAndLegacyCache(TestContext test){
        NbtCompound first=payload(false),second=payload(true);byte[] rawFirst=raw(first),rawSecond=raw(second);
        test.assertTrue(first.equals(second)&&!Arrays.equals(rawFirst,rawSecond),"same typed payload can have distinct native serialized map orders");
        byte[] stable=CompositeData.bytes(first);
        test.assertTrue(Arrays.equals(stable,CompositeData.bytes(second))&&Arrays.equals(stable,CompositeData.bytes(first.copy())),"canonical snapshots remain exact after reordered construction/copy");
        test.assertTrue(CompositeData.nbt(stable).equals(first)&&Arrays.equals(rawFirst,raw(first)),"every tag type/value/list order roundtrips and input is unmodified");
        NbtCompound cached=new NbtCompound();cached.putString("id","bloodborne_dw:prototype_thin_window");cached.put("properties",new NbtCompound());cached.putByteArray("payload",rawSecond);
        BlockData legacy=CompositeData.data(cached);
        test.assertTrue(Arrays.equals(legacy.blockEntityNbt(),stable)&&CompositeData.nbt(legacy.blockEntityNbt()).equals(first),"saved earlier unordered cache payload is normalized without changing typed data");
        NbtCompound changed=first.copy();changed.putInt("byte",1);
        test.assertTrue(!Arrays.equals(stable,CompositeData.bytes(changed)),"byte-to-int changes still fail exact comparison");
        changed=first.copy();NbtList list=new NbtList();list.add(NbtInt.of(9));list.add(NbtInt.of(3));changed.put("orderedList",list);
        test.assertTrue(!Arrays.equals(stable,CompositeData.bytes(changed)),"list order is not normalized away");
        changed=first.copy();changed.putLongArray("longArray",new long[]{1,Long.MIN_VALUE});
        test.assertTrue(!Arrays.equals(stable,CompositeData.bytes(changed)),"array content/order changes still fail exact comparison");
        test.complete();
    }
    private static NbtCompound payload(boolean reverse){
        LinkedHashMap<String,NbtElement> values=new LinkedHashMap<>();
        values.put("GlazingMounted",NbtByte.of((byte)1));values.put("MountFace",NbtString.of("up"));values.put("MountY",NbtDouble.of(.25));
        values.put("byte",NbtByte.of((byte)1));values.put("short",NbtShort.of((short)-7));values.put("int",NbtInt.of(73));values.put("long",NbtLong.of(Long.MIN_VALUE));
        values.put("float",NbtFloat.of(-0.0F));values.put("double",NbtDouble.of(-0.0));values.put("string",NbtString.of("Стык ☃"));
        values.put("byteArray",new NbtByteArray(new byte[]{-128,0,127}));values.put("intArray",new NbtIntArray(new int[]{Integer.MIN_VALUE,3}));values.put("longArray",new NbtLongArray(new long[]{Long.MIN_VALUE,1}));
        NbtList list=new NbtList();list.add(NbtInt.of(3));list.add(NbtInt.of(9));values.put("orderedList",list);
        LinkedHashMap<String,NbtElement> inner=new LinkedHashMap<>();inner.put(reverse?"BB":"Aa",NbtString.of("same"));inner.put(reverse?"Aa":"BB",NbtString.of("same"));
        values.put("nested",ordered(inner));NbtList compounds=new NbtList();compounds.add(ordered(inner));values.put("compoundList",compounds);
        values.put("emptyList",new NbtList());values.put("emptyCompound",new NbtCompound());
        if(reverse){List<String> keys=new ArrayList<>(values.keySet());Collections.reverse(keys);LinkedHashMap<String,NbtElement> reordered=new LinkedHashMap<>();for(String key:keys)reordered.put(key,values.get(key));values=reordered;}
        return ordered(values);
    }
    private static NbtCompound ordered(Map<String,NbtElement> values){
        try{var constructor=NbtCompound.class.getDeclaredConstructor(Map.class);constructor.setAccessible(true);return constructor.newInstance(values);}
        catch(ReflectiveOperationException error){throw new IllegalStateException("Native test requires NbtCompound's typed map constructor",error);}
    }
    private static byte[] raw(NbtCompound nbt){
        try{ByteArrayOutputStream buffer=new ByteArrayOutputStream();NbtIo.write(nbt,new DataOutputStream(buffer));return buffer.toByteArray();}
        catch(IOException error){throw new IllegalStateException(error);}
    }
}
