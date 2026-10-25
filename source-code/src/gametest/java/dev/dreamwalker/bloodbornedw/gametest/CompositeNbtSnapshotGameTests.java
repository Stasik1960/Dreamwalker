package dev.dreamwalker.bloodbornedw.gametest;

import dev.dreamwalker.bloodbornedw.composite.CompositeData;
import dev.dreamwalker.bloodbornedw.runtime.CellSnapshot.BlockData;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
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
    @GameTest(templateName="bloodborne_dw:window_test",tickLimit=40,batchId="composite_nbt")
    public void packetLocalPoolsShareOnlyEqualImmutableValuesAndPreserveTypedPayload(TestContext test){
        Owner owner=new Owner(UUID.randomUUID(),"bloodborne_dw:prototype_thin_window",new Cell(1,64,2));
        Footprint shape=new Footprint(List.of(new Box(0,0,0,.5,1,1)),List.of(new Box(0,0,0,1,1,1)));
        NbtCompound row=CompositeData.contribution(new CompositeData.Contribution(owner,new BlockData(owner.registryId(),Map.of("open","false"),raw(payload(false))),shape));
        byte[] input=raw(row),expected=CompositeData.bytes(CompositeData.contribution(CompositeData.contribution(row)));
        CompositeData.Contribution retained;
        CompositeData.DecodePool pool=new CompositeData.DecodePool();
        try{
            retained=pool.contribution(row);var copied=pool.contribution(row.copy());
            test.assertTrue(retained.equals(copied)&&retained.owner()==copied.owner()&&retained.rootData()==copied.rootData()&&retained.shape()==copied.shape(),"equal rows share owner, payload and footprint only inside their packet pool");
            NbtCompound reordered=row.copy();reordered.getCompound("rootData").putByteArray("payload",raw(payload(true)));
            var canonical=pool.contribution(reordered);
            test.assertTrue(retained.equals(canonical)&&retained.rootData()==canonical.rootData(),"distinct raw map-order bytes converge only after exact canonical payload equality");
            test.assertTrue(Arrays.equals(expected,CompositeData.bytes(CompositeData.contribution(retained)))&&Arrays.equals(input,raw(row)),"sharing preserves every typed payload byte and leaves packet NBT unmodified");
            NbtCompound changed=row.copy(),changedPayload=payload(false);changedPayload.putInt("byte",1);changed.getCompound("rootData").putByteArray("payload",raw(changedPayload));
            var typed=pool.contribution(changed);
            test.assertTrue(typed.rootData()!=retained.rootData()&&!typed.rootData().equals(retained.rootData())&&CompositeData.nbt(typed.rootData().blockEntityNbt()).equals(changedPayload),"unequal typed payload is retained instead of replaced by an earlier owner payload");
            changed=row.copy();changed.getCompound("rootData").getCompound("properties").putString("open","true");var state=pool.contribution(changed);
            test.assertTrue(state.rootData()!=retained.rootData()&&state.rootData().properties().get("open").equals("true"),"unequal state properties cannot reuse prior root data");
            changed=row.copy();changed.put("selection",CompositeData.boxes(List.of(new Box(0,0,0,.25,1,1))));var selected=pool.contribution(changed);
            test.assertTrue(selected.owner()==retained.owner()&&selected.rootData()==retained.rootData()&&selected.shape()!=retained.shape()&&!selected.shape().equals(retained.shape()),"cell-local selection changes keep their own footprint while sharing equal owner data");
            changed=row.copy();changed.put("collision",CompositeData.boxes(List.of()));var passable=pool.contribution(changed);
            test.assertTrue(passable.shape()!=retained.shape()&&passable.shape().collision().isEmpty()&&passable.shape().selection().equals(retained.shape().selection()),"passable collision cannot acquire an earlier solid footprint");
            changed=row.copy();changed.getCompound("owner").putUuid("uuid",UUID.randomUUID());var replacement=pool.contribution(changed);
            test.assertTrue(replacement.owner()!=retained.owner()&&!replacement.owner().equals(retained.owner()),"different owner UUIDs retain independent identity");
        }finally{pool.close();}
        boolean closed=false;try{pool.contribution(row);}catch(IllegalStateException expectedClosed){closed=true;}
        test.assertTrue(closed,"a closed packet pool rejects accidental reuse");
        try(var nextPacket=new CompositeData.DecodePool()){
            var isolated=nextPacket.contribution(row.copy());
            test.assertTrue(retained.equals(isolated)&&retained.owner()!=isolated.owner()&&retained.rootData()!=isolated.rootData()&&retained.shape()!=isolated.shape(),"equal data in a later packet has an independent sharing scope");
            test.assertTrue(Arrays.equals(expected,CompositeData.bytes(CompositeData.contribution(retained))),"closing the pool leaves retained contribution values intact");
        }
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
