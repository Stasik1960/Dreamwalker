package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import java.util.*;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.*;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;

/** Static cached owner contributions; deliberately has no BlockEntityTicker. */
public class CompositeBlockEntity extends BlockEntity {
    private Owner resident;
    private List<CompositeData.Contribution> contributions=List.of();
    private NbtCompound payload=new NbtCompound();
    private final UUID shapePublicationToken=UUID.randomUUID();
    public CompositeBlockEntity(BlockPos pos,BlockState state){this(CompositeArchitecture.CELL_ENTITY,pos,state);if(dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.isNative(state))resident=new Owner(UUID.randomUUID(),net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()).toString(),CompositeData.cell(pos));}
    protected CompositeBlockEntity(net.minecraft.block.entity.BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    public Owner resident(){return resident;}
    public List<CompositeData.Contribution> contributions(){return contributions;}
    public NbtCompound payload(){return payload.copy();}
    public double verticalOffset(){return payload.getDouble(dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.KEY);}
    public double mountY(){return payload.getDouble("MountY")+payload.getDouble(dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.KEY);}
    public void set(Owner owner,List<CompositeData.Contribution> entries,NbtCompound extra){resident=owner;contributions=List.copyOf(entries);payload=extra.copy();markDirty();publishShapeSnapshot();}
    /** No World reads: also safe when a chunk attaches/deserializes its BEs on a worker. */
    public final void publishShapeSnapshot(){CompositeShapeSnapshots.entity(world,pos,shapePublicationToken,resident(),contributions(),payload(),this instanceof dev.dreamwalker.bloodbornedw.architecture.ladder_source.SourceLadderBlockEntity source?source.root():null);}
    @Override public void setWorld(net.minecraft.world.World world){super.setWorld(world);publishShapeSnapshot();}
    @Override public void setCachedState(BlockState state){super.setCachedState(state);publishShapeSnapshot();}
    @Override public void markRemoved(){CompositeShapeSnapshots.retired(world,pos,shapePublicationToken);super.markRemoved();}
    @Override protected void writeNbt(NbtCompound nbt){try{super.writeNbt(nbt);nbt.put("payload",payload.copy());if(resident!=null)nbt.put("resident",CompositeData.owner(resident));nbt.put("owners",CompositeData.contributions(contributions));}catch(RuntimeException failure){diagnosticError("COMPOSITE_SAVE_DATA",failure);throw failure;}}
    @Override public void readNbt(NbtCompound nbt){try{super.readNbt(nbt);payload=nbt.getCompound("payload").copy();resident=nbt.contains("resident",10)?CompositeData.owner(nbt.getCompound("resident")):dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.isNative(getCachedState())?resident:null;contributions=CompositeData.contributions(nbt.getList("owners",10));publishShapeSnapshot();}catch(RuntimeException failure){diagnosticError("COMPOSITE_DATA_CORRUPTION",failure);throw failure;}}
    private void diagnosticError(String category,RuntimeException failure){dev.dreamwalker.bloodbornedw.diagnostics.DwDiagnostics.error(world instanceof net.minecraft.server.world.ServerWorld server?server:null,dev.dreamwalker.bloodbornedw.diagnostics.ArchitectureDiagnostics.type(getCachedState()),resident==null?dev.dreamwalker.bloodbornedw.diagnostics.ArchitectureDiagnostics.rootId(pos):resident.instanceId().toString(),pos,category,"Owner contribution or typed payload failed: "+failure.getMessage(),failure);}
    @Override public NbtCompound toInitialChunkDataNbt(){return createNbt();}
    @Override public BlockEntityUpdateS2CPacket toUpdatePacket(){return BlockEntityUpdateS2CPacket.create(this);}
}
