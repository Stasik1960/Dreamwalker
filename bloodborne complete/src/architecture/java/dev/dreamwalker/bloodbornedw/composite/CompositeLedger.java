package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

/** Foreign carriers retain their real block and real BE. Only owner contributions live here. */
public final class CompositeLedger extends PersistentState {
    private final Map<Cell,List<CompositeData.Contribution>> cells=new TreeMap<>();
    private java.lang.ref.WeakReference<World> boundWorld=new java.lang.ref.WeakReference<>(null);
    private static final Map<World,CompositeLedger> CLIENT=Collections.synchronizedMap(new WeakHashMap<>());
    public static CompositeLedger get(World world){CompositeLedger ledger=world instanceof ServerWorld server?server.getPersistentStateManager().getOrCreate(CompositeLedger::read,CompositeLedger::new,"bloodborne_dw_composite_owners"):CLIENT.computeIfAbsent(world,unused->new CompositeLedger());if(ledger.boundWorld.get()!=world){ledger.boundWorld=new java.lang.ref.WeakReference<>(world);CompositeShapeSnapshots.ledgerLoaded(world,ledger.cells);}return ledger;}
    public List<CompositeData.Contribution> at(Cell cell){return cells.getOrDefault(cell,List.of());}
    public void put(Cell cell,List<CompositeData.Contribution> entries){if(entries.isEmpty())cells.remove(cell);else cells.put(cell,List.copyOf(entries));markDirty();World world=boundWorld.get();if(world!=null)CompositeShapeSnapshots.ledger(world,cell,entries);}
    public Set<Cell> cells(){return Set.copyOf(cells.keySet());}
    public static CompositeLedger read(NbtCompound nbt){CompositeLedger state=new CompositeLedger();net.minecraft.util.math.BlockPos current=null;try{NbtList entries=nbt.getList("cells",10);for(int i=0;i<entries.size();i++){NbtCompound row=entries.getCompound(i);int[] p=row.getIntArray("pos");if(p.length!=3)throw new IllegalArgumentException("Invalid composite ledger cell");current=new net.minecraft.util.math.BlockPos(p[0],p[1],p[2]);state.cells.put(new Cell(p[0],p[1],p[2]),CompositeData.contributions(row.getList("owners",10)));}return state;}catch(RuntimeException failure){dev.dreamwalker.bloodbornedw.diagnostics.DwDiagnostics.error(null,"UNASSIGNED","composite_owners_ledger",current,"COMPOSITE_LEDGER_CORRUPTION","Cannot read saved owner cell: "+failure.getMessage(),failure);throw failure;}}
    @Override public NbtCompound writeNbt(NbtCompound nbt){net.minecraft.util.math.BlockPos current=null;try{NbtList entries=new NbtList();for(var entry:cells.entrySet()){NbtCompound row=new NbtCompound();Cell p=entry.getKey();current=CompositeData.pos(p);row.putIntArray("pos",new int[]{p.x(),p.y(),p.z()});row.put("owners",CompositeData.contributions(entry.getValue()));entries.add(row);}nbt.put("cells",entries);return nbt;}catch(RuntimeException failure){dev.dreamwalker.bloodbornedw.diagnostics.DwDiagnostics.error(null,"UNASSIGNED","composite_owners_ledger",current,"COMPOSITE_LEDGER_SAVE","Cannot encode saved owner cell: "+failure.getMessage(),failure);throw failure;}}
}
