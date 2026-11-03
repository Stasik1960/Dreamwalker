package dev.dreamwalker.editorqa;

import dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity;
import dev.dreamwalker.bloodbornedw.composite.NativeVisuals;
import dev.dreamwalker.bloodbornedw.editor.EditorData;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;

/** Test-only transaction probe; captures before the first physical/native write. */
public final class TouchedCells {
    public record Cell(BlockPos pos, BlockState state, int nativeIndex, int style, UUID owner, String blockEntityType) {}
    private static TouchedCells active;
    private final ServerWorld world;
    private final BlockPos center;
    private final Map<Long, Cell> cells = new LinkedHashMap<>();
    private final Map<Long, Cell> untrackedAttempts = new LinkedHashMap<>();
    private boolean breaking;
    private String unsupported;
    public TouchedCells(ServerWorld world, BlockPos center) { this.world = world; this.center = center.toImmutable(); }
    public void begin(boolean breaking) { this.breaking = breaking; active = this; }
    public void end() { if (active == this) active = null; verifyUntrackedAttempts(); }
    public Map<Long, Cell> cells() { return cells; }
    public String unsupported() { return unsupported; }
    /** Preserve the captured pre-fixture state when QA temporarily stages a placement context. */
    public void captureBeforeFixtureChange(BlockPos pos) {
        long key=pos.asLong();
        if(!cells.containsKey(key))cells.put(key,snapshot(world,pos.toImmutable()));
    }
    public static void touch(World world, BlockPos pos) {
        TouchedCells probe = active;
        if (probe == null || world != probe.world || !probe.world.getServer().isOnThread()) return;
        long key = pos.asLong();
        if (probe.cells.containsKey(key) || probe.untrackedAttempts.containsKey(key)) return;
        boolean bounded=Math.abs(pos.getX()-probe.center.getX()) <= 64 && Math.abs(pos.getY()-probe.center.getY()) <= 64
                && Math.abs(pos.getZ()-probe.center.getZ()) <= 64 && pos.getY() >= world.getBottomY() && pos.getY() < world.getTopY()
                && world.getChunkManager().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false) != null;
        if (!bounded || probe.breaking || probe.cells.size() >= 32768) {
            probe.recordUntracked(world,pos,key); return;
        }
        BlockPos saved = pos.toImmutable();
        probe.cells.put(key, probe.snapshot(world,saved));
    }
    private void recordUntracked(World world,BlockPos pos,long key){
        if(pos.getY()<world.getBottomY()||pos.getY()>=world.getTopY()
                || world.getChunkManager().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false) == null) {
            unsupported="Cannot compare untracked write outside loaded world bounds at "+pos;return;
        }
        if(untrackedAttempts.size()>=32768){unsupported="Untracked write observation budget exceeded";return;}
        untrackedAttempts.put(key,snapshot(world,pos.toImmutable()));
    }
    private Cell snapshot(World world,BlockPos pos){
        var entity=world.getBlockEntity(pos);
        return new Cell(pos,world.getBlockState(pos),NativeVisuals.index(world,pos),EditorData.style(world,pos),owner(world,pos),entity==null?null:entity.getClass().getName());
    }
    private void verifyUntrackedAttempts(){
        if(unsupported!=null)return;
        for(Cell before:untrackedAttempts.values()){
            Cell after=snapshot(world,before.pos());
            if(!before.state().equals(after.state())||before.nativeIndex()!=after.nativeIndex()||before.style()!=after.style()
                    ||!java.util.Objects.equals(before.owner(),after.owner())||!java.util.Objects.equals(before.blockEntityType(),after.blockEntityType())){
                unsupported="Untracked footprint changed "+before.pos()+" from {state="+before.state()+", native="+before.nativeIndex()+", style="+before.style()+", owner="+before.owner()+", blockEntity="+before.blockEntityType()+"} to {state="+after.state()+", native="+after.nativeIndex()+", style="+after.style()+", owner="+after.owner()+", blockEntity="+after.blockEntityType()+"}";return;
            }
        }
    }
    public static UUID owner(World world, BlockPos pos) {
        if (world.getBlockEntity(pos) instanceof CompositeBlockEntity entity && entity.resident() != null) return entity.resident().instanceId();
        return null;
    }
}
