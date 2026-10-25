package dev.dreamwalker.bloodbornedw.composite;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;

/** Immutable copy-on-write tables. Disk/network use one packed int per occupied
 * cell; only loaded sections allocate a dense 4096-int lookup for workers. */
public final class NativeVisualData {
    public static final String KEY="DreamwalkerVisuals";
    public static final NativeVisualData EMPTY=new NativeVisualData(Map.of());
    private final Map<Integer,int[]> sections;
    private NativeVisualData(Map<Integer,int[]> sections){this.sections=Map.copyOf(sections);}
    private static int local(BlockPos pos){return (pos.getY()&15)<<8|(pos.getZ()&15)<<4|(pos.getX()&15);}
    public int at(BlockPos pos){var values=sections.get(pos.getY()>>4);return values==null?-1:values[local(pos)];}
    public boolean empty(){return sections.isEmpty();}
    public NativeVisualData with(BlockPos pos,int index){
        if(index>=0&&(index>=NativeGridCatalog.cells().size()||NativeVisualLayout.physics(index)<0))throw new IllegalArgumentException("Invalid ordinary visual index");
        if(index< -1)throw new IllegalArgumentException("Invalid cleared visual index");
        if(at(pos)==index)return this;
        int section=pos.getY()>>4;var old=sections.get(section);int[] values=old==null?new int[4096]:old.clone();
        if(old==null)Arrays.fill(values,-1);values[local(pos)]=index;
        var next=new HashMap<>(sections);boolean any=false;for(int value:values)if(value>=0){any=true;break;}
        if(any)next.put(section,values);else next.remove(section);
        return next.isEmpty()?EMPTY:new NativeVisualData(next);
    }
    public NbtCompound nbt(){
        var n=new NbtCompound();n.putInt("Schema",1);n.putString("Catalog",NativeVisualLayout.catalogSha());var list=new NbtList();
        for(var section:new TreeMap<>(sections).entrySet()){
            int count=0;for(int v:section.getValue())if(v>=0)count++;
            int[] packed=new int[count];int out=0;for(int p=0;p<4096;p++){int v=section.getValue()[p];if(v>=0){if(v>=1<<19)throw new IllegalArgumentException("Visual index exceeds packed schema");packed[out++]=v<<12|p;}}
            var row=new NbtCompound();row.putInt("Y",section.getKey());row.putIntArray("Entries",packed);list.add(row);
        }
        n.put("Sections",list);return n;
    }
    public static NativeVisualData read(NbtCompound n,Chunk chunk){
        if(n.isEmpty()){var data=EMPTY;data.validate(chunk);return data;}
        if(n.getInt("Schema")!=1||!NativeVisualLayout.catalogSha().equals(n.getString("Catalog")))throw new IllegalArgumentException("Visual table schema/catalog mismatch at "+chunk.getPos());
        var rows=n.getList("Sections",NbtElement.COMPOUND_TYPE);var result=new HashMap<Integer,int[]>();
        if(rows.size()>chunk.countVerticalSections())throw new IllegalArgumentException("Visual table section budget");
        for(int i=0;i<rows.size();i++){
            var row=rows.getCompound(i);int y=row.getInt("Y");int[] entries=row.getIntArray("Entries");
            if(y<chunk.getBottomSectionCoord()||y>=chunk.getTopSectionCoord()||entries.length==0||entries.length>4096||result.containsKey(y))throw new IllegalArgumentException("Visual section bounds/duplicates");
            int[] values=new int[4096];Arrays.fill(values,-1);
            for(int entry:entries){int local=entry&4095,index=entry>>>12;if(values[local]>=0||index>=NativeGridCatalog.cells().size()||NativeVisualLayout.physics(index)<0)throw new IllegalArgumentException("Visual table reference/duplicate");values[local]=index;}
            result.put(y,values);
        }
        var data=result.isEmpty()?EMPTY:new NativeVisualData(result);data.validate(chunk);return data;
    }
    public void validate(Chunk chunk){
        int referenced=0,physical=0;var pos=new BlockPos.Mutable();
        for(var section:sections.entrySet())for(int i=0;i<4096;i++)if(section.getValue()[i]>=0){
            int index=section.getValue()[i];pos.set(chunk.getPos().getStartX()+(i&15),(section.getKey()<<4)+(i>>8),chunk.getPos().getStartZ()+((i>>4)&15));
            if(!chunk.getBlockState(pos).equals(NativeGridCatalog.state(index)))throw new IllegalArgumentException("Visual/physical state mismatch at "+pos);referenced++;
        }
        for(var section:chunk.getSectionArray())if(section!=null&&section.getBlockStateContainer().hasAny(state->state.getBlock() instanceof NativePhysicsBlock)){
            int[] count={0};section.getBlockStateContainer().count((state,n)->{if(state.getBlock() instanceof NativePhysicsBlock)count[0]+=n;});physical+=count[0];
        }
        if(referenced!=physical)throw new IllegalArgumentException("Missing visual entries at "+chunk.getPos()+": "+referenced+" entries for "+physical+" physical cells");
    }
    public int cells(){int count=0;for(var values:sections.values())for(int value:values)if(value>=0)count++;return count;}
    public int sections(){return sections.size();}
}
