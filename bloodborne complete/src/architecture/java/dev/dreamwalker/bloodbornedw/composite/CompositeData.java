package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.runtime.CellSnapshot.BlockData;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import java.io.*;
import java.util.*;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public final class CompositeData {
    public record Contribution(Owner owner, BlockData rootData, Footprint shape) {}
    public static Cell cell(BlockPos pos){return new Cell(pos.getX(),pos.getY(),pos.getZ());}
    public static BlockPos pos(Cell cell){return new BlockPos(cell.x(),cell.y(),cell.z());}
    /** Stable typed snapshots across vanilla/Lithium map capacities and copies.
     * Compound key order has no NBT meaning; tag types and list/array order do.
     * Sort at the write stage, without changing any caller-owned NBT map.
     */
    public static byte[] bytes(NbtCompound nbt){
        try{
            ByteArrayOutputStream out=new ByteArrayOutputStream();DataOutputStream data=new DataOutputStream(out);
            data.writeByte(NbtElement.COMPOUND_TYPE);data.writeUTF("");writeCanonical(nbt,data);
            return out.toByteArray();
        }catch(IOException failure){throw new IllegalStateException(failure);}
    }
    private static void writeCanonical(NbtElement element,DataOutput output)throws IOException{
        if(element instanceof NbtCompound compound){
            for(String key:new TreeSet<>(compound.getKeys())){
                NbtElement child=Objects.requireNonNull(compound.get(key));
                output.writeByte(child.getType());output.writeUTF(key);writeCanonical(child,output);
            }
            output.writeByte(NbtElement.END_TYPE);
        }else if(element instanceof NbtList list){
            output.writeByte(list.getHeldType());output.writeInt(list.size());
            for(NbtElement child:list)writeCanonical(child,output);
        }else element.write(output);
    }
    public static NbtCompound nbt(byte[] bytes){if(bytes.length==0)return new NbtCompound();try{return NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes)));}catch(IOException failure){throw new IllegalStateException("Invalid composite payload",failure);}}
    public static BlockData data(BlockState state,NbtCompound nbt){Map<String,String> props=new TreeMap<>();state.getEntries().forEach((property,value)->props.put(property.getName(),name(property,value)));return new BlockData(Registries.BLOCK.getId(state.getBlock()).toString(),props,bytes(nbt));}
    @SuppressWarnings({"rawtypes","unchecked"})private static String name(Property property,Comparable value){return property.name(value);}
    public static BlockState state(BlockData data){try{Identifier id=new Identifier(data.blockId());if(!Registries.BLOCK.containsId(id))dev.dreamwalker.bloodbornedw.diagnostics.DwDiagnostics.error(null,"UNASSIGNED",data.blockId(),null,"UNKNOWN_BLOCK_ID_FALLBACK","Missing registry block; native registry fallback selected: "+data.blockId(),null);BlockState state=Registries.BLOCK.get(id).getDefaultState();for(var entry:data.properties().entrySet()){Property<?> property=state.getBlock().getStateManager().getProperty(entry.getKey());if(property==null)throw new IllegalArgumentException("Unknown composite state property "+entry.getKey());state=with(state,property,entry.getValue());}return state;}catch(RuntimeException failure){dev.dreamwalker.bloodbornedw.diagnostics.DwDiagnostics.error(null,"UNASSIGNED",data.blockId(),null,"INVALID_BLOCK_ID_OR_STATE","Cannot decode composite block/state: "+failure.getMessage(),failure);throw failure;}}
    private static <T extends Comparable<T>> BlockState with(BlockState state,Property<T> property,String value){return state.with(property,property.parse(value).orElseThrow(()->new IllegalArgumentException("Invalid state "+value)));}
    public static NbtCompound owner(Owner owner){NbtCompound n=new NbtCompound();n.putUuid("uuid",owner.instanceId());n.putString("id",owner.registryId());n.putIntArray("root",new int[]{owner.root().x(),owner.root().y(),owner.root().z()});return n;}
    public static Owner owner(NbtCompound n){int[] p=n.getIntArray("root");if(p.length!=3)throw new IllegalArgumentException("Invalid composite owner root");return new Owner(n.getUuid("uuid"),n.getString("id"),new Cell(p[0],p[1],p[2]));}
    public static NbtCompound data(BlockData data){NbtCompound n=new NbtCompound();n.putString("id",data.blockId());NbtCompound props=new NbtCompound();data.properties().forEach(props::putString);n.put("properties",props);n.putByteArray("payload",data.blockEntityNbt());return n;}
    public static BlockData data(NbtCompound n){Map<String,String> props=new TreeMap<>();NbtCompound values=n.getCompound("properties");for(String key:values.getKeys())props.put(key,values.getString(key));return new BlockData(n.getString("id"),props,bytes(nbt(n.getByteArray("payload"))));}
    public static NbtList boxes(List<Box> boxes){NbtList result=new NbtList();for(Box b:boxes){NbtList row=new NbtList();for(double v:new double[]{b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ()})row.add(NbtDouble.of(v));result.add(row);}return result;}
    public static List<Box> boxes(NbtList list){List<Box> result=new ArrayList<>();for(int i=0;i<list.size();i++){NbtList b=(NbtList)list.get(i);if(b.size()!=6)throw new IllegalArgumentException("Invalid cached composite cuboid");result.add(new Box(b.getDouble(0),b.getDouble(1),b.getDouble(2),b.getDouble(3),b.getDouble(4),b.getDouble(5)));}return List.copyOf(result);}
    public static NbtCompound contribution(Contribution c){NbtCompound n=new NbtCompound();n.put("owner",owner(c.owner));n.put("rootData",data(c.rootData));n.put("collision",boxes(c.shape.collision()));n.put("selection",boxes(c.shape.selection()));return n;}
    public static Contribution contribution(NbtCompound n){return new Contribution(owner(n.getCompound("owner")),data(n.getCompound("rootData")),new Footprint(boxes(n.getList("collision",9)),boxes(n.getList("selection",9))));}
    public static NbtList contributions(List<Contribution> entries){NbtList n=new NbtList();entries.stream().sorted(Comparator.comparing(Contribution::owner)).forEach(c->n.add(contribution(c)));return n;}
    public static List<Contribution> contributions(NbtList n){List<Contribution> result=new ArrayList<>();for(int i=0;i<n.size();i++)result.add(contribution(n.getCompound(i)));return List.copyOf(result);}
    private CompositeData(){}
}
