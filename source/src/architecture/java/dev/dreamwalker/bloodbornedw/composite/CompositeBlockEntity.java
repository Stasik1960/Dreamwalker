package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import java.util.*;
import net.minecraft.block.BlockState;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.*;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;

/** Static cached owner contributions; deliberately has no BlockEntityTicker. */
public class CompositeBlockEntity extends BlockEntity implements net.fabricmc.fabric.api.blockview.v2.RenderDataBlockEntity {
    private Owner resident;
    private List<CompositeData.Contribution> contributions=List.of();
    private NbtCompound payload=new NbtCompound();
    private volatile CompositeTerrain.Snapshot terrainSnapshot;
    private int nativeGridBase=-1;
    private volatile Object renderData;
    private final UUID shapePublicationToken=UUID.randomUUID();
    public CompositeBlockEntity(BlockPos pos,BlockState state){this(CompositeArchitecture.CELL_ENTITY,pos,state);if(dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.isNative(state))resident=new Owner(UUID.randomUUID(),net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()).toString(),CompositeData.cell(pos));}
    protected CompositeBlockEntity(net.minecraft.block.entity.BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    public Owner resident(){return resident;}
    public List<CompositeData.Contribution> contributions(){return contributions;}
    public NbtCompound payload(){return payload.copy();}
    public int nativeGridBase(){return nativeGridBase;}
    /** Transaction adapter sets this before publishing the complete revision. */
    public void nativeGridBase(int index){if(index< -1)throw new IllegalArgumentException("Invalid native background index");if(index>=0)NativeGridBridge.validate(index,getCachedState());nativeGridBase=index;}
    /** Render-thread NEW-source reader only. Never mutate or retain this owned compound. */
    NbtCompound sourceRenderPayload(){return payload;}
    /** Server maintenance compares identity only, never exposes or edits NBT. */
    Object cleanupPayloadRevision(){return payload;}
    Map<dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell,dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Footprint> cleanupFootprint(net.minecraft.world.BlockView view){
        BlockState state=getCachedState();
        return state.getBlock() instanceof CompositeRootBlock block?CompositeSourceShift.footprint(block.spec,state,payload):dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.nativeFootprint(view,pos,state,payload);
    }
    public double verticalOffset(){return payload.getDouble(dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.KEY);}
    public double mountY(){return payload.getDouble("MountY")+payload.getDouble(dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.KEY);}
    public void set(Owner owner,List<CompositeData.Contribution> entries,NbtCompound extra){resident=owner;contributions=List.copyOf(entries);payload=extra.copy();markDirty();publishShapeSnapshot();}
    /** Owner packets also follow vanilla chunk BE NBT. Equal data only adopts
     * the packet's canonical immutable graph; keep the owned payload and avoid
     * a dirty/render update. The server transaction setter retains its contract. */
    public boolean applySyncedRoot(Owner owner,List<CompositeData.Contribution> entries,NbtCompound extra){
        if(Objects.equals(resident,owner)&&contributions.equals(entries)&&payload.equals(extra)){
            List<CompositeData.Contribution> shared=List.copyOf(entries);
            if(resident!=owner||contributions!=shared){resident=owner;contributions=shared;publishShapeSnapshot();}
            return false;
        }
        set(owner,entries,extra);return true;
    }
    /** Drop a duplicate decoded graph only when every saved contribution is
     * equal. Resident authority and the separately owned payload stay intact. */
    public boolean shareEqualContributions(List<CompositeData.Contribution> shared){
        if(contributions==shared||!contributions.equals(shared))return false;
        List<CompositeData.Contribution> immutable=List.copyOf(shared);
        if(contributions==immutable)return false;
        contributions=immutable;publishShapeSnapshot();return true;
    }
    /** No World reads: also safe when a chunk attaches/deserializes its BEs on a worker. */
    public final void publishShapeSnapshot(){
        if(world==null){terrainSnapshot=null;renderData=null;return;}
        if(world.isClient&&CompositeTerrain.eligible(getCachedState())){
            var previous=terrainSnapshot;
            if(previous==null||previous.state()!=getCachedState()||previous.owner()!=resident||previous.payload()!=payload)
                terrainSnapshot=new CompositeTerrain.Snapshot(getCachedState(),resident,payload,pos);
        }else terrainSnapshot=null;
        // entity() extracts scalar metadata synchronously and never retains the
        // compound. Copying every typed payload just to read its offset turned
        // chunk attachment into a large stream of unnecessary NBT allocations.
        renderData=nativeGridBase<0?terrainSnapshot:new NativeGridBridge.RenderData(terrainSnapshot,nativeGridBase);
        CompositeShapeSnapshots.entity(world,pos,shapePublicationToken,resident,contributions,payload,this instanceof dev.dreamwalker.bloodbornedw.architecture.ladder_source.SourceLadderBlockEntity source?source.root():null,nativeGridBase);
    }
    @Override public Object getRenderData(){return renderData;}
    public final boolean terrainMeshed(){var snapshot=terrainSnapshot;return nativeGridBase<0&&snapshot!=null&&snapshot.meshable()&&CompositeTerrain.at(snapshot.state(),pos);}
    @Override public void setWorld(net.minecraft.world.World world){boolean changed=this.world!=world;super.setWorld(world);if(changed)publishShapeSnapshot();}
    @Override public void setCachedState(BlockState state){boolean changed=getCachedState()!=state;super.setCachedState(state);if(changed)publishShapeSnapshot();}
    @Override public void markRemoved(){CompositeShapeSnapshots.retired(world,pos,shapePublicationToken);super.markRemoved();}
    @Override protected void writeNbt(NbtCompound nbt){try{super.writeNbt(nbt);nbt.put("payload",payload.copy());if(resident!=null)nbt.put("resident",CompositeData.owner(resident));if(nativeGridBase>=0)nbt.putInt(NativeGridBridge.KEY,nativeGridBase);CompositeOwnerNbt.write(nbt,CompositeData.contributions(contributions));}catch(RuntimeException failure){diagnosticError("COMPOSITE_SAVE_DATA",failure);throw failure;}}
    @Override public void readNbt(NbtCompound nbt){try{int previousBase=nativeGridBase;super.readNbt(nbt);var owners=CompositeData.savedContributions(nbt);payload=nbt.getCompound("payload").copy();resident=nbt.contains("resident",10)?CompositeData.owner(nbt.getCompound("resident")):dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.isNative(getCachedState())?resident:null;contributions=owners;nativeGridBase=NativeGridBridge.read(nbt,getCachedState());publishShapeSnapshot();if(world!=null&&world.isClient&&previousBase!=nativeGridBase)world.updateListeners(pos,getCachedState(),getCachedState(),Block.NOTIFY_LISTENERS);}catch(RuntimeException failure){diagnosticError("COMPOSITE_DATA_CORRUPTION",failure);throw failure;}}
    private void diagnosticError(String category,RuntimeException failure){dev.dreamwalker.bloodbornedw.DreamwalkerBb.LOG.warn("Owner contribution or typed payload failed: "+failure.getMessage(), failure);}
    @Override public NbtCompound toInitialChunkDataNbt(){NbtCompound tag=createNbt();CompositeInitialSync.record(this,tag);return tag;}
    @Override public BlockEntityUpdateS2CPacket toUpdatePacket(){return BlockEntityUpdateS2CPacket.create(this);}
}
