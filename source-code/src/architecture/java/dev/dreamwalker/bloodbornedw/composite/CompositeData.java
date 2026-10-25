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
    private static final ThreadLocal<DecodePool> ACTIVE_DECODE=new ThreadLocal<>();
    /** One vanilla chunk attachment/packet, including reentrant loads. Only
     * immutable decoded values are shared; raw decoder NBT dies at close. */
    public static final class DecodeScope implements AutoCloseable {
        private final DecodePool previous=ACTIVE_DECODE.get(),pool=new DecodePool();
        private boolean closed;
        private DecodeScope(){ACTIVE_DECODE.set(pool);}
        @Override public void close(){if(closed||ACTIVE_DECODE.get()!=pool)throw new IllegalStateException("Unbalanced owner decode scope");closed=true;pool.close();if(previous==null)ACTIVE_DECODE.remove();else ACTIVE_DECODE.set(previous);}
    }
    public static DecodeScope openDecodeScope(){return new DecodeScope();}
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
    public static BlockState state(BlockData data){try{Identifier id=new Identifier(data.blockId());if(!Registries.BLOCK.containsId(id))dev.dreamwalker.bloodbornedw.DreamwalkerBb.LOG.warn("Missing registry block; native registry fallback selected: "+data.blockId());BlockState state=Registries.BLOCK.get(id).getDefaultState();for(var entry:data.properties().entrySet()){Property<?> property=state.getBlock().getStateManager().getProperty(entry.getKey());if(property==null)throw new IllegalArgumentException("Unknown composite state property "+entry.getKey());state=with(state,property,entry.getValue());}return state;}catch(RuntimeException failure){dev.dreamwalker.bloodbornedw.DreamwalkerBb.LOG.warn("Cannot decode composite block/state: "+failure.getMessage(), failure);throw failure;}}
    private static <T extends Comparable<T>> BlockState with(BlockState state,Property<T> property,String value){return state.with(property,property.parse(value).orElseThrow(()->new IllegalArgumentException("Invalid state "+value)));}
    public static NbtCompound owner(Owner owner){NbtCompound n=new NbtCompound();n.putUuid("uuid",owner.instanceId());n.putString("id",owner.registryId());n.putIntArray("root",new int[]{owner.root().x(),owner.root().y(),owner.root().z()});return n;}
    public static Owner owner(NbtCompound n){int[] p=n.getIntArray("root");if(p.length!=3)throw new IllegalArgumentException("Invalid composite owner root");return new Owner(n.getUuid("uuid"),n.getString("id"),new Cell(p[0],p[1],p[2]));}
    public static NbtCompound data(BlockData data){NbtCompound n=new NbtCompound();n.putString("id",data.blockId());NbtCompound props=new NbtCompound();data.properties().forEach(props::putString);n.put("properties",props);n.putByteArray("payload",data.blockEntityNbt());return n;}
    public static BlockData data(NbtCompound n){Map<String,String> props=new TreeMap<>();NbtCompound values=n.getCompound("properties");for(String key:values.getKeys())props.put(key,values.getString(key));return new BlockData(n.getString("id"),props,bytes(nbt(n.getByteArray("payload"))));}
    public static NbtList boxes(List<Box> boxes){NbtList result=new NbtList();for(Box b:boxes){NbtList row=new NbtList();for(double v:new double[]{b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ()})row.add(NbtDouble.of(v));result.add(row);}return result;}
    public static List<Box> boxes(NbtList list){List<Box> result=new ArrayList<>();for(int i=0;i<list.size();i++){NbtList b=(NbtList)list.get(i);if(b.size()!=6)throw new IllegalArgumentException("Invalid cached composite cuboid");result.add(new Box(b.getDouble(0),b.getDouble(1),b.getDouble(2),b.getDouble(3),b.getDouble(4),b.getDouble(5)));}return List.copyOf(result);}
    public static NbtCompound contribution(Contribution c){NbtCompound n=new NbtCompound();n.put("owner",owner(c.owner));n.put("rootData",data(c.rootData));n.put("collision",boxes(c.shape.collision()));n.put("selection",boxes(c.shape.selection()));return n;}
    public static Contribution contribution(NbtCompound n){return new Contribution(owner(n.getCompound("owner")),data(n.getCompound("rootData")),new Footprint(boxes(n.getList("collision",9)),boxes(n.getList("selection",9))));}
    /** One already bounded custom-packet decode, never a world cache. Values are
     * immutable; raw NBT keys remain decoder-owned and disappear at close. */
    public static final class DecodePool implements AutoCloseable {
        private final Map<Owner,Owner> owners=new HashMap<>();
        private final Map<BlockData,BlockData> data=new HashMap<>();
        private final Map<Footprint,Footprint> shapes=new HashMap<>();
        private final Map<NbtCompound,BlockData> rawData=new HashMap<>();
        private record RawFootprint(NbtList collision,NbtList selection){}
        private final Map<RawFootprint,Footprint> rawShapes=new HashMap<>();
        private final Map<List<Contribution>,List<Contribution>> lists=new HashMap<>();
        private final Map<PackedOwners,List<Contribution>> packedOwners=new HashMap<>();
        private static final class PackedOwners {
            final byte[] bytes;
            final int hash;
            PackedOwners(byte[] bytes,boolean owned){this.bytes=owned?bytes.clone():bytes;hash=Arrays.hashCode(bytes);}
            @Override public int hashCode(){return hash;}
            @Override public boolean equals(Object other){return other instanceof PackedOwners key&&Arrays.equals(bytes,key.bytes);}
        }
        private boolean closed;
        /** Equal packed helper lists used to pass through NbtIo.read before
         * field sharing could help. Reuse the already verified immutable list
         * before parsing that envelope again, within this chunk/packet only. */
        public List<Contribution> savedContributions(NbtCompound source){
            if(closed)throw new IllegalStateException("Closed composite packet decode pool");
            byte[] packed=CompositeOwnerNbt.packedBytes(source);
            if(packed==null)return contributions(CompositeOwnerNbt.read(source));
            List<Contribution> cached=packedOwners.get(new PackedOwners(packed,false));
            if(cached!=null)return cached;
            List<Contribution> decoded=contributions(CompositeOwnerNbt.read(source));
            // The caller may mutate its NBT after this read. Own this temporary
            // byte key; neither mutable NBT nor this key outlives the scope.
            packedOwners.put(new PackedOwners(packed,true),decoded);return decoded;
        }
        public List<Contribution> contributions(NbtList values){
            if(closed)throw new IllegalStateException("Closed composite packet decode pool");
            List<Contribution> result=new ArrayList<>(values.size());
            for(int i=0;i<values.size();i++)result.add(contribution(values.getCompound(i)));
            List<Contribution> immutable=List.copyOf(result);return lists.computeIfAbsent(immutable,key->key);
        }
        public Contribution contribution(NbtCompound n){
            if(closed)throw new IllegalStateException("Closed composite packet decode pool");
            Owner decodedOwner=CompositeData.owner(n.getCompound("owner"));
            Owner owner=owners.computeIfAbsent(decodedOwner,value->value);
            // Check identical serialized root data before canonical payload
            // parsing and its defensive byte-array/property-map copies.
            NbtCompound raw=n.getCompound("rootData");
            BlockData rootData=rawData.get(raw);
            if(rootData==null){BlockData decoded=CompositeData.data(raw);rootData=data.computeIfAbsent(decoded,value->value);rawData.put(raw,rootData);}
            RawFootprint rawShape=new RawFootprint(n.getList("collision",9),n.getList("selection",9));
            Footprint shape=rawShapes.get(rawShape);
            if(shape==null){Footprint decodedShape=new Footprint(boxes(rawShape.collision),boxes(rawShape.selection));shape=shapes.computeIfAbsent(decodedShape,value->value);rawShapes.put(rawShape,shape);}
            return new Contribution(owner,rootData,shape);
        }
        @Override public void close(){owners.clear();data.clear();shapes.clear();rawData.clear();rawShapes.clear();lists.clear();packedOwners.clear();closed=true;}
    }
    public static NbtList contributions(List<Contribution> entries){NbtList n=new NbtList();entries.stream().sorted(Comparator.comparing(Contribution::owner)).forEach(c->n.add(contribution(c)));return n;}
    public static List<Contribution> contributions(NbtList n){DecodePool pool=ACTIVE_DECODE.get();if(pool!=null)return pool.contributions(n);List<Contribution> result=new ArrayList<>(n.size());for(int i=0;i<n.size();i++)result.add(contribution(n.getCompound(i)));return List.copyOf(result);}
    public static List<Contribution> savedContributions(NbtCompound source){DecodePool pool=ACTIVE_DECODE.get();return pool==null?contributions(CompositeOwnerNbt.read(source)):pool.savedContributions(source);}
    private CompositeData(){}
}
