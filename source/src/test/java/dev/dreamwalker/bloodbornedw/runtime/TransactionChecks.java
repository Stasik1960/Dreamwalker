package dev.dreamwalker.bloodbornedw.runtime;

import dev.dreamwalker.bloodbornedw.composite.CompositeData;
import dev.dreamwalker.bloodbornedw.runtime.CellSnapshot.BlockData;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import java.util.*;
import net.minecraft.nbt.NbtCompound;

/** Existing Java 17 checks; NBT sharing checks use no registries or game bootstrap. */
public final class TransactionChecks {
    private TransactionChecks() {}
    public static void main(String[] args) { TransactionCoreTest.main(args); RollbackStagingTest.main(args); decodePoolSharing(); }
    private static void decodePoolSharing(){
        NbtCompound payload=new NbtCompound();payload.putByte("typed",(byte)1);payload.putByteArray("bytes",new byte[]{-128,0,127});payload.putDouble("signedZero",-0.0);
        Owner owner=new Owner(UUID.randomUUID(),"test:shared",new Cell(1,64,2));Footprint shape=new Footprint(List.of(new Box(0,0,0,.5,1,1)),List.of(new Box(0,0,0,1,1,1)));
        NbtCompound row=CompositeData.contribution(new CompositeData.Contribution(owner,new BlockData(owner.registryId(),Map.of("open","false"),CompositeData.bytes(payload)),shape));
        byte[] input=CompositeData.bytes(row);CompositeData.Contribution retained;CompositeData.DecodePool pool=new CompositeData.DecodePool();
        try(pool){
            retained=pool.contribution(row);var copied=pool.contribution(row.copy());
            require(retained.equals(copied)&&retained.owner()==copied.owner()&&retained.rootData()==copied.rootData()&&retained.shape()==copied.shape(),"equal packet rows share all immutable value references");
            require(Arrays.equals(input,CompositeData.bytes(CompositeData.contribution(retained)))&&Arrays.equals(input,CompositeData.bytes(row)),"pool preserves typed packet bytes and input NBT");
            NbtCompound changed=row.copy(),differentPayload=payload.copy();differentPayload.putInt("typed",1);changed.getCompound("rootData").putByteArray("payload",CompositeData.bytes(differentPayload));
            var typed=pool.contribution(changed);
            require(typed.rootData()!=retained.rootData()&&CompositeData.nbt(typed.rootData().blockEntityNbt()).equals(differentPayload),"same owner with unequal typed payload keeps exact changed data");
            changed=row.copy();changed.getCompound("rootData").getCompound("properties").putString("open","true");var state=pool.contribution(changed);
            require(state.rootData()!=retained.rootData()&&state.rootData().properties().get("open").equals("true"),"unequal block state never reuses earlier payload data");
            changed=row.copy();changed.put("selection",CompositeData.boxes(List.of()));var selection=pool.contribution(changed);
            require(selection.owner()==retained.owner()&&selection.rootData()==retained.rootData()&&selection.shape()!=retained.shape()&&selection.shape().selection().isEmpty(),"cell-local geometry stays distinct while equal owner/root data shares");
            changed=row.copy();changed.getCompound("owner").putUuid("uuid",UUID.randomUUID());var replacement=pool.contribution(changed);
            require(!replacement.owner().equals(retained.owner())&&replacement.owner()!=retained.owner(),"different UUIDs cannot share owner identity");
        }
        boolean closed=false;try{pool.contribution(row);}catch(IllegalStateException expected){closed=true;}require(closed,"closed packet pool cannot be reused");
        try(var nextPacket=new CompositeData.DecodePool()){
            var isolated=nextPacket.contribution(row.copy());
            require(retained.equals(isolated)&&retained.owner()!=isolated.owner()&&retained.rootData()!=isolated.rootData()&&retained.shape()!=isolated.shape(),"packet-local pools retain no cross-packet sharing scope");
            require(Arrays.equals(input,CompositeData.bytes(CompositeData.contribution(retained))),"closing a pool preserves externally retained immutable values");
        }
        System.out.println("PASS packet-local composite decode sharing, exact typed bytes, unequal values and scope isolation");
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
