package dev.dreamwalker.bloodbornedw.composite;

import java.io.*;
import java.nio.channels.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.nbt.scanner.*;

/** Transient projections of one already-open shard generation. Native skip
 * visits the omitted bytes without constructing their NBT object trees. */
final class MembershipNbtReader {
    record Metadata(NbtCompound tag,List<Integer> memberCounts) {}
    record Rows(int size,Map<Integer,NbtCompound> entries) {}
    private static void scan(SeekableByteChannel input,NbtScanner collector)throws IOException {
        input.position(0);
        // NbtIo closes its gzip reader. Keep this same file handle for both
        // passes so an atomic external replacement cannot mix generations.
        try(var stream=new FilterInputStream(Channels.newInputStream(input)) {
            @Override public void close() {}
        }) {NbtIo.scanCompressed(stream,collector);}
    }
    static Metadata metadata(SeekableByteChannel input)throws IOException {
        var collector=new MetaCollector();scan(input,collector);
        return new Metadata((NbtCompound)collector.getRoot(),List.copyOf(collector.memberCounts));
    }
    static Rows members(SeekableByteChannel input,Set<Integer> wanted)throws IOException {return rows(input,"ownerMembership",wanted);}
    static Rows shapes(SeekableByteChannel input,Set<Integer> wanted)throws IOException {return rows(input,"shapeTable",wanted);}
    private static Rows rows(SeekableByteChannel input,String field,Set<Integer> wanted)throws IOException {
        if(wanted.isEmpty())return new Rows(0,Map.of());
        var collector=new RowCollector(field,wanted);scan(input,collector);
        var tags=((NbtCompound)collector.getRoot()).getList(field,NbtElement.COMPOUND_TYPE);
        if(tags.size()!=collector.indices.size())throw new IllegalArgumentException("Shard shape dictionary type");
        Map<Integer,NbtCompound> result=new HashMap<>();
        for(int i=0;i<tags.size();i++)result.put(collector.indices.get(i),tags.getCompound(i));
        return new Rows(collector.size,result);
    }
    /** Keep identities and list budgets, but no unrequested owner's mask. */
    private static final class MetaCollector extends NbtCollector {
        private final List<Integer> memberCounts=new ArrayList<>();
        private boolean membershipList,memberList;private int row=-1;
        @Override public NestedResult startSubNbt(NbtType<?> type,String key) {
            if(getDepth()==1){
                if(key.equals("cells")||key.equals("dataTable")||key.equals("shapeTable"))return NestedResult.SKIP;
                if(key.equals("ownerMembership")){membershipList=type==NbtList.TYPE;memberCounts.clear();row=-1;}
            }else if(getDepth()==3&&membershipList&&key.equals("members")){
                memberCounts.set(row,0);memberList=type==NbtList.TYPE;
            }
            return super.startSubNbt(type,key);
        }
        @Override public NestedResult startListItem(NbtType<?> type,int index) {
            if(getDepth()==2&&membershipList){row=index;while(memberCounts.size()<=row)memberCounts.add(0);}
            if(getDepth()==4&&memberList)return NestedResult.SKIP;
            return super.startListItem(type,index);
        }
        @Override public Result visitListMeta(NbtType<?> type,int length) {
            if(getDepth()==4&&memberList)memberCounts.set(row,type==NbtCompound.TYPE?length:0);
            return super.visitListMeta(type,length);
        }
        @Override public Result endNested() {
            if(getDepth()==4&&memberList)memberList=false;
            if(getDepth()==2&&membershipList){membershipList=false;row=-1;}
            return super.endNested();
        }
    }
    private static final class RowCollector extends NbtCollector {
        private final String field;private final Set<Integer> wanted;private final List<Integer> indices=new ArrayList<>();private int size;
        RowCollector(String field,Set<Integer> wanted){this.field=field;this.wanted=wanted;}
        @Override public NestedResult startSubNbt(NbtType<?> type,String key) {
            if(getDepth()==1){
                if(type!=NbtList.TYPE||!key.equals(field))return NestedResult.SKIP;
                indices.clear();size=0;
            }
            return super.startSubNbt(type,key);
        }
        @Override public Result visitListMeta(NbtType<?> type,int length) {
            if(getDepth()==2)size=type==NbtCompound.TYPE?length:0;
            return super.visitListMeta(type,length);
        }
        @Override public NestedResult startListItem(NbtType<?> type,int index) {
            if(getDepth()==2){if(!wanted.contains(index))return NestedResult.SKIP;indices.add(index);}
            return super.startListItem(type,index);
        }
    }
    private MembershipNbtReader() {}
}
