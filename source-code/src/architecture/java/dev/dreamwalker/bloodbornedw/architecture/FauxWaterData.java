package dev.dreamwalker.bloodbornedw.architecture;

import java.util.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;

/** Only waterlogged hosts need metadata; empty chunks allocate no table/file. */
public final class FauxWaterData {
    public static final String KEY="DreamwalkerFauxWater";
    public static final FauxWaterData EMPTY=new FauxWaterData(Set.of());
    private final Set<Long> cells;
    private FauxWaterData(Set<Long> cells){this.cells=Set.copyOf(cells);}
    public boolean contains(BlockPos pos){return cells.contains(pos.asLong());}
    public boolean empty(){return cells.isEmpty();}
    public FauxWaterData with(BlockPos pos,boolean present){if(contains(pos)==present)return this;var next=new HashSet<>(cells);if(present)next.add(pos.asLong());else next.remove(pos.asLong());return next.isEmpty()?EMPTY:new FauxWaterData(next);}
    public NbtCompound nbt(){var n=new NbtCompound();n.putInt("Schema",1);n.putLongArray("Cells",cells.stream().mapToLong(Long::longValue).sorted().toArray());return n;}
    public static FauxWaterData read(NbtCompound n,Chunk chunk){
        if(n.isEmpty())return EMPTY;if(n.getInt("Schema")!=1)throw new IllegalArgumentException("Unknown faux water schema");
        long[] values=n.getLongArray("Cells");if(values.length>chunk.countVerticalSections()*4096)throw new IllegalArgumentException("Faux water cell budget");
        Set<Long> cells=new HashSet<>();for(long value:values){var pos=BlockPos.fromLong(value);if(!chunk.getPos().equals(new net.minecraft.util.math.ChunkPos(pos))||pos.getY()<chunk.getBottomY()||pos.getY()>=chunk.getTopY()||!cells.add(value)||!FauxWaterlogging.wet(chunk.getBlockState(pos)))throw new IllegalArgumentException("Invalid faux water host at "+pos);}
        return cells.isEmpty()?EMPTY:new FauxWaterData(cells);
    }
    public interface Holder {FauxWaterData fauxWaterData();void fauxWaterData(FauxWaterData value);}
}
