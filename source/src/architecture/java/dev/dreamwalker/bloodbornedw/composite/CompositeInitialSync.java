package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

/** A bounded witness of the exact vanilla packet being constructed now.
 * Nothing is cached between sends. The constructor temporarily borrows its
 * already loaded chunk; endPacket releases it. Finally/close drops all NBT,
 * packet and immutable contribution references, including exceptional exits. */
public final class CompositeInitialSync {
    private static final int MAX_WITNESSES=4096;
    private static final ThreadLocal<Scope> CURRENT=new ThreadLocal<>();
    private record Proof(List<CompositeData.Contribution> entries,NbtCompound tag,NbtElement envelope) {
        boolean covers(Cell cell,List<CompositeData.Contribution> expected){
            if(envelope==null||tag.get(CompositeOwnerNbt.KEY)!=envelope||!entries.equals(expected))return false;
            try{
                byte[] payload=null;
                for(var entry:expected)if(entry.owner().root().equals(cell)){
                    // Generic roots would otherwise take the last matching
                    // root owner/payload from the custom packet. Ambiguity
                    // stays on the full-sync path, including distinct roots.
                    if(!tag.contains("resident",NbtElement.COMPOUND_TYPE)||!entry.owner().equals(CompositeData.owner(tag.getCompound("resident"))))return false;
                    if(payload==null)payload=CompositeData.bytes(tag.getCompound("payload"));
                    if(!Arrays.equals(payload,entry.rootData().blockEntityNbt()))return false;
                }
                return true;
            }catch(RuntimeException malformed){return false;}
        }
    }
    public static final class Scope implements AutoCloseable {
        private final Scope previous;private final Identifier dimension;private final long chunk;
        private Map<Long,Proof> proofs;private Object packet;private WorldChunk constructingChunk;private boolean constructing,confirmed,closed;
        private Scope(ServerWorld world,ChunkPos chunk,boolean collect){previous=CURRENT.get();dimension=world.getRegistryKey().getValue();this.chunk=chunk.toLong();proofs=collect?new HashMap<>():null;CURRENT.set(this);}
        public void confirm(Object packet){confirmed=packet!=null&&packet==this.packet&&proofs!=null;}
        private void start(WorldChunk chunk){proofs.clear();packet=null;confirmed=false;constructing=true;constructingChunk=chunk;}
        public void endPacket(Object packet){
            constructing=false;constructingChunk=null;this.packet=packet;
            if(proofs==null)return;
            if(!(packet instanceof ChunkDataS2CPacket actual)){proofs.clear();return;}
            // Prove that the borrowed tag is actually in this exact packet,
            // rather than another NBT read made by a constructor interceptor.
            try{
                Set<Long> delivered=new HashSet<>();
                actual.getChunkData().getBlockEntities(ChunkPos.getPackedX(chunk),ChunkPos.getPackedZ(chunk)).accept((pos,type,tag)->{
                    long key=pos.asLong();Proof proof=proofs.get(key);
                    if(type==CompositeArchitecture.CELL_ENTITY&&proof!=null&&proof.tag()==tag)delivered.add(key);
                });
                proofs.keySet().retainAll(delivered);
            }catch(RuntimeException uncertain){proofs.clear();}
        }
        public boolean covers(Cell cell,List<CompositeData.Contribution> entries){if(!confirmed||proofs==null||ChunkPos.toLong(cell.x()>>4,cell.z()>>4)!=chunk)return false;Proof proof=proofs.get(CompositeData.pos(cell).asLong());return proof!=null&&proof.covers(cell,entries);}
        @Override public void close(){if(closed||CURRENT.get()!=this)throw new IllegalStateException("Unbalanced initial owner witness");closed=true;if(proofs!=null)proofs.clear();proofs=null;packet=null;constructingChunk=null;constructing=confirmed=false;if(previous==null)CURRENT.remove();else CURRENT.set(previous);}
    }
    public static Scope begin(ServerWorld world,ChunkPos chunk,boolean collect){return new Scope(world,chunk,collect);}
    public static Scope capturePacket(WorldChunk chunk){Scope scope=CURRENT.get();if(scope==null||scope.proofs==null||chunk.getPos().toLong()!=scope.chunk)return null;scope.start(chunk);return scope;}
    public static boolean covered(Cell cell,List<CompositeData.Contribution> entries){Scope scope=CURRENT.get();return scope!=null&&scope.covers(cell,entries);}
    public static boolean active(){return CURRENT.get()!=null;}
    static void record(CompositeBlockEntity entity,NbtCompound tag){
        Scope scope=CURRENT.get();if(scope==null||!scope.constructing||scope.proofs==null||entity.getClass()!=CompositeBlockEntity.class)return;
        if(!(entity.getWorld() instanceof ServerWorld world)||!world.getRegistryKey().getValue().equals(scope.dimension)||new ChunkPos(entity.getPos()).toLong()!=scope.chunk)return;
        var state=scope.constructingChunk.getBlockState(entity.getPos());
        // Only the known base factory is covered. Native/foreign factories,
        // climbing subclasses and unsupported saved BEs keep full owner sync.
        if(entity.getType()!=CompositeArchitecture.CELL_ENTITY||(!(state.getBlock() instanceof CompositeRootBlock)&&!state.isOf(CompositeArchitecture.CELL))||state.getBlock() instanceof ImportedLadderBlock)return;
        if(!state.hasBlockEntity()||!entity.getType().supports(state)||!tag.contains(CompositeOwnerNbt.KEY,NbtElement.BYTE_ARRAY_TYPE)||!tag.contains("payload",NbtElement.COMPOUND_TYPE))return;
        if(scope.proofs.size()>=MAX_WITNESSES){scope.proofs.clear();scope.proofs=null;return;}
        scope.proofs.put(entity.getPos().asLong(),new Proof(entity.contributions(),tag,tag.get(CompositeOwnerNbt.KEY)));
    }
    /** A live update following vanilla BE NBT still invalidates geometry when
     * initial delivery omitted the redundant ledger cell. Only explicitly
     * initial packets may use identical vanilla data as a no-op witness. */
    public static boolean visualChange(boolean initial,boolean hadLedger,boolean ledgerChanged,CompositeBlockEntity entity,List<CompositeData.Contribution> entries){
        if(!hadLedger&&entity!=null)return initial?!entity.contributions().equals(entries):ledgerChanged||!entity.contributions().equals(entries);
        return ledgerChanged;
    }
    private CompositeInitialSync(){}
}
